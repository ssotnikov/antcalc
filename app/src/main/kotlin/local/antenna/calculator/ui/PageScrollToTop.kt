package local.antenna.calculator.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.launch

internal val LocalPageScrollToTop = staticCompositionLocalOf<PageScrollToTop?> { null }

internal class PageScrollToTop {
    var action by mutableStateOf<(() -> Unit)?>(null)
}

@Composable
internal fun rememberPageScrollState(): ScrollState {
    val scroll = rememberScrollState()
    BindPageScrollToTop(scroll) {
        if (scroll.canScrollBackward) scroll.animateScrollTo(0)
    }
    return scroll
}

@Composable
internal fun rememberPageLazyListState(): LazyListState {
    val scroll = rememberLazyListState()
    BindPageScrollToTop(scroll)
    return scroll
}

// The journal keeps this list state while a detail screen is open.
@Composable
internal fun BindPageScrollToTop(scroll: LazyListState) = BindPageScrollToTop(scroll) {
    if (scroll.canScrollBackward) scroll.animateScrollToItem(0)
}

@Composable
private fun BindPageScrollToTop(scrollState: Any, scrollToTop: suspend () -> Unit) {
    val target = LocalPageScrollToTop.current ?: return
    val scope = rememberCoroutineScope()
    val currentScrollToTop by rememberUpdatedState(scrollToTop)
    DisposableEffect(target, scrollState, scope) {
        val action: () -> Unit = { scope.launch { currentScrollToTop() } }
        target.action = action
        onDispose {
            if (target.action === action) target.action = null
        }
    }
}
