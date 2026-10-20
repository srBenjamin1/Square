package dev.lelonio.square.thor.input

import kotlin.math.abs

/** Hysteresis and timed repeat, independent of how often the driver emits events. */
class AnalogNavigation {
    enum class Direction { UP, DOWN, LEFT, RIGHT }
    private var held: Direction? = null
    private var nextAt = 0L
    fun step(x: Float, y: Float, now: Long): Direction? {
        val strength = maxOf(abs(x), abs(y))
        if (strength < 0.30f) { held = null; return null }
        if (strength < 0.55f) return null
        val horizontal = when (held) {
            Direction.LEFT, Direction.RIGHT -> abs(x) >= abs(y) * 0.8f
            Direction.UP, Direction.DOWN -> abs(x) > abs(y) * 1.25f
            null -> abs(x) > abs(y)
        }
        val direction = if (horizontal) {
            if (x < 0) Direction.LEFT else Direction.RIGHT
        } else if (y < 0) Direction.UP else Direction.DOWN
        if (direction != held) {
            held = direction
            nextAt = now + 350L
            return direction
        }
        if (now < nextAt) return null
        nextAt = now + 130L
        return direction
    }
    fun reset() { held = null; nextAt = 0 }
}

/** Key and axis reports of the same trigger share one latch. */
class TriggerLatch {
    private var keyHeld = false
    private var axisHeld = false
    fun key(down: Boolean): Boolean {
        val fire = down && !keyHeld && !axisHeld
        keyHeld = down
        return fire
    }
    fun axis(value: Float): Boolean {
        val fire = value >= 0.65f && !axisHeld && !keyHeld
        if (value >= 0.65f) axisHeld = true
        else if (value <= 0.25f) axisHeld = false
        return fire
    }
    fun reset() { keyHeld = false; axisHeld = false }
}
