package dev.lelonio.square.thor.input

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.ui.window.DialogWindowProvider

/** Modal Android windows forward to the SAME dispatcher, retaining their own back
 * handling and key target. Reference counting avoids one adapter per control. */
class ThorWindowInput {
    private data class Binding(val original: Window.Callback, val adapter: Window.Callback, var users: Int)
    private val windows = mutableMapOf<Window, Binding>()
    fun key(event: KeyEvent): Boolean? = windows.values.lastOrNull()?.adapter?.dispatchKeyEvent(event)
    fun motion(event: MotionEvent): Boolean? = windows.values.lastOrNull()?.adapter?.dispatchGenericMotionEvent(event)

    fun attach(window: Window, input: ThorInputDispatcher,
               moveFocus: (AnalogNavigation.Direction) -> Boolean, ensureFocus: () -> Unit): () -> Unit {
        val existing = windows[window]
        if (existing != null) existing.users++ else {
            val original = window.callback
            val adapter = object : Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                    input.key(event, original::dispatchKeyEvent, ensureFocus, onBack = {
                        original.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                        original.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                    }, moveFocus = moveFocus) || original.dispatchKeyEvent(event)

                override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
                    input.motion(event, original::dispatchKeyEvent, ensureFocus, moveFocus) ||
                        original.dispatchGenericMotionEvent(event)

                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    ThorInteraction.touch()
                    input.stop()
                    return original.dispatchTouchEvent(event)
                }
            }
            windows[window] = Binding(original, adapter, 1)
            window.callback = adapter
        }
        return {
            val binding = windows[window]
            if (binding != null && --binding.users == 0) {
                input.stop()
                if (window.callback === binding.adapter) window.callback = binding.original
                windows.remove(window)
            }
        }
    }

    fun close() {
        windows.forEach { (window, binding) ->
            if (window.callback === binding.adapter) window.callback = binding.original
        }
        windows.clear()
    }

    companion object {
        fun dialogWindow(view: View): Window? {
            var parent = view.parent
            while (parent != null) {
                if (parent is DialogWindowProvider) return parent.window
                parent = parent.parent
            }
            return null
        }
    }
}
