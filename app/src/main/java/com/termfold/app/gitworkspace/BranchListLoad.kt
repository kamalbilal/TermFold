package com.termfold.app.gitworkspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

const val BRANCH_LIST_LOAD_TIMEOUT_MS = 120_000L

const val BRANCH_LIST_ERROR_TIMEOUT = "timeout"

sealed class BranchListLoadState {
    data object Loading : BranchListLoadState()

    data class Ready(val branches: List<String>) : BranchListLoadState()

    data class Error(val message: String) : BranchListLoadState()
}

@Composable
fun rememberBranchListLoad(
    cacheKey: String,
    refreshNonce: Int,
    loader: suspend () -> List<String>,
): BranchListLoadState {
    var state by remember(cacheKey) { mutableStateOf<BranchListLoadState>(BranchListLoadState.Loading) }
    LaunchedEffect(cacheKey, refreshNonce) {
        val force = refreshNonce > 0
        val cached = if (!force) BranchListCache.getSuccessful(cacheKey) else null
        if (!shouldFetchBranchListFromGit(force, cached)) {
            state = BranchListLoadState.Ready(cached!!)
            return@LaunchedEffect
        }
        state = BranchListLoadState.Loading
        try {
            val loaded = withContext(Dispatchers.IO) {
                withTimeout(BRANCH_LIST_LOAD_TIMEOUT_MS) { loader() }
            }
            BranchListCache.putSuccessful(cacheKey, loaded)
            state = BranchListLoadState.Ready(loaded)
        } catch (_: TimeoutCancellationException) {
            state = BranchListLoadState.Error(BRANCH_LIST_ERROR_TIMEOUT)
        } catch (e: Exception) {
            state = BranchListLoadState.Error(e.message?.take(400).orEmpty().ifBlank { "error" })
        }
    }
    return state
}
