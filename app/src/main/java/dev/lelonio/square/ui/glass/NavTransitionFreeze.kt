package dev.lelonio.square.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Holds the app's glass still while one screen is replacing another.
 *
 * The page backdrop is a recorded layer, and every frame that layer changes it
 * is captured again for the surfaces that refract it — the bars, the sheets,
 * the buttons. Scrolling already stops that: the tab bar's scroll connection
 * says when a list is moving and the glass reuses its last capture until it
 * settles. A navigation is neither a scroll nor a fling, so nothing covered it,
 * and every push and pop paid a full re-record on every frame of its own
 * animation — which is exactly when the phone has the least to spare.
 *
 * Timed rather than tied to the transition itself: Navigation's own
 * `AnimatedContent` does not offer the call site a "running" flag, and the
 * durations here are fixed constants, so a window is enough. It must cover the
 * longest of them and no more — thawing early puts the cost back where it
 * hurts, and freezing late is stale content behind the glass, which is visible.
 *
 * Read during draw, from a plain field rather than snapshot state: a state read
 * in a draw-phase provider invalidates the frame that read it, which is a
 * redraw that never stops.
 */
class NavTransitionFreeze {
    private var startedAtNs = 0L

    val frozen: () -> Boolean = {
        val started = startedAtNs
        started != 0L && System.nanoTime() - started < WINDOW_NS
    }

    fun mark() {
        startedAtNs = System.nanoTime()
    }

    private companion object {
        /** The longest transition in the app plus slop for frame delivery. */
        const val WINDOW_NS = 330_000_000L
    }
}

/** Starts the window again every time [key] changes — a route, or a page. */
@Composable
fun rememberNavTransitionFreeze(key: Any?): NavTransitionFreeze {
    val freeze = remember { NavTransitionFreeze() }
    LaunchedEffect(key) { freeze.mark() }
    return freeze
}
