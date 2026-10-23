package com.termfold.app.notify

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import com.termfold.app.R
import com.termfold.app.acp.AcpItem
import com.termfold.app.acp.AcpSessions
import com.termfold.app.shell.TerminalHost
import com.termfold.app.shell.TerminalWakeLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches every live session, agent chats and shells alike, and tells the user when one that
 * was working finishes or starts waiting on them, unless they are already looking at it.
 * It also keeps [KeepAliveService] running while any session is open. A partial wake lock is held
 * when an ACP session needs it or when a terminal has Wake enabled (Auto on: only while working;
 * Auto off: latched until Wake is turned off).
 *
 * Polling once a second is deliberate: it needs no hooks into every state change, costs next to
 * nothing, and the same signals already drive the working spinners in the session list.
 */
object SessionWatcher {

    /** A shell counts as done once it has been quiet this long after working. */
    private const val SHELL_QUIET_MS = 8_000L
    /** Short bursts (a prompt redraw, a quick `ls`) are not worth a notification. */
    private const val SHELL_MIN_WORK_MS = 20_000L

    private class AcpTrack(var busy: Boolean = false, var permissionId: Any? = null, var busySince: Long = 0)
    private class ShellTrack(var busySince: Long = 0, var lastActive: Long = 0)

    private val acp = HashMap<String, AcpTrack>()
    private val shells = HashMap<String, ShellTrack>()
    private var keepingAlive = false

    /** Ends every agent chat and shell and removes the notification: what its Stop button does. */
    fun stopAll(context: Context) {
        runCatching { AcpSessions.all().keys.forEach { AcpSessions.close(it) } }
        runCatching { TerminalHost.liveSessions().forEach { TerminalHost.closeSession(it.key) } }
        KeepAliveService.stop(context)
        keepingAlive = false
    }

    fun start(app: Application) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            while (true) {
                runCatching { tick(app) }
                delay(1_000)
            }
        }
    }

    /** Called when the display turns on or off so keep-alive can react without waiting for the poll. */
    fun onDisplayChanged(context: Context) {
        runCatching { syncKeepAlive(context) }
    }

    /** Call when a new shell or agent session starts so protection begins immediately. */
    fun onSessionStarted(context: Context) {
        runCatching { syncKeepAlive(context) }
    }

    /** Terminal Wake / Auto changed — refresh locks without waiting for the poll. */
    fun onKeepAliveInputsChanged(context: Context) {
        runCatching { syncKeepAlive(context) }
    }

    private data class KeepAliveSnapshot(
        val live: Int,
        val active: Int,
        val working: Int,
        val holdWakeLock: Boolean,
        val terminalWake: Boolean,
        val agentWake: Boolean,
    )

    /** The terminal bell: a CLI agent asking for attention. */
    fun onBell(context: Context, sessionKey: String, title: String) {
        if (AppPresence.isWatching(sessionKey) || AppPresence.inForeground) return
        Notifier.notifyAttention(
            context,
            sessionKey,
            context.getString(R.string.notify_bell_title, title),
            context.getString(R.string.notify_bell_body),
        )
    }

    private fun tick(context: Context) {
        val now = System.currentTimeMillis()
        var live = 0
        var working = 0
        // Sessions that are doing something or waiting for the user (a permission prompt), as
        // opposed to one just sitting at an idle prompt, which needs nothing kept alive.
        var active = 0

        // ---- Agent chats
        val clients = AcpSessions.all()
        acp.keys.retainAll(clients.keys)
        for ((key, client) in clients) {
            val state = client.state.value
            val track = acp.getOrPut(key) { AcpTrack(busy = state.agentBusy, permissionId = state.pendingPermission?.requestId) }
            live++
            if (state.agentBusy) working++
            if (state.agentBusy || state.pendingPermission != null) active++
            val watching = AppPresence.isWatching(key)
            if (watching) Notifier.cancelFor(context, key)

            if (state.agentBusy && !track.busy) track.busySince = now
            if (!state.agentBusy && track.busy && !watching) {
                val reply = state.items.lastOrNull { it is AcpItem.AgentText } as? AcpItem.AgentText
                Notifier.notifyFinished(
                    context,
                    key,
                    context.getString(R.string.notify_done_title, client.agentName, client.folderLabel),
                    reply?.text?.let(::preview) ?: context.getString(R.string.notify_done_body),
                )
            }
            val permission = state.pendingPermission
            if (permission != null && permission.requestId != track.permissionId && !watching) {
                Notifier.notifyAttention(
                    context,
                    key,
                    context.getString(R.string.notify_permission_title, client.agentName),
                    state.items.lastOrNull { it is AcpItem.ToolCall && it.toolCallId == permission.toolCallId }
                        ?.let { (it as AcpItem.ToolCall).title }
                        ?: context.getString(R.string.notify_permission_body, client.folderLabel),
                )
            }
            track.busy = state.agentBusy
            track.permissionId = permission?.requestId
        }

        // ---- Shells (and the CLI agents running in them)
        val terminals = TerminalHost.liveSessions()
        shells.keys.retainAll(terminals.map { it.key }.toSet())
        for (t in terminals) {
            if (!t.running) continue
            live++
            val track = shells.getOrPut(t.key) { ShellTrack() }
            if (t.working) {
                working++
                active++
                if (track.busySince == 0L || now - track.lastActive > SHELL_QUIET_MS) track.busySince = now
                track.lastActive = now
                continue
            }
            val watching = AppPresence.isWatching(t.key)
            if (watching) Notifier.cancelFor(context, t.key)
            val workedFor = track.lastActive - track.busySince
            if (track.busySince != 0L && now - track.lastActive >= SHELL_QUIET_MS) {
                if (workedFor >= SHELL_MIN_WORK_MS && !watching) {
                    Notifier.notifyFinished(
                        context,
                        t.key,
                        context.getString(R.string.notify_shell_title, t.title, t.folderLabel),
                        context.getString(R.string.notify_shell_body),
                    )
                }
                track.busySince = 0L
            }
        }

        val terminalWake = TerminalWakeLock.anyTerminalWantsWakeLock(terminals)
        val agentWake = AcpSessions.all().values.any { it.keepsCpuAwake() }
        syncKeepAlive(
            context,
            KeepAliveSnapshot(
                live = live,
                active = active,
                working = working,
                holdWakeLock = terminalWake || agentWake,
                terminalWake = terminalWake,
                agentWake = agentWake,
            ),
        )
    }

    private fun recomputeKeepAlive(): KeepAliveSnapshot {
        var live = AcpSessions.all().size
        var active = 0
        var working = 0
        for (client in AcpSessions.all().values) {
            val state = client.state.value
            if (state.agentBusy) working++
            if (state.agentBusy || state.pendingPermission != null) active++
        }
        val terminals = TerminalHost.liveSessions()
        for (t in terminals) {
            if (!t.running) continue
            live++
            if (t.working) {
                working++
                active++
            }
        }
        val terminalWake = TerminalWakeLock.anyTerminalWantsWakeLock(terminals)
        val agentWake = AcpSessions.all().values.any { it.keepsCpuAwake() }
        return KeepAliveSnapshot(
            live = live,
            active = active,
            working = working,
            holdWakeLock = terminalWake || agentWake,
            terminalWake = terminalWake,
            agentWake = agentWake,
        )
    }

    private fun syncKeepAlive(context: Context, snapshot: KeepAliveSnapshot? = null) {
        val s = snapshot ?: recomputeKeepAlive()
        if (s.live == 0) {
            if (keepingAlive) {
                KeepAliveService.stop(context)
                keepingAlive = false
            }
            return
        }
        KeepAliveService.update(
            context,
            active = s.active,
            working = s.working,
            holdWakeLock = s.holdWakeLock,
            terminalWake = s.terminalWake,
            agentWake = s.agentWake,
        )
        keepingAlive = true
    }

    private fun preview(text: String): String {
        val flat = text.replace(Regex("[`*#>_]+"), "").replace(Regex("\\s+"), " ").trim()
        return if (flat.length > 240) flat.take(240).trimEnd() + "…" else flat
    }
}

/**
 * A foreground service with a quiet ongoing notification. Its only job is to tell Android that
 * TermFold is doing work the user asked for, so running agents and shells survive while the user
 * is in other apps or has swiped TermFold away.
 */
class KeepAliveService : android.app.Service() {

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val active = intent?.getIntExtra(EXTRA_ACTIVE, 0) ?: 0
        val working = intent?.getIntExtra(EXTRA_WORKING, 0) ?: 0
        val holdWake = intent?.getBooleanExtra(EXTRA_WAKE_LOCK, false) ?: false
        val terminalWake = intent?.getBooleanExtra(EXTRA_WAKE_TERMINAL, false) ?: false
        val agentWake = intent?.getBooleanExtra(EXTRA_WAKE_AGENT, false) ?: false
        setBackgroundLocksHeld(holdWake)
        startForeground(Notifier.RUNNING_ID, notification(this, active, working, holdWake, terminalWake, agentWake))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        setBackgroundLocksHeld(false)
        super.onDestroy()
    }

    private fun setBackgroundLocksHeld(hold: Boolean) {
        if (!hold) {
            wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
            wakeLock = null
            wifiLock?.let { lock -> if (lock.isHeld) lock.release() }
            wifiLock = null
            networkCallback?.let { callback ->
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
            networkCallback = null
            return
        }
        if (wakeLock?.isHeld != true) {
            val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "TermFold::AgentWork").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (onWifi() && wifiLock?.isHeld != true) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, "TermFold::AgentNet").apply {
                setReferenceCounted(false)
                acquire()
            }
        } else if (!onWifi()) {
            wifiLock?.let { lock -> if (lock.isHeld) lock.release() }
            wifiLock = null
        }
        if (networkCallback == null) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {}
            networkCallback = callback
            runCatching { cm.requestNetwork(request, callback) }
        }
    }

    private fun onWifi(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        private const val EXTRA_ACTIVE = "active"
        private const val EXTRA_WORKING = "working"
        private const val EXTRA_WAKE_LOCK = "wake_lock"
        private const val EXTRA_WAKE_TERMINAL = "wake_terminal"
        private const val EXTRA_WAKE_AGENT = "wake_agent"
        private data class Shown(
            val active: Int,
            val working: Int,
            val holdWakeLock: Boolean,
            val terminalWake: Boolean,
            val agentWake: Boolean,
        )

        private var shown: Shown? = null

        fun update(
            context: Context,
            active: Int,
            working: Int,
            holdWakeLock: Boolean,
            terminalWake: Boolean,
            agentWake: Boolean,
        ) {
            val snapshot = Shown(active, working, holdWakeLock, terminalWake, agentWake)
            if (shown == snapshot) return
            shown = snapshot
            val intent = Intent(context, KeepAliveService::class.java)
                .putExtra(EXTRA_ACTIVE, active)
                .putExtra(EXTRA_WORKING, working)
                .putExtra(EXTRA_WAKE_LOCK, holdWakeLock)
                .putExtra(EXTRA_WAKE_TERMINAL, terminalWake)
                .putExtra(EXTRA_WAKE_AGENT, agentWake)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            }
        }

        fun stop(context: Context) {
            shown = null
            runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
        }

        private fun notification(
            context: Context,
            active: Int,
            working: Int,
            holdWakeLock: Boolean,
            terminalWake: Boolean,
            agentWake: Boolean,
        ): android.app.Notification {
            val stop = android.app.PendingIntent.getBroadcast(
                context,
                Notifier.RUNNING_ID + 1,
                Intent(context, StopSessionsReceiver::class.java).setAction(StopSessionsReceiver.ACTION),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            return androidx.core.app.NotificationCompat.Builder(context, Notifier.CHANNEL_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_termfold)
                .setColor(0xFFFF7A2E.toInt())
                // Only sessions that are really doing something are counted; one idle at its
                // prompt used to be reported as "running" for as long as it existed.
                .setContentTitle(
                    if (active > 0) context.resources.getQuantityString(R.plurals.running_title, active, active)
                    else context.getString(R.string.running_idle_title),
                )
                .setContentText(wakeLockStatusLine(context, holdWakeLock, terminalWake, agentWake))
                .setStyle(
                    androidx.core.app.NotificationCompat.BigTextStyle()
                        .bigText(
                            wakeLockStatusLine(context, holdWakeLock, terminalWake, agentWake) + "\n" +
                                if (active > 0) context.getString(R.string.running_active_text)
                                else context.getString(R.string.running_idle_text),
                        ),
                )
                .addAction(0, context.getString(R.string.running_stop), stop)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setContentIntent(Notifier.openIntent(context, AppPresence.place, requestCode = Notifier.RUNNING_ID))
                .build()
        }

        private fun wakeLockStatusLine(
            context: Context,
            holdWakeLock: Boolean,
            terminalWake: Boolean,
            agentWake: Boolean,
        ): String = when {
            !holdWakeLock -> context.getString(R.string.running_wake_lock_off)
            terminalWake && agentWake -> context.getString(R.string.running_wake_lock_on_both)
            terminalWake -> context.getString(R.string.running_wake_lock_on_terminal)
            agentWake -> context.getString(R.string.running_wake_lock_on_chat)
            else -> context.getString(R.string.running_wake_lock_off)
        }
    }
}

/** The notification's Stop button: ends every session, so nothing keeps running and the notification goes. */
class StopSessionsReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) SessionWatcher.stopAll(context.applicationContext)
    }

    companion object {
        const val ACTION = "com.termfold.app.action.STOP_SESSIONS"
    }
}
