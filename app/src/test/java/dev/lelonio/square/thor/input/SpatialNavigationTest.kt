package dev.lelonio.square.thor.input

import org.junit.Assert.*
import org.junit.Test

class SpatialNavigationTest {
    @Test fun firstDirectionPreparesInputEntersAndMovesInOneCall() {
        val calls = mutableListOf<String>()
        val navigation = SpatialNavigation(
            prepare = { calls += "keyboard" }, hasFocus = { false },
            enter = { calls += "enter"; true }, move = { calls += it.name; true },
        )
        assertTrue(navigation.move(AnalogNavigation.Direction.DOWN))
        assertEquals(listOf("keyboard", "enter", "DOWN"), calls)
    }

    @Test fun movingExistingFocusDoesNotResetToTheFirstItem() {
        var entries = 0
        val directions = mutableListOf<AnalogNavigation.Direction>()
        val navigation = SpatialNavigation({}, { true }, { entries++; true }, { directions += it; true })
        for (direction in AnalogNavigation.Direction.entries) assertTrue(navigation.move(direction))
        assertEquals(0, entries)
        assertEquals(AnalogNavigation.Direction.entries, directions)
    }

    @Test fun anEmptySceneDoesNotPretendToHaveMoved() {
        var moves = 0
        val navigation = SpatialNavigation({}, { false }, { false }, { moves++; true })
        assertFalse(navigation.move(AnalogNavigation.Direction.RIGHT))
        assertEquals(0, moves)
    }

    @Test fun aSpatialBoundaryIsReportedWithoutEnteringAgain() {
        var entries = 0
        val navigation = SpatialNavigation({}, { true }, { entries++; true }, { false })
        assertFalse(navigation.move(AnalogNavigation.Direction.UP))
        assertEquals(0, entries)
    }
}
