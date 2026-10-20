package dev.lelonio.square.thor.input

import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.ref.WeakReference

/** Routes events delivered to the upper window to the existing lower dispatcher.
 * No focus, expansion, player or navigation state is shared by this bridge. */
object ThorInputRouter {
    private var lower = WeakReference<ThorInputDispatcher>(null)
    fun attach(dispatcher: ThorInputDispatcher) { lower = WeakReference(dispatcher) }
    fun detach(dispatcher: ThorInputDispatcher) {
        if (lower.get() === dispatcher) lower.clear()
    }
    fun key(event: KeyEvent): Boolean = lower.get()?.routedKey(event) ?: false
    fun motion(event: MotionEvent): Boolean = lower.get()?.routedMotion(event) ?: false
}
