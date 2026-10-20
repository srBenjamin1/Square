package dev.lelonio.square.thor.input

import org.junit.Assert.*
import org.junit.Test

class AnalogNavigationTest {
    @Test fun restingNoiseCannotMoveFocus() {
        val input = AnalogNavigation()
        repeat(200) { assertNull(input.step(0.12f, -0.2f, it * 16L)) }
    }

    @Test fun aHeldStickRepeatsByTimeRatherThanDriverEventRate() {
        val input = AnalogNavigation()
        assertEquals(AnalogNavigation.Direction.RIGHT, input.step(1f, 0f, 0))
        for (t in 1L..349L) assertNull(input.step(1f, 0f, t))
        assertEquals(AnalogNavigation.Direction.RIGHT, input.step(1f, 0f, 350))
        assertNull(input.step(1f, 0f, 479))
        assertEquals(AnalogNavigation.Direction.RIGHT, input.step(1f, 0f, 480))
    }

    @Test fun dominantAxisDoesNotOscillateAlongTheDiagonal() {
        val input = AnalogNavigation()
        assertEquals(AnalogNavigation.Direction.RIGHT, input.step(0.8f, 0.7f, 0))
        assertNull(input.step(0.7f, 0.8f, 16))
        assertEquals(AnalogNavigation.Direction.DOWN, input.step(0.3f, 1f, 32))
    }

    @Test fun releasingBelowDeadZoneRearmsAnImmediateMove() {
        val input = AnalogNavigation()
        input.step(0f, -1f, 0)
        assertNull(input.step(0f, -0.4f, 16))
        assertNull(input.step(0f, 0f, 32))
        assertEquals(AnalogNavigation.Direction.UP, input.step(0f, -1f, 48))
    }

    @Test fun reversingDirectionDoesNotWaitForTheOldRepeatTimer() {
        val input = AnalogNavigation()
        input.step(1f, 0f, 0)
        assertEquals(AnalogNavigation.Direction.LEFT, input.step(-1f, 0f, 16))
        input.reset()
        assertEquals(AnalogNavigation.Direction.DOWN, input.step(0f, 1f, 17))
    }

    @Test fun axisThenKeyIsOnlyOneTrackSkip() {
        val latch = TriggerLatch()
        assertTrue(latch.axis(0.8f))
        assertFalse(latch.key(true))
        assertFalse(latch.axis(1f))
        assertFalse(latch.key(false))
        assertFalse(latch.axis(0f))
        assertTrue(latch.axis(0.9f))
    }

    @Test fun keyThenAxisIsOnlyOneTrackSkip() {
        val latch = TriggerLatch()
        assertTrue(latch.key(true))
        assertFalse(latch.axis(1f))
        assertFalse(latch.key(true))
        assertFalse(latch.axis(0f))
        assertFalse(latch.key(false))
        assertTrue(latch.key(true))
    }

    @Test fun triggerHysteresisRejectsNoiseUntilFullyReleased() {
        val latch = TriggerLatch()
        assertFalse(latch.axis(0.6f))
        assertTrue(latch.axis(0.7f))
        assertFalse(latch.axis(0.5f))
        assertFalse(latch.axis(0.7f))
        assertFalse(latch.axis(0.2f))
        assertTrue(latch.axis(0.7f))
        latch.reset()
        assertTrue(latch.key(true))
    }
}
