package com.termfold.app.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Base64
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import com.termfold.app.preview.engine.BrowserBridgeTimeouts
import com.termfold.app.preview.engine.BrowserSurface
import com.termfold.app.preview.engine.GeckoBrowserSurface
import com.termfold.app.preview.engine.WebViewBrowserSurface
import com.termfold.app.preview.network.NetworkCaptureStore
import com.termfold.app.preview.network.NetworkRuleCommands
import com.termfold.app.preview.network.NetworkRuleKeys
import com.termfold.app.preview.network.NetworkRuleStore
import com.termfold.app.preview.network.NetworkRulesHub
import com.termfold.app.shell.ShellPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

/** What the driver needs from the preview pane around it. */
interface DriverUi {
    fun activeWebView(): WebView
    fun projectDir(): String?
    fun navigate(url: String)
    fun navigateNewTab(url: String): Int?
    fun progress(): Int
    fun loadError(): String?
    /** The HTTP error the page itself was served with (404, 500...), if any. */
    fun httpError(): String?
    fun setViewport(mode: String): Boolean
    fun viewportName(): String
    /** Glides the agent's pointer to a point in the WebView (view pixels), showing [label]. */
    suspend fun moveCursor(x: Float, y: Float, label: String)
    fun pulse()
    fun consoleSize(): Int
    fun consoleText(errorsOnly: Boolean, since: Int): String
    fun consoleErrorsSince(since: Int): Int
    fun appendConsole(level: String, text: String, where: String = "")
    fun networkText(failedOnly: Boolean, sinceGeneration: Int = 0): String
    fun networkInFlight(): Int
    fun networkGeneration(): Int
    fun failedRequestCount(): Int
    fun visibleControlCount(): Int
    fun setVisibleControls(count: Int)
    fun offerUpload(file: File)
    fun uploadTaken(): Boolean
    fun captchaPending(): Boolean
    fun updateCaptchaFromPage(detected: Boolean)
    fun dismissCaptchaFalsePositive()
    fun clearSiteData()
    fun tabList(): JSONArray
    fun tabSwitch(id: Int): Boolean
    fun tabNew(url: String?): Int?
    fun tabClose(id: Int): Boolean
    fun ensureResumed()

    fun browserSurface(): BrowserSurface = WebViewBrowserSurface(activeWebView())
}

/**
 * Drives the preview WebView for an agent. Taps, swipes and key presses are real input events
 * sent to the WebView, so pages see them exactly as a person's; reading the page (the numbered
 * map of buttons, links and fields) and filling fields go through a small script in the page.
 */
class BrowserDriver(
    private val context: Context,
    private val ui: DriverUi,
) : BrowserBridge.Controller {

    private val surface: BrowserSurface get() = ui.browserSurface()

    private companion object {
        const val TEMP_SHOTS = "/tmp/termfold-screenshots"
        const val SNAPSHOT_DIR = "/tmp/termfold-browser"
        const val SNAPSHOT_LIMIT = 60
        const val KEEP_TEMP_SHOTS = 100
        val TF_BRIDGE_FALLBACK_ERRORS = setOf(
            "helper_not_ready",
            "engine_not_ready",
            "extension_port_missing",
            "extension_rpc_timeout",
            "empty_response",
            "Receiving end does not exist",
            "Could not establish connection",
        )
    }

    override suspend fun run(action: String, args: JSONObject): JSONObject = withContext(Dispatchers.Main.immediate) {
        ui.ensureResumed()
        surface.onResume()
        resolveTab(args)?.let { return@withContext it }
        when (action) {
            "open" -> open(args)
            "back" -> history { if (surface.canGoBack()) surface.goBack() else return@history "There is no earlier page." ; null }
            "forward" -> history { if (surface.canGoForward()) surface.goForward() else return@history "There is no later page."; null }
            "reload" -> history { surface.reload(); null }
            "status" -> ok(pageLine())
            "health" -> health()
            "tab_list" -> ok(tabListText())
            "tab_new" -> tabNew(args)
            "tab_switch" -> tabSwitch(args)
            "tab_close" -> tabClose(args)
            "dismiss_captcha" -> dismissCaptcha()
            "snapshot" -> snapshot(args)
            "text" -> text(args)
            "outline" -> outline()
            "click" -> click(args)
            "fill" -> fill(args)
            "type" -> type(args)
            "press" -> press(args)
            "select" -> select(args)
            "check" -> check(args)
            "hover" -> hover(args)
            "scroll" -> scroll(args)
            "wait" -> waitFor(args)
            "screenshot" -> screenshot(args)
            "console" -> ok(ui.consoleText(args.optBoolean("errors_only"), 0))
            "network" -> network(args)
            "network_detail" -> networkDetail(args)
            "network_rules" -> networkRules(args)
            "eval" -> eval(args)
            "viewport" -> viewport(args)
            "upload" -> upload(args)
            else -> err("Unknown action \"$action\".")
        }
    }

    // ---- Navigation --------------------------------------------------------------------------

    private fun resolveTab(args: JSONObject): JSONObject? {
        if (!args.has("tab_id")) return null
        val id = args.optInt("tab_id")
        return if (ui.tabSwitch(id)) null else err("No tab with id $id.")
    }

    private suspend fun health(): JSONObject {
        refreshCaptcha()
        val errors = ui.consoleErrorsSince(0)
        val failed = ui.failedRequestCount()
        var controls = ui.visibleControlCount()
        val titleRaw = js("JSON.stringify(document.title)", BrowserBridgeTimeouts.READY_STATE_EVAL_MS)
        var title = if (evalResponseFailed(titleRaw)) {
            ""
        } else {
            BrowserJsResults.parseEvalString(titleRaw)
        }
        if (title.isBlank() || evalResponseFailed(titleRaw)) {
            val snap = call("snapshot()")
            if (snap != null && !snap.has("error")) {
                title = snap.optString("title").ifBlank { title }
                if (controls <= 0) {
                    controls = snap.optJSONArray("items")?.length() ?: 0
                    ui.setVisibleControls(controls)
                }
            }
        } else if (controls <= 0) {
            val snap = call("snapshot()")
            if (snap != null && !snap.has("error")) {
                controls = snap.optJSONArray("items")?.length() ?: 0
                ui.setVisibleControls(controls)
            }
        }
        val json = JSONObject()
            .put("url", surface.url().orEmpty())
            .put("title", title)
            .put("viewport", ui.viewportName())
            .put("console_errors", errors.coerceAtLeast(0))
            .put("failed_requests", failed.coerceAtLeast(0))
            .put("visible_controls", controls)
            .put("captcha_pending", ui.captchaPending())
            .put("network_generation", ui.networkGeneration())
        return ok(json.toString(2))
    }

    private fun tabListText(): String {
        val tabs = ui.tabList()
        val sb = StringBuilder("Tabs:\n")
        for (i in 0 until tabs.length()) {
            val t = tabs.getJSONObject(i)
            sb.append(if (t.optBoolean("active")) "* " else "  ")
                .append('[').append(t.optInt("id")).append("] ")
                .append(t.optString("title").ifBlank { "(untitled)" })
                .append(" — ").append(Preview.display(t.optString("url"))).append('\n')
        }
        return sb.toString().trimEnd()
    }

    private suspend fun tabNew(args: JSONObject): JSONObject {
        val raw = args.optString("url").trim()
        val url = if (raw.isEmpty()) null else Preview.normalize(raw, ui.projectDir()).also {
            BrowserNavPolicy.checkAgentOpen(it)?.let { blocked -> return err(blocked) }
        }
        val id = ui.tabNew(url) ?: return err("Could not open a new tab (limit reached).")
        if (url != null) awaitLoad()
        return ok("Opened tab $id." + if (url != null) " " + pageLine() else "")
    }

    private suspend fun tabSwitch(args: JSONObject): JSONObject {
        val id = args.optInt("tab_id", args.optInt("id"))
        if (!ui.tabSwitch(id)) return err("No tab with id $id.")
        return ok("Switched to tab $id. " + pageLine())
    }

    private fun tabClose(args: JSONObject): JSONObject {
        val id = args.optInt("tab_id", args.optInt("id", -1))
        if (id < 0) return err("Give tab_id.")
        if (!ui.tabClose(id)) return err("Could not close tab $id.")
        return ok("Closed tab $id.")
    }

    private fun dismissCaptcha(): JSONObject {
        ui.dismissCaptchaFalsePositive()
        return ok("Captcha handoff dismissed. Continue when the page is ready.")
    }

    private fun captureSessionKey(args: JSONObject): String? =
        NetworkRuleKeys.boundSessionKey(args.optString("session_key").takeIf { it.isNotBlank() })

    private fun network(args: JSONObject): JSONObject {
        NetworkCaptureStore.attach(context)
        val key = captureSessionKey(args) ?: return err("No bound browser session for network.")
        when (args.optString("op", "list")) {
            "preserve" -> {
                when (args.optString("enabled", "").lowercase()) {
                    "true", "on", "1" -> NetworkCaptureStore.setPreserve(key, true)
                    "false", "off", "0" -> NetworkCaptureStore.setPreserve(key, false)
                    else -> return err("Use enabled: true or false.")
                }
                return ok("Preserve log is ${NetworkCaptureStore.preserveStatus(key)}.")
            }
            "preserve_status" -> return ok("Preserve log is ${NetworkCaptureStore.preserveStatus(key)}.")
            "clear" -> {
                NetworkCaptureStore.clear(key)
                return ok("Network capture cleared.")
            }
            else -> {
                val sinceCursor = if (args.has("since_cursor")) args.optLong("since_cursor") else 0L
                val sinceGeneration = if (args.has("since_generation")) args.optInt("since_generation") else 0
                val (text, cursor) = NetworkCaptureStore.listText(
                    key,
                    sinceCursor,
                    args.optBoolean("failed_only"),
                    args.optBoolean("xhr_only"),
                    args.optString("url_contains").takeIf { it.isNotBlank() },
                    args.optInt("limit", NetworkCaptureStore.LIST_DEFAULT_LIMIT),
                    sinceGeneration,
                )
                val genSuffix = if (sinceGeneration > 0) " generation_filter=$sinceGeneration" else ""
                return ok("$text\n(cursor=$cursor$genSuffix)")
            }
        }
    }

    private fun networkDetail(args: JSONObject): JSONObject {
        NetworkCaptureStore.attach(context)
        val key = captureSessionKey(args) ?: return err("No bound browser session for network.")
        val id = when {
            args.has("entry_id") -> args.optLong("entry_id")
            args.has("id") -> args.optLong("id")
            else -> 0L
        }
        if (id <= 0L) return err("Give entry_id.")
        val part = args.optString("part", "overview")
        return ok(NetworkCaptureStore.detailText(key, id, part))
    }

    private fun networkRules(args: JSONObject): JSONObject {
        NetworkRulesHub.attach(context)
        val key = captureSessionKey(args)
            ?: return err("No bound browser session for network rules.")
        val result = NetworkRuleCommands.execute(NetworkRuleStore.forApp(context), key, args)
        if (!result.has("error")) NetworkRulesHub.notifyChanged()
        return result
    }

    private suspend fun refreshCaptcha() {
        val data = call("captchaDetect()")
        ui.updateCaptchaFromPage(data?.optBoolean("value", false) == true)
    }

    private suspend fun open(args: JSONObject): JSONObject {
        val raw = args.optString("url").trim()
        if (raw.isEmpty()) return err("Give a URL, a port (5173) or a file path.")
        val url = Preview.normalize(raw, ui.projectDir())
        BrowserNavPolicy.checkAgentOpen(url)?.let { return err(it) }
        if (args.optBoolean("new_tab", false)) {
            val id = ui.navigateNewTab(url) ?: return err("Could not open a new tab (tab limit reached).")
            awaitLoad(url)
            ui.loadError()?.let { return err("Could not open ${Preview.display(url)}: $it.") }
            return ok("Opened tab $id. " + openedLine(url))
        }
        if (Preview.sameBrowseTarget(url, surface.url().orEmpty())) {
            return ok("Opened. " + openedLine(url))
        }
        ui.navigate(url)
        awaitLoad(url)
        if (!Preview.sameBrowseTarget(url, surface.url().orEmpty())) {
            delay(400)
            awaitLoad(url)
        }
        ui.loadError()?.let { return err("Could not open ${Preview.display(url)}: $it. If it is a dev server, is it running?") }
        ui.httpError()?.let { return err("${Preview.display(url)} answered $it. " + openedLine(url)) }
        return ok("Opened. " + openedLine(url))
    }

    private suspend fun history(step: () -> String?): JSONObject {
        step()?.let { return err(it) }
        awaitLoad()
        return ok(openedLine())
    }

    private suspend fun viewport(args: JSONObject): JSONObject {
        val mode = args.optString("mode").lowercase()
        if (!ui.setViewport(mode)) return err("Viewport must be fit, phone or desktop.")
        awaitLoad()
        return ok("Viewport is now $mode. " + openedLine())
    }

    // ---- Reading -----------------------------------------------------------------------------

    /** The visible elements of the last snapshot, to show only what changed on request. */
    private var lastSnapshot: Pair<String, List<String>>? = null

    private var lastShotHash = 0L
    private var lastShotPath: String? = null

    /**
     * Reads the page as a numbered list of controls, kept short on purpose: only what is on
     * screen, capped, without link addresses. The complete list (every element, with its
     * address) is written to a file for the agent to search instead of reading it all again.
     */
    private suspend fun snapshot(args: JSONObject): JSONObject {
        val scope = args.optString("scope").trim()
        val options = JSONObject()
        if (scope.isNotEmpty()) {
            options.put("scope", if (scope.all(Char::isDigit)) JSONObject().put("ref", scope.toInt()) else JSONObject().put("selector", scope))
        }
        val data = call("snapshot($options)") ?: return err("The page could not be read.")
        if (data.has("error")) {
            val code = data.optString("error")
            return err(
                when (code) {
                    "cross_origin_frame" -> "That iframe is cross-origin and cannot be automated in-app."
                    "scope not found" -> "Nothing on the page matches scope \"$scope\"."
                    else -> code
                },
            )
        }
        val items = data.optJSONArray("items") ?: JSONArray()
        val find = args.optString("find").trim().lowercase()
        val all = args.optBoolean("all", false)
        val links = args.optBoolean("links", false)
        val changed = args.optBoolean("changed", false)

        fun line(o: JSONObject, withHref: Boolean, marker: Boolean, withHint: Boolean = false): String {
            val sb = StringBuilder()
            sb.append('[').append(o.optInt("ref")).append("] ").append(o.optString("role"))
            val name = o.optString("name").take(80)
            if (name.isNotEmpty()) sb.append(" \"").append(name).append('"')
            if (o.has("expanded")) sb.append(if (o.optBoolean("expanded")) " (open)" else " (closed)")
            if (o.has("value")) sb.append(" = \"").append(o.optString("value").take(60)).append('"')
            if (o.has("checked")) sb.append(if (o.optBoolean("checked")) " (checked)" else " (unchecked)")
            if (o.optBoolean("disabled")) sb.append(" (disabled)")
            if (withHref && o.has("href")) sb.append(" → ").append(o.optString("href").take(120))
            if (withHint && o.has("hint")) sb.append(" ⟨").append(o.optString("hint")).append('⟩')
            if (marker && !o.optBoolean("visible")) sb.append(" ↓")
            return sb.toString()
        }

        val objects = (0 until items.length()).map { items.getJSONObject(it) }
        val header = StringBuilder()
        header.append("Page: ").append(data.optString("title").ifBlank { "(untitled)" }).append('\n')
        header.append("URL: ").append(data.optString("url")).append('\n')
        header.append("Scrolled ").append(data.optInt("scrollY")).append(" of ").append(data.optInt("scrollHeight"))
            .append(" (view ").append(data.optInt("viewHeight")).append(")\n")

        // The complete list goes to a file; the answer below is the short version of it.
        val full = StringBuilder(header).append("Elements:\n")
        objects.forEach { full.append(line(it, withHref = true, marker = true, withHint = true)).append('\n') }
        if (data.optBoolean("shadowCapped")) full.append("(Shadow DOM depth capped.)\n")
        if (data.optBoolean("captcha")) full.append("(Possible CAPTCHA — ask the user to solve it on screen.)\n")
        ui.updateCaptchaFromPage(data.optBoolean("captcha"))
        val path = runCatching {
            val dir = File(ShellPaths.rootfsDir(context), SNAPSHOT_DIR.trimStart('/')).apply { mkdirs() }
            File(dir, "snapshot.txt").writeText(full.toString())
            "$SNAPSHOT_DIR/snapshot.txt"
        }.getOrNull()

        val visible = objects.filter { it.optBoolean("visible") }
        ui.setVisibleControls(visible.size)
        val visibleLines = visible.map { line(it, withHref = false, marker = false) }
        val previous = lastSnapshot?.takeIf { it.first == data.optString("url") }?.second
        lastSnapshot = data.optString("url") to visibleLines

        val sb = StringBuilder(header)
        var hidden = 0
        if (changed && previous != null) {
            val added = visibleLines.filter { it !in previous.toSet() }
            val removed = previous.filter { it !in visibleLines.toSet() }
            if (added.isEmpty() && removed.isEmpty()) {
                sb.append("No change in the ${visibleLines.size} elements on screen.\n")
            } else {
                sb.append("Changed since the last snapshot (").append(visibleLines.size).append(" on screen):\n")
                added.take(40).forEach { sb.append("+ ").append(it).append('\n') }
                removed.take(40).forEach { sb.append("- ").append(it).append('\n') }
            }
        } else {
            val shown: List<JSONObject> = when {
                find.isNotEmpty() -> objects.filter {
                    (it.optString("name") + " " + it.optString("role") + " " + it.optString("value") + " " + it.optString("href"))
                        .lowercase().contains(find)
                }.also { hidden = (it.size - 40).coerceAtLeast(0) }.take(40)
                all -> objects
                else -> visible.take(SNAPSHOT_LIMIT).also { hidden = objects.size - it.size }
            }
            if (find.isEmpty() && scope.isEmpty() && !all) {
                val heads = data.optJSONArray("headings") ?: JSONArray()
                if (heads.length() > 0) {
                    sb.append("Headings: ").append((0 until minOf(heads.length(), 6)).joinToString(" | ") { heads.getString(it).take(60) }).append('\n')
                }
            }
            if (find.isNotEmpty()) sb.append("Elements matching \"").append(find).append("\":\n")
            else sb.append("Elements (use the number with click, fill, select, check, hover, scroll):\n")
            shown.forEach { sb.append(line(it, withHref = links || find.isNotEmpty(), marker = all || find.isNotEmpty())).append('\n') }
            if (shown.isEmpty()) sb.append(if (find.isNotEmpty()) "  (nothing matches)\n" else "  (no buttons, links or fields)\n")
        }
        if (hidden > 0) {
            sb.append("+ $hidden more (off screen or over the limit): ask for all, find \"text\", scope a part of the page")
            if (path != null) sb.append(", or search the full list: grep -i word $path")
            sb.append('\n')
        }
        val errors = ui.consoleErrorsSince(0)
        if (errors > 0) sb.append("Console: $errors error(s). Run console to read them.\n")
        return ok(sb.toString().trimEnd())
    }

    private suspend fun text(args: JSONObject): JSONObject {
        val options = JSONObject().put("max", args.optInt("max", 8000).coerceIn(500, 30000))
        args.optString("selector").trim().takeIf { it.isNotEmpty() }?.let { options.put("selector", it) }
        val data = call("text($options)") ?: return err("The page could not be read.")
        if (data.has("error")) return err("Nothing on the page matches that selector.")
        return ok("Page: ${data.optString("title")}\nURL: ${data.optString("url")}\n\n${data.optString("text")}")
    }

    /** Just the headings: the shape of a long page for a few tokens. */
    private suspend fun outline(): JSONObject {
        val data = call("outline()") ?: return err("The page could not be read.")
        val heads = data.optJSONArray("headings") ?: JSONArray()
        val sb = StringBuilder("Page: ").append(data.optString("title")).append("\nURL: ").append(data.optString("url")).append('\n')
        for (i in 0 until heads.length()) sb.append(heads.getString(i)).append('\n')
        if (heads.length() == 0) sb.append("(no headings)\n")
        return ok(sb.toString().trimEnd())
    }

    // ---- Acting ------------------------------------------------------------------------------

    private suspend fun click(args: JSONObject): JSONObject {
        val before = ui.consoleSize()
        val (x, y, target) = locate(args) ?: return notFound(args)
        if (target?.optBoolean("disabled") == true) return err("${describe(target)} is disabled.")
        ui.moveCursor(x, y, "Click" + (target?.let { " · " + it.optString("name").take(24) } ?: ""))
        tap(x, y)
        ui.pulse()
        settle()
        return ok("Clicked ${target?.let(::describe) ?: "at $x, $y"}. " + pageLine() + consoleNote(before))
    }

    private suspend fun fill(args: JSONObject): JSONObject {
        val before = ui.consoleSize()
        val text = args.optString("text")
        // Here "text" is what to type; the field is found by its number or a selector.
        val where = JSONObject(args.toString()).apply { remove("text") }
        if (!where.has("ref") && !where.has("selector")) return err("Say which field: give ref (from snapshot) or selector.")
        val point = call("point(${spec(where)})") ?: return notFound(where)
        if (point.has("error")) return notFound(where)
        // A real site's password field: typed as asked, with a reminder the agent must pass on.
        val realPassword = point.optString("type").equals("password", true) && !Preview.isLocal(surface.url().orEmpty())
        val (x, y) = toPx(point)
        ui.moveCursor(x, y, "Type · " + point.optString("name").take(20))
        ui.pulse()
        val result = call("fill(${spec(where, JSONObject().put("text", text))})") ?: return err("Could not type there.")
        if (result.has("error")) return err(result.optString("error"))
        if (args.optBoolean("submit")) {
            key(KeyEvent.KEYCODE_ENTER, 0)
            settle()
        } else {
            delay(120)
        }
        val shown = if (result.optBoolean("password")) "(hidden)" else "\"${result.optString("value")}\""
        val warning = if (realPassword) {
            " IMPORTANT: this was a password on a real website. Tell the user it passed through you (the AI) " +
                "and recommend they change it after this session."
        } else {
            ""
        }
        return ok("Filled ${describe(result)} with $shown" + (if (args.optBoolean("submit")) " and pressed Enter. " else ". ") + pageLine() + consoleNote(before) + warning)
    }

    private suspend fun type(args: JSONObject): JSONObject {
        val text = args.optString("text")
        surface.requestFocus()
        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
        if (events == null) {
            js("document.execCommand('insertText', false, ${JSONObject.quote(text)})")
        } else {
            events.forEach { event ->
                surface.dispatchKeyEvent(KeyEvent(event))
                if (event.action == KeyEvent.ACTION_UP) delay(12)
            }
        }
        delay(120)
        return ok("Typed ${text.length} characters into the focused field.")
    }

    private suspend fun press(args: JSONObject): JSONObject {
        val before = ui.consoleSize()
        val spec = args.optString("key").trim()
        if (spec.isEmpty()) return err("Give a key, like Enter, Tab, Escape, ArrowDown or Control+A.")
        val parts = spec.split('+').map { it.trim() }
        var meta = 0
        parts.dropLast(1).forEach { m ->
            meta = meta or when (m.lowercase()) {
                "control", "ctrl" -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
                "shift" -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                "alt" -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
                "meta", "cmd", "command" -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
                else -> return err("Unknown modifier \"$m\".")
            }
        }
        val code = keyCode(parts.last()) ?: return err("Unknown key \"${parts.last()}\".")
        BrowserBridge.setLabel("Pressing $spec")
        key(code, meta)
        settle()
        return ok("Pressed $spec. " + pageLine() + consoleNote(before))
    }

    private suspend fun select(args: JSONObject): JSONObject {
        val point = call("point(${spec(args)})")
        if (point == null || point.has("error")) return notFound(args)
        val (x, y) = toPx(point)
        ui.moveCursor(x, y, "Choose · " + args.optString("value").take(20))
        ui.pulse()
        val result = call("select(${spec(args, JSONObject().put("value", args.optString("value")))})") ?: return err("Could not choose.")
        if (result.has("error")) {
            val options = result.optJSONArray("options")
            return err(result.optString("error") + (options?.let { "\nOptions: " + (0 until it.length()).joinToString(", ") { i -> it.getString(i) } } ?: ""))
        }
        settle()
        return ok("Selected \"${result.optString("value")}\" in ${describe(result)}. " + pageLine())
    }

    private suspend fun check(args: JSONObject): JSONObject {
        val want = if (args.has("checked")) args.optBoolean("checked") else true
        val state = call("state(${spec(args)})")
        if (state == null || state.has("error")) return notFound(args)
        if (state.optBoolean("checked") == want) return ok("${describe(state)} is already ${if (want) "checked" else "unchecked"}.")
        return click(args).also {
            if (it.has("error")) return it
        }.let {
            val after = call("state(${spec(args)})")
            ok("${describe(state)} is now ${if (after?.optBoolean("checked") == true) "checked" else "unchecked"}. " + pageLine())
        }
    }

    private suspend fun hover(args: JSONObject): JSONObject {
        val (x, y, target) = locate(args) ?: return notFound(args)
        ui.moveCursor(x, y, "Hover")
        call("hover(${spec(args)})")
        delay(300)
        return ok("Hovering over ${target?.let(::describe) ?: "$x, $y"}.")
    }

    private suspend fun scroll(args: JSONObject): JSONObject {
        if (args.has("ref") || args.has("selector") || args.has("text")) {
            val (x, y, target) = locate(args) ?: return notFound(args)
            ui.moveCursor(x, y, "Scroll here")
            return ok("Scrolled ${target?.let(::describe) ?: "the element"} into view. " + position())
        }
        val direction = args.optString("direction", "down").lowercase()
        when (direction) {
            "top" -> js("(document.scrollingElement||document.documentElement).scrollTo({top:0,behavior:'smooth'})")
            "bottom" -> js("(document.scrollingElement||document.documentElement).scrollTo({top:1e9,behavior:'smooth'})")
            "up", "down" -> {
                val pages = args.optDouble("amount", 0.8).coerceIn(0.1, 5.0)
                var remaining = pages
                while (remaining > 0.01) {
                    val step = remaining.coerceAtMost(0.8)
                    swipe(if (direction == "down") step else -step)
                    remaining -= step
                }
            }
            else -> return err("Direction must be up, down, top or bottom.")
        }
        delay(450)
        return ok("Scrolled $direction. " + position())
    }

    private suspend fun waitFor(args: JSONObject): JSONObject {
        val timeout = (args.optDouble("timeout", 10.0) * 1000).toLong().coerceIn(500, 60_000)
        val until = SystemClock.uptimeMillis() + timeout
        val quietMs = (args.optDouble("quiet_ms", 500.0)).toLong().coerceIn(200, 5000)
        val what = when {
            args.optBoolean("captcha_cleared", false) -> "captcha to clear"
            args.has("url") -> "URL containing \"${args.optString("url")}\""
            args.optBoolean("network_idle", false) -> "network idle"
            args.has("text") -> "the text \"${args.optString("text")}\""
            args.has("selector") -> "\"${args.optString("selector")}\""
            else -> "the page to load"
        }
        BrowserBridge.setLabel("Waiting for " + what.take(30))
        var idleSince = 0L
        while (SystemClock.uptimeMillis() < until) {
            when {
                args.optBoolean("captcha_cleared", false) -> {
                    refreshCaptcha()
                    if (!ui.captchaPending()) return ok("Captcha cleared. " + pageLine())
                }
                args.has("url") -> {
                    val loc = surface.url().orEmpty()
                    if (loc.contains(args.optString("url")) && ui.progress() >= 100) return ok("URL matched. " + pageLine())
                }
                args.optBoolean("network_idle", false) -> {
                    if (ui.networkInFlight() == 0) {
                        if (idleSince == 0L) idleSince = SystemClock.uptimeMillis()
                        if (SystemClock.uptimeMillis() - idleSince >= quietMs && ui.progress() >= 100) {
                            return ok("Network idle. " + pageLine())
                        }
                    } else {
                        idleSince = 0L
                    }
                }
                else -> {
                    val has = call("has(${spec(args)})")
                    val ready = ui.progress() >= 100 || ui.progress() >= 95
                    if (has?.optBoolean("found") == true && ready) return ok("Found $what. " + pageLine())
                }
            }
            delay(300)
        }
        return err("Timed out after ${timeout / 1000}s waiting for $what. " + pageLine())
    }

    private suspend fun eval(args: JSONObject): JSONObject {
        val script = args.optString("script")
        if (script.isBlank()) return err("Give a script.")
        val awaitMs = when {
            args.has("await_ms") -> args.optInt("await_ms", 0)
            args.optBoolean("await", false) -> 10_000
            else -> 0
        }.coerceIn(0, 60_000)
        val wrapped = buildEvalWrapper(script, awaitMs)
        val rpcMs = (awaitMs + 5_000L).coerceAtLeast(BrowserBridgeTimeouts.EXTENSION_RPC_MS).coerceAtMost(65_000L)
        val raw = if (surface is GeckoBrowserSurface) {
            surface.evaluateJavascript(wrapped, rpcMs)
        } else {
            js(wrapped)
        }
        val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: raw
        return ok(formatEvalResult(decoded).take(20_000))
    }

    private fun buildEvalWrapper(script: String, awaitMs: Int): String {
        val quoted = JSONObject.quote(script)
        val awaitBlock = if (awaitMs > 0) {
            "if(r&&typeof r.then==='function'){r=await Promise.race([r,new Promise(function(_,rej){setTimeout(function(){rej(new Error('eval_await_timeout'));},$awaitMs);})]);}"
        } else {
            "if(r&&typeof r.then==='function')return JSON.stringify({note:'Returned a promise; set await_ms (milliseconds) or await:true.'});"
        }
        return "(async function(){var __tfLogs=[];function __tfCap(l,a){try{__tfLogs.push({level:l,msg:Array.prototype.map.call(a,function(x){try{return typeof x==='object'?JSON.stringify(x):String(x);}catch(e){return String(x);}}).join(' ')});}catch(e){}}var __o={log:console.log,warn:console.warn,error:console.error,info:console.info,debug:console.debug};['log','info','warn','error','debug'].forEach(function(l){if(!__o[l])return;console[l]=function(){__tfCap(l,arguments);return __o[l].apply(console,arguments);};});try{var r=(0,eval)($quoted);$awaitBlock return JSON.stringify({value:r===undefined?null:r,logs:__tfLogs});}catch(e){return JSON.stringify({error:String(e),logs:__tfLogs});}finally{['log','info','warn','error','debug'].forEach(function(l){if(__o[l])console[l]=__o[l];});}})()"
    }

    private fun formatEvalResult(decoded: String): String {
        val obj = runCatching { JSONObject(decoded) }.getOrNull()
        if (obj == null) return decoded
        pushEvalLogs(obj.optJSONArray("logs"))
        when {
            obj.has("error") -> return obj.optString("error")
            obj.has("note") -> return obj.optString("note")
            obj.has("value") -> {
                val v = obj.get("value")
                return when (v) {
                    null, JSONObject.NULL -> "null"
                    is String -> v
                    is Number, is Boolean -> v.toString()
                    else -> v.toString()
                }
            }
        }
        return decoded
    }

    private fun pushEvalLogs(logs: JSONArray?) {
        if (logs == null || surface is GeckoBrowserSurface) return
        for (i in 0 until logs.length()) {
            val row = logs.optJSONObject(i) ?: continue
            ui.appendConsole(row.optString("level", "log"), row.optString("msg", ""), "eval")
        }
    }

    private suspend fun upload(args: JSONObject): JSONObject {
        val path = args.optString("path")
        if (path.isBlank()) return err("Give the path of the file to upload.")
        val rootfs = ShellPaths.rootfsDir(context)
        val guest = if (path.startsWith("/")) path else workDir(args) + "/" + path
        val file = File(rootfs, guest.trimStart('/'))
        val inside = runCatching { file.canonicalPath.startsWith(rootfs.canonicalPath + File.separator) }.getOrDefault(false)
        if (!inside || !file.isFile || guest.split('/').any { it.startsWith(".") }) return err("No such file: $guest")
        ui.offerUpload(file)
        val result = click(args)
        if (result.has("error")) return result
        val until = SystemClock.uptimeMillis() + 3000
        while (!ui.uploadTaken() && SystemClock.uptimeMillis() < until) delay(100)
        return if (ui.uploadTaken()) ok("Chose ${file.name} for upload. " + pageLine()) else err("The page did not ask for a file after the click. Is that a file field?")
    }

    // ---- Screenshots -------------------------------------------------------------------------

    private suspend fun capturePageBitmap(): Bitmap? {
        val gecko = surface as? GeckoBrowserSurface
        gecko?.captureScreenshot()?.let { return it }
        val bitmap = surface.createScreenshotBitmap() ?: return null
        surface.drawScreenshot(Canvas(bitmap))
        return bitmap
    }

    private suspend fun screenshot(args: JSONObject): JSONObject {
        BrowserBridge.setLabel("Taking a screenshot")
        delay(80)
        if (surface.width() == 0 || surface.height() == 0) return err("The browser is not on screen.")
        val baseline = args.optString("baseline").trim()
        if (baseline.isNotEmpty()) {
            val rootfs = ShellPaths.rootfsDir(context)
            val guest = if (baseline.startsWith("/")) baseline else workDir(args) + "/" + baseline
            val file = File(rootfs, guest.trimStart('/'))
            if (!file.isFile) return err("Baseline not found: $guest")
            val baseBmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?: return err("Could not read baseline image.")
            var shot = capturePageBitmap() ?: return err("The browser is not on screen.")
            val match = pictureHash(baseBmp) == pictureHash(shot)
            baseBmp.recycle()
            shot.recycle()
            return ok(if (match) "Screenshot matches baseline." else "Screenshot differs from baseline (not an error).")
        }
        // A capture taken while the page is still drawing (right after a scroll, say) can come
        // out as one flat colour; give it a moment and take it again.
        // A picture of one element: bring it into view and remember where it is.
        val aim = JSONObject().also { t -> listOf("ref", "selector", "text").forEach { k -> if (args.has(k)) t.put(k, args.get(k)) } }
        var region: JSONObject? = null
        if (aim.length() > 0) {
            region = call("box($aim)")
            if (region == null || region.has("error")) return err("Could not find that element to take a picture of it.")
            delay(150)
        }
        var bitmap = capturePageBitmap() ?: return err("The browser is not on screen.")
        var tries = 0
        while (isBlank(bitmap) && tries < 4) {
            tries++
            delay(250L * tries)
            bitmap.recycle()
            bitmap = capturePageBitmap() ?: return err("The browser is not on screen.")
        }
        if (region != null) {
            val scale = bitmap.width / region.optDouble("vw", bitmap.width.toDouble()).coerceAtLeast(1.0)
            val x = (region.optDouble("x") * scale).toInt().coerceIn(0, bitmap.width - 8)
            val y = (region.optDouble("y") * scale).toInt().coerceIn(0, bitmap.height - 8)
            val w = (region.optDouble("w") * scale).toInt().coerceAtMost(bitmap.width - x)
            val h = (region.optDouble("h") * scale).toInt().coerceAtMost(bitmap.height - y)
            if (w >= 8 && h >= 8) {
                val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
                bitmap.recycle()
                bitmap = cropped
            }
        }
        val blank = isBlank(bitmap)
        val result = JSONObject()
        // The WebView may only be touched on the main thread; read what is needed first.
        val shown = surface.url()?.let(Preview::display).orEmpty()
        return withContext(Dispatchers.IO) {
            val summary = StringBuilder("Screenshot of ").append(shown)
            // Kept in the project only when asked; otherwise a temporary file the agent can open,
            // since checking work can mean dozens of screenshots.
            val keep = args.optBoolean("save", false)
            // Asked to skip a picture that would look the same as the last one.
            val hash = pictureHash(bitmap)
            if (args.optBoolean("if_changed", false) && !keep && !blank && hash == lastShotHash) {
                bitmap.recycle()
                return@withContext result.put(
                    "text",
                    "Unchanged since the last screenshot" + (lastShotPath?.let { " ($it)" } ?: "") + ".",
                )
            }
            lastShotHash = hash
            lastShotPath = null
            if (keep || !args.optBoolean("image", false)) {
                val rootfs = ShellPaths.rootfsDir(context)
                val guestDir = if (keep) workDir(args) + "/screenshots" else TEMP_SHOTS
                val dir = File(rootfs, guestDir.trimStart('/')).apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", java.util.Locale.US).format(java.util.Date())
                // Kept screenshots stay full-quality PNG; the throwaway ones the agent opens to look
                // are a smaller JPEG, because the picture is read in tokens by its size.
                val file = File(dir, "browser-$stamp." + if (keep) "png" else "jpg")
                file.outputStream().use { out ->
                    if (keep) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } else {
                        val s = (1280f / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
                        val small = if (s < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt().coerceAtLeast(1), (bitmap.height * s).toInt().coerceAtLeast(1), true) else bitmap
                        small.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        if (small !== bitmap) small.recycle()
                    }
                }
                if (!keep) pruneTemporary(dir)
                val guest = "$guestDir/${file.name}"
                result.put("path", guest)
                if (!keep) lastShotPath = guest
                summary.append(if (keep) " saved to " else " (temporary) at ").append(guest)
            }
            if (args.optBoolean("image", false)) {
                val scale = (1024f / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
                val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
                val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
                if (small !== bitmap) small.recycle()
                result.put("image", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("mime", "image/jpeg")
            }
            bitmap.recycle()
            if (blank) summary.append(". It is a single flat colour: the page may be blank or still drawing; scroll a little or wait, then take another")
            result.put("text", summary.append('.').toString())
        }
    }

    // ---- Input -------------------------------------------------------------------------------

    private suspend fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        touch(down, down, MotionEvent.ACTION_DOWN, x, y)
        delay(70)
        touch(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)
    }

    /** A finger drag over [pages] of the view (positive scrolls down), ending still so it does not fling. */
    private suspend fun swipe(pages: Double) {
        val x = surface.width() / 2f
        val distance = (surface.height() * 0.6f * pages.toFloat() / 0.8f).coerceIn(-surface.height() * 0.7f, surface.height() * 0.7f)
        val startY = surface.height() / 2f + distance / 2f
        val endY = surface.height() / 2f - distance / 2f
        ui.moveCursor(x, startY, "Scroll")
        val down = SystemClock.uptimeMillis()
        touch(down, down, MotionEvent.ACTION_DOWN, x, startY)
        val steps = 14
        for (i in 1..steps) {
            delay(22)
            val y = startY + (endY - startY) * i / steps
            touch(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y)
        }
        repeat(4) {
            delay(30)
            touch(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, endY)
        }
        touch(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, endY)
    }

    private fun touch(down: Long, time: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, time, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        surface.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun key(code: Int, meta: Int) {
        surface.requestFocus()
        val down = SystemClock.uptimeMillis()
        surface.dispatchKeyEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0, meta))
        surface.dispatchKeyEvent(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun keyCode(name: String): Int? = when (name.lowercase()) {
        "enter", "return" -> KeyEvent.KEYCODE_ENTER
        "tab" -> KeyEvent.KEYCODE_TAB
        "escape", "esc" -> KeyEvent.KEYCODE_ESCAPE
        "backspace" -> KeyEvent.KEYCODE_DEL
        "delete", "del" -> KeyEvent.KEYCODE_FORWARD_DEL
        "space", " " -> KeyEvent.KEYCODE_SPACE
        "arrowup", "up" -> KeyEvent.KEYCODE_DPAD_UP
        "arrowdown", "down" -> KeyEvent.KEYCODE_DPAD_DOWN
        "arrowleft", "left" -> KeyEvent.KEYCODE_DPAD_LEFT
        "arrowright", "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "home" -> KeyEvent.KEYCODE_MOVE_HOME
        "end" -> KeyEvent.KEYCODE_MOVE_END
        "pageup" -> KeyEvent.KEYCODE_PAGE_UP
        "pagedown" -> KeyEvent.KEYCODE_PAGE_DOWN
        else -> when {
            name.length == 1 && name[0].isLetter() -> KeyEvent.KEYCODE_A + (name.lowercase()[0] - 'a')
            name.length == 1 && name[0].isDigit() -> KeyEvent.KEYCODE_0 + (name[0] - '0')
            Regex("^f([1-9]|1[0-2])$", RegexOption.IGNORE_CASE).matches(name) -> KeyEvent.KEYCODE_F1 + name.drop(1).toInt() - 1
            else -> null
        }
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private data class Located(val x: Float, val y: Float, val target: JSONObject?)

    /** An element (by ref, selector or text) scrolled into view, or a point given as x/y in CSS pixels. */
    private suspend fun locate(args: JSONObject): Located? {
        if (args.has("x") && args.has("y") && !args.has("ref") && !args.has("text") && !args.has("selector")) {
            val vp = call("viewport()") ?: return null
            val scale = surface.width() / vp.optDouble("vw", surface.width().toDouble()).toFloat()
            return Located(args.optDouble("x").toFloat() * scale, args.optDouble("y").toFloat() * scale, null)
        }
        val point = call("point(${spec(args)})") ?: return null
        if (point.has("error")) return null
        delay(60)
        // Scrolling it into view may have moved it; measure again once it has settled.
        val settled = call("point(${spec(args)})") ?: point
        val (x, y) = toPx(settled)
        return Located(x, y, settled)
    }

    /** The agent's project (from the folder it runs in), else the preview's, else home. */
    private fun workDir(args: JSONObject): String {
        val cwd = args.optString("cwd").trimEnd('/')
        Regex("^/root/projects/[^/]+").find(cwd)?.let { return it.value }
        if (cwd.startsWith("/root/") || cwd == "/root") return ui.projectDir() ?: cwd
        return ui.projectDir() ?: "/root"
    }

    private fun toPx(point: JSONObject): Pair<Float, Float> {
        val surfaceW = surface.width().coerceAtLeast(1)
        val vw = point.optDouble("vw", surfaceW.toDouble()).toFloat().coerceAtLeast(1f)
        val scale = surfaceW / vw
        val maxX = surface.width().coerceAtLeast(1).toFloat()
        val maxY = surface.height().coerceAtLeast(1).toFloat()
        val x = (point.optDouble("x").toFloat() * scale).coerceIn(1f, maxX - 1f)
        val y = (point.optDouble("y").toFloat() * scale).coerceIn(1f, maxY - 1f)
        return x to y
    }

    private fun spec(args: JSONObject, extra: JSONObject = JSONObject()): String {
        val s = JSONObject()
        if (args.has("ref")) s.put("ref", args.optInt("ref"))
        if (args.has("selector")) s.put("selector", args.optString("selector"))
        if (args.has("text") && !extra.has("text")) s.put("text", args.optString("text"))
        if (args.has("frame")) {
            val f = args.opt("frame")
            val frame = JSONObject()
            when (f) {
                is Number -> frame.put("index", f.toInt())
                is String -> if (f.all { it.isDigit() }) frame.put("index", f.toInt()) else frame.put("url", f)
            }
            if (frame.length() > 0) s.put("frame", frame)
        }
        if (args.optBoolean("captcha_cleared", false)) extra.put("captcha_cleared", true)
        if (args.has("url") && extra.length() == 0) extra.put("url", args.optString("url"))
        extra.keys().forEach { s.put(it, extra.get(it)) }
        return s.toString()
    }

    private fun describe(o: JSONObject): String {
        val name = o.optString("name")
        return "[${o.optInt("ref")}] ${o.optString("role")}" + if (name.isNotEmpty()) " \"$name\"" else ""
    }

    private fun notFound(args: JSONObject): JSONObject = err(
        "No element matches " + when {
            args.has("ref") -> "[${args.optInt("ref")}]. The page may have changed; run snapshot again."
            args.has("selector") -> "the selector \"${args.optString("selector")}\"."
            args.has("text") -> "the text \"${args.optString("text")}\"."
            else -> "that. Give ref, text or selector."
        },
    )

    private suspend fun pageLine(): String {
        val title = runCatching {
            JSONTokener(js("document.title", BrowserBridgeTimeouts.READY_STATE_EVAL_MS))
                .nextValue() as? String
        }.getOrNull().orEmpty()
        return "Page: " + title.ifBlank { "(untitled)" } + " · " + (surface.url()?.let(Preview::display) ?: "")
    }

    private suspend fun position(): String {
        val p = call("pos()") ?: return ""
        return "Now at ${p.optInt("scrollY")} of ${p.optInt("scrollHeight")} (view ${p.optInt("viewHeight")})."
    }

    private fun consoleNote(before: Int): String {
        val errors = ui.consoleErrorsSince(before)
        return if (errors > 0) " $errors new console error(s): " + ui.consoleText(true, before).lines().drop(1).take(3).joinToString(" | ") else ""
    }

    /** Waits for what a tap or key may have started: a navigation, a re-render. */
    private suspend fun settle() {
        delay(300)
        val until = SystemClock.uptimeMillis() + 10_000
        while (ui.progress() < 100 && SystemClock.uptimeMillis() < until) delay(100)
        delay(150)
    }

    private fun openedLine(requested: String? = null): String {
        val shown = surface.url()?.takeUnless { it.isBlank() } ?: requested.orEmpty()
        return "Page: " + Preview.display(shown)
    }

    private suspend fun awaitLoad(requested: String? = null) {
        delay(80)
        val until = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < until) {
            if (ui.loadError() != null) return
            val url = surface.url()?.takeUnless { it.isBlank() || it == "about:blank" } ?: run {
                delay(50)
                continue
            }
            if (requested != null && !Preview.sameBrowseTarget(requested, url)) {
                delay(50)
                continue
            }
            when {
                ui.progress() >= 100 -> {
                    delay(200)
                    return
                }
                ui.progress() >= 90 && requested != null -> {
                    delay(300)
                    if (Preview.sameBrowseTarget(requested, surface.url().orEmpty())) return
                }
            }
            delay(50)
        }
    }

    private suspend fun js(
        code: String,
        timeoutMs: Long = BrowserBridgeTimeouts.EXTENSION_RPC_MS,
    ): String = surface.evaluateJavascript(code, timeoutMs)

    private fun bridgeErrorNeedsPageFallback(err: String): Boolean =
        err.isNotBlank() && (
            err in TF_BRIDGE_FALLBACK_ERRORS ||
                TF_BRIDGE_FALLBACK_ERRORS.any { token -> err.contains(token) }
            )

    private fun evalResponseFailed(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty() || t == "null") return true
        return bridgeErrorNeedsPageFallback(t) ||
            (t.startsWith("{") && t.contains("\"error\""))
    }

    /** Calls a helper in the page (installing it first) and parses the JSON it returns. */
    private suspend fun call(expression: String): JSONObject? {
        val geckoSurface = surface as? GeckoBrowserSurface
        if (geckoSurface != null) {
            val viaTf = geckoSurface.callTf(expression)
            val err = viaTf?.optString("error").orEmpty()
            if (err.isEmpty()) return viaTf
            if (bridgeErrorNeedsPageFallback(err)) {
                return callTfViaPageEval(geckoSurface, expression) ?: viaTf
            }
            return viaTf
        }
        val raw = js(
            "(function(){${BrowserPageScript.HELPER};try{return JSON.stringify(window.__tf.$expression);}catch(e){return JSON.stringify({error:String(e)});}})()",
        )
        return BrowserJsResults.parseHelperCall(raw)
    }

    /** Runs `window.__tf` in the page context when the content-script bridge is not ready. */
    private suspend fun callTfViaPageEval(gecko: GeckoBrowserSurface, expression: String): JSONObject? {
        val script =
            "(function(){${BrowserPageScript.HELPER};try{return JSON.stringify(window.__tf.$expression);}catch(e){return JSON.stringify({error:String(e)});}})()"
        val raw = gecko.evaluateJavascript(script, BrowserBridgeTimeouts.EXTENSION_RPC_MS)
        return BrowserJsResults.parseHelperCall(raw)
    }

    private fun ok(text: String) = JSONObject().put("text", text)

    private fun err(text: String) = JSONObject().put("error", text)

    /**
     * True when a capture is one colour all over. It is shrunk with filtering first, so even thin
     * text anywhere on a plain page shows up as a slightly different pixel.
     */
    /** A fingerprint of the picture, coarse enough to be cheap and fine enough to notice changed text. */
    private fun pictureHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
        var h = 1125899906842597L
        for (y in 0 until 96) for (x in 0 until 96) h = 31 * h + small.getPixel(x, y)
        small.recycle()
        return h
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        val small = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
        val pixels = IntArray(96 * 96)
        small.getPixels(pixels, 0, 96, 0, 0, 96, 96)
        if (small !== bitmap) small.recycle()
        val first = pixels[0]
        return pixels.all { it == first }
    }

    /** Keeps the newest few temporary screenshots and nothing older than a day. */
    private fun pruneTemporary(dir: File) {
        val shots = dir.listFiles { f -> f.isFile && f.name.startsWith("browser-") }?.sortedByDescending { it.lastModified() } ?: return
        val dayAgo = System.currentTimeMillis() - 24L * 3600 * 1000
        shots.forEachIndexed { i, f -> if (i >= KEEP_TEMP_SHOTS || f.lastModified() < dayAgo) f.delete() }
    }
}
