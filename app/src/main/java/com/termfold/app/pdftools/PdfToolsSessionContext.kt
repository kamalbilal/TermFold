package com.termfold.app.pdftools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PdfToolsSessionContext {
    @Volatile
    var activeSessionId: String? = null

    @Volatile
    var approvedPlanId: String? = null

    @Volatile
    var authzClass: PdfToolsAuthzClass? = null

    @Volatile
    var pendingPlanId: String? = null

    @Volatile
    var approvedPlanJson: String? = null

    private val _pendingPlanJson = MutableStateFlow<String?>(null)
    val pendingPlanJson: StateFlow<String?> = _pendingPlanJson.asStateFlow()

    fun submitPlan(planId: String, raw: String) {
        pendingPlanId = planId
        approvedPlanId = null
        approvedPlanJson = null
        _pendingPlanJson.value = raw
    }

    fun approvePending(): Boolean {
        val id = pendingPlanId ?: return false
        approvedPlanId = id
        approvedPlanJson = _pendingPlanJson.value
        _pendingPlanJson.value = null
        return true
    }

    fun planJsonForApply(): String? =
        approvedPlanJson ?: _pendingPlanJson.value

    fun clearIf(sessionId: String) {
        if (activeSessionId != sessionId) return
        activeSessionId = null
        approvedPlanId = null
        approvedPlanJson = null
        authzClass = null
        pendingPlanId = null
        _pendingPlanJson.value = null
    }
}
