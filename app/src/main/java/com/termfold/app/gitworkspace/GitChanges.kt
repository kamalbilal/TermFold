package com.termfold.app.gitworkspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** VS Code–style source control pane for the active session workspace. */
object GitChanges {

    data class State(
        val open: Boolean = false,
        val guestPath: String? = null,
        val nonce: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun open(guestPath: String) {
        _state.update { current ->
            State(
                open = true,
                guestPath = guestPath,
                nonce = current.nonce + 1,
            )
        }
    }

    fun close() = _state.update { State() }

    fun refresh() = _state.update { it.copy(nonce = it.nonce + 1) }
}
