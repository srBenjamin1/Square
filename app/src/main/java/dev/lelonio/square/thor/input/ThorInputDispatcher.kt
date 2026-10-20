package dev.lelonio.square.thor.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.media3.common.Player

enum class ThorPanel { LYRICS, LIBRARY, QUEUE, EQUALIZER }

class ThorInputDispatcher(
    private val currentPanel: () -> ThorPanel,
    private val onPanel: (ThorPanel) -> Unit,
    private val onSearch: () -> Unit,
    private val onContext: () -> Unit,
    private val onNavigate: (Int) -> Unit,
) {
    fun key(event: KeyEvent, player: Player?): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0 && isOneShot(event.keyCode)) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
                if (player?.isPlaying == true) player.pause() else player?.play()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> return false
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_MENU -> onContext()
            KeyEvent.KEYCODE_BUTTON_Y ->
                if (player?.isPlaying == true) player.pause() else player?.play()
            KeyEvent.KEYCODE_BUTTON_L1 -> onPanel(previous(currentPanel()))
            KeyEvent.KEYCODE_BUTTON_R1 -> onPanel(next(currentPanel()))
            KeyEvent.KEYCODE_BUTTON_L2 -> player?.seekToPrevious()
            KeyEvent.KEYCODE_BUTTON_R2 -> player?.seekToNext()
            KeyEvent.KEYCODE_BUTTON_START -> onPanel(ThorPanel.LYRICS)
            KeyEvent.KEYCODE_BUTTON_SELECT -> onSearch()
            KeyEvent.KEYCODE_DPAD_UP -> onNavigate(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> onNavigate(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> onPanel(previous(currentPanel()))
            KeyEvent.KEYCODE_DPAD_RIGHT -> onPanel(next(currentPanel()))
            else -> return false
        }
        return true
    }

    fun motion(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) return false
        val device = event.device ?: return false
        if (device.sources and InputDevice.SOURCE_JOYSTICK == 0) return false
        val axis = event.getAxisValue(MotionEvent.AXIS_Y)
        if (kotlin.math.abs(axis) < 0.35f) return false
        onNavigate(if (axis > 0) 1 else -1)
        return true
    }

    private fun isOneShot(keyCode: Int) =
        keyCode in setOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT)

    private fun next(panel: ThorPanel) = ThorPanel.entries[(panel.ordinal + 1) % ThorPanel.entries.size]
    private fun previous(panel: ThorPanel) = ThorPanel.entries[(panel.ordinal + ThorPanel.entries.size - 1) % ThorPanel.entries.size]
}
