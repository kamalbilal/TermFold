package com.termfold.app.preview.network

import android.content.Context
import com.termfold.app.preview.engine.GeckoTermfoldExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

object NetworkRulesHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    @Volatile
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun store(): NetworkRuleStore? {
        val ctx = appContext ?: return null
        return NetworkRuleStore.forApp(ctx)
    }

    fun notifyChanged() {
        _revision.update { it + 1 }
        pushToExtension()
    }

    fun pushToExtension() {
        val store = store() ?: return
        scope.launch {
            GeckoTermfoldExtension.request(
                JSONObject()
                    .put("op", "set_rules")
                    .put("rulesBySession", store.rulesBySessionJson()),
            )
        }
    }

    fun bindSession(sessionKey: String) {
        scope.launch {
            GeckoTermfoldExtension.request(
                JSONObject().put("op", "bind_session").put("sessionKey", sessionKey),
            )
            NetworkCaptureHub.pushCaptureOptions(sessionKey)
        }
    }
}
