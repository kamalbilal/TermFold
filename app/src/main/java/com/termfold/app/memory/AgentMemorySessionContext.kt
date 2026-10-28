package com.termfold.app.memory

/** Active agent chat session key for private memory (coding, PR review, automation). */
object AgentMemorySessionContext {
    @Volatile
    var activeKey: String? = null
}
