package com.termfold.app.ui.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Forwards scroll to [parent] only after [child] has scrolled as far as it can in that direction.
 * Pre-scroll is not intercepted so the diff list keeps normal finger tracking.
 */
@Composable
fun Modifier.forwardNestedScrollToLazyParent(
    parent: LazyListState,
    child: LazyListState,
    onChainedToParent: () -> Unit = {},
): Modifier {
    val connection = remember(parent, child, onChainedToParent) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val y = available.y
                if (y == 0f) return Offset.Zero
                val stuckAtStart = y < 0f && !child.canScrollBackward
                val stuckAtEnd = y > 0f && !child.canScrollForward
                if (!stuckAtStart && !stuckAtEnd) return Offset.Zero
                // Nested-scroll delta and LazyListState.dispatchRawDelta use opposite signs.
                val parentConsumed = parent.dispatchRawDelta(-y)
                if (parentConsumed == 0f) return Offset.Zero
                onChainedToParent()
                return Offset(0f, y)
            }
        }
    }
    return nestedScroll(connection)
}
