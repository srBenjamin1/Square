package dev.lelonio.square.thor.input

import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.ref.WeakReference

/** Reuses the existing lower dispatcher, with a scoped target for upper player controls.
 * Focus and navigation state remain owned by each window. */
object ThorInputRouter {
    private var lower = WeakReference<ThorInputDispatcher>(null)
    private class Upper(
        val key: (ThorInputDispatcher, KeyEvent) -> Boolean,
        val motion: (ThorInputDispatcher, MotionEvent) -> Boolean,
    )
    private var upper: Upper? = null

    /** Only events delivered to the upper window use this target; lower input stays local. */
    fun attachUpper(
        key: (ThorInputDispatcher, KeyEvent) -> Boolean,
        motion: (ThorInputDispatcher, MotionEvent) -> Boolean,
    ): () -> Unit {
        val binding = Upper(key, motion)
        upper = binding
        return {
            if (upper === binding) {
                lower.get()?.stop()
                upper = null
            }
        }
    }
    fun attach(dispatcher: ThorInputDispatcher) { lower = WeakReference(dispatcher) }
    fun detach(dispatcher: ThorInputDispatcher) {
        if (lower.get() === dispatcher) lower.clear()
    }
    fun key(event: KeyEvent): Boolean = lower.get()?.let { input ->
        upper?.key?.invoke(input, event) ?: input.routedKey(event)
    } ?: false
    fun motion(event: MotionEvent): Boolean = lower.get()?.let { input ->
        upper?.motion?.invoke(input, event) ?: input.routedMotion(event)
    } ?: false
}
