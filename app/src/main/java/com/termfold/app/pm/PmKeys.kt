package com.termfold.app.pm

object PmKeys {
    const val PREFIX = "pm"

    fun acpSessionKey(sessionId: String): String = "$PREFIX/$sessionId"
}
