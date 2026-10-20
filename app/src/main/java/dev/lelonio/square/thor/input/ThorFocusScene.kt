package dev.lelonio.square.thor.input

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.InputMode as ComposeInputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** A focus entry point for an existing page/tool/menu, not another navigation host. */
@Composable
fun ThorFocusScene(
    sceneKey: String,
    modifier: Modifier = Modifier,
    ready: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!LocalThorEnabled.current) { Box(modifier, content = content); return }
    val registry = LocalThorFocusRegistry.current
    val owner = LocalView.current
    val inputMode = LocalInputModeManager.current
    val manager = LocalFocusManager.current
    val group = remember(sceneKey) { FocusRequester() }
    val identity = remember(sceneKey) { Any() }
    val priority = LocalThorScrollPriority.current + if (ThorWindowInput.dialogWindow(owner) != null) 2 else 0
    val mode by ThorInteraction.mode.collectAsStateWithLifecycle()
    val isReady by rememberUpdatedState(ready)
    val enter: () -> Boolean = {
        if (!isReady) false else {
            inputMode.requestInputMode(ComposeInputMode.Keyboard)
            registry?.restore(owner, sceneKey) == true || group.requestFocus()
        }
    }
    val latestEnter by rememberUpdatedState(enter)
    DisposableEffect(registry, owner, identity, priority) {
        registry?.scene(identity, owner, sceneKey, priority) { latestEnter() }
        onDispose { registry?.scene(identity, owner, sceneKey, priority, null) }
    }
    LaunchedEffect(identity, ready, mode) {
        if (ready && mode == InputMode.GAMEPAD) {
            withFrameNanos { }
            if (registry?.activeScene(owner) === identity) {
                val entered = latestEnter()
                if (dev.lelonio.square.BuildConfig.DEBUG) android.util.Log.d("ThorFocus",
                    "display=${owner.display?.displayId} enter=$sceneKey success=$entered")
            }
        }
    }
    CompositionLocalProvider(LocalThorFocusScene provides sceneKey) {
        Box(modifier.focusRequester(group).focusRestorer().focusGroup().onPreviewKeyEvent { event ->
            // Popups have their own Android window, bypassing Activity.dispatchKeyEvent.
            // Use the same Compose direction search there; sliders/editors keep arrows.
            val key = event.nativeKeyEvent
            val direction = when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> AnalogNavigation.Direction.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> AnalogNavigation.Direction.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> AnalogNavigation.Direction.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> AnalogNavigation.Direction.RIGHT
                else -> null
            }
            if (!ThorInputDispatcher.isController(key) || direction == null ||
                registry?.handlesDirectionLocally(direction) == true) false
            else {
                ThorInteraction.gamepad()
                if (key.action == KeyEvent.ACTION_DOWN) {
                    inputMode.requestInputMode(ComposeInputMode.Keyboard)
                    if (registry?.hasFocusIn(owner) != true) latestEnter()
                    manager.moveFocus(direction.asFocusDirection())
                }
                true
            }
        }, content = content)
    }
}
