package dev.lelonio.square.thor.input

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InputMode { GAMEPAD, TOUCH }

/** Only input modality crosses displays. Focus and navigation remain local. */
object ThorInteraction {
    private val current = MutableStateFlow(InputMode.TOUCH)
    val mode = current.asStateFlow()
    fun gamepad() { current.value = InputMode.GAMEPAD }
    fun touch() { current.value = InputMode.TOUCH }
}

enum class ThorCommand { SETTINGS, SEARCH, PREVIOUS_SECTION, NEXT_SECTION, PREVIOUS, NEXT, PLAY_PAUSE }

/** One instance per lower activity, with buffered requests until NavHost is ready. */
class ThorInputSession {
    val commands = Channel<ThorCommand>(Channel.UNLIMITED)
    var ensureFocus: () -> Unit = {}
    var moveFocus: (AnalogNavigation.Direction) -> Boolean = { false }
    var handlesArrowsLocally: (AnalogNavigation.Direction) -> Boolean = { false }
    var scroll: (Float, Float) -> Unit = { _, _ -> }
    var dispatcher: ThorInputDispatcher? = null
    internal val modalWindows = ThorWindowInput()
}

val LocalThorEnabled = staticCompositionLocalOf { false }
val LocalThorInputSession = staticCompositionLocalOf<ThorInputSession?> { null }
