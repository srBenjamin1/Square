package dev.lelonio.square.thor.ui

import android.view.KeyEvent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import dev.lelonio.square.thor.input.SpatialNavigation
import dev.lelonio.square.thor.input.ThorInputRouter
import dev.lelonio.square.thor.input.ThorFocusScene

@Composable
internal fun UpperPlayerFocusScene(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) ThorFocusScene(sceneKey = "player:upper") { content() }
    else content()
}

/** Reuses the lower dispatcher for events received by the upper player's window. */
@Composable
internal fun UpperPlayerControls(enabled: Boolean, spatial: SpatialNavigation, onBack: (() -> Unit)?) {
    val view = LocalView.current
    val back by rememberUpdatedState(onBack)
    DisposableEffect(enabled, spatial, view) {
        val release = if (enabled) ThorInputRouter.attachUpper(
            key = { input, event ->
                if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B && back == null) input.routedKey(event)
                else input.key(event, target = view::dispatchKeyEvent,
                    ensureFocus = { spatial.ensureFocus() }, moveFocus = spatial::move,
                    onBack = { back?.invoke() })
            },
            motion = { input, event ->
                input.motion(event, target = view::dispatchKeyEvent,
                    ensureFocus = { spatial.ensureFocus() }, moveFocus = spatial::move)
            },
        ) else null
        onDispose { release?.invoke() }
    }
}
