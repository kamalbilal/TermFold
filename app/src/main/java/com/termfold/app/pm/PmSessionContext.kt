package com.termfold.app.pm

object PmSessionContext {
    @Volatile
    var activeSessionId: String? = null
}
