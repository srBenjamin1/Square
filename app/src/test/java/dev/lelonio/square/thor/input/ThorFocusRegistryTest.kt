package dev.lelonio.square.thor.input

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class ThorFocusRegistryTest {
    @Test fun focusRestorationIsScopedToThePageAndWindow() {
        val registry = ThorFocusRegistry()
        val owner = Any()
        val otherWindow = Any()
        var selected = ""
        registry.candidate("track", owner, "album", { selected = "track"; true })
        registry.candidate("other", otherWindow, "album", { selected = "other"; true }, 200)
        registry.focus("track", Rect.Zero, owner, "album")
        assertTrue(registry.restore(owner, "album"))
        assertEquals("track", selected)
        assertFalse(registry.restore(owner, "library"))
        registry.candidate("track", owner, "album", null)
        assertFalse(registry.restore(owner, "album"))
    }

    @Test fun anAlbumInitiallyPrefersAPlayableRowToHeaderActions() {
        val registry = ThorFocusRegistry()
        val owner = Any()
        var selected = ""
        registry.candidate("header", owner, "album", { selected = "header"; true })
        registry.candidate("track", owner, "album", { selected = "track"; true }, 100)
        assertTrue(registry.restore(owner, "album"))
        assertEquals("track", selected)
    }

    @Test fun toolsAndDialogsEnterTheirOwnSceneWithoutTouchingAnotherWindow() {
        val registry = ThorFocusRegistry()
        val owner = Any()
        val page = Any()
        val tool = Any()
        val dialog = Any()
        var selected = ""
        registry.scene(page, owner, "library", 0) { selected = "library"; true }
        registry.scene(tool, owner, "queue", 1) { selected = "queue"; true }
        registry.scene(Any(), Any(), "other display", 3) { fail("cross-window focus"); false }
        assertTrue(registry.enterScene(owner))
        assertEquals("queue", selected)
        registry.modal(dialog, true)
        assertFalse(registry.enterScene(owner))
        registry.scene(dialog, owner, "menu", 2) { selected = "menu"; true }
        assertTrue(registry.enterScene(owner))
        assertEquals("menu", selected)
        registry.scene(dialog, owner, "menu", 2, null)
        registry.modal(dialog, false)
        registry.scene(tool, owner, "queue", 1, null)
        assertTrue(registry.enterScene(owner))
        assertEquals("library", selected)
    }

    @Test fun editorArrowsRemainLocalWhileVerticalNavigationCanLeave() {
        val registry = ThorFocusRegistry()
        val editor = Any()
        registry.focus(editor, Rect.Zero, localArrows = true)
        assertTrue(registry.handlesDirectionLocally(AnalogNavigation.Direction.LEFT))
        assertTrue(registry.handlesDirectionLocally(AnalogNavigation.Direction.RIGHT))
        assertFalse(registry.handlesDirectionLocally(AnalogNavigation.Direction.UP))
        assertFalse(registry.handlesDirectionLocally(AnalogNavigation.Direction.DOWN))
        registry.focus(editor, null)
        registry.focus(Any(), Rect.Zero, allArrows = true)
        for (direction in AnalogNavigation.Direction.entries) assertTrue(registry.handlesDirectionLocally(direction))
    }

    private class Container : ScrollableState {
        val deltas = mutableListOf<Float>()
        override val isScrollInProgress = false
        override fun dispatchRawDelta(delta: Float): Float { deltas.add(delta); return delta }
        override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
            block(object : ScrollScope {
                override fun scrollBy(pixels: Float): Float = dispatchRawDelta(pixels)
            })
        }
    }

    private fun target(registry: ThorFocusRegistry, container: Container, priority: Int,
                       rect: Rect = Rect(0f, 0f, 100f, 100f), horizontal: Boolean = false): Any {
        val id = Any()
        registry.register(id, container, if (horizontal) Orientation.Horizontal else Orientation.Vertical, priority)
        registry.bounds(id, rect)
        return id
    }

    @Test fun rightStickNeverScrollsTheNavigationBehindTools() {
        val registry = ThorFocusRegistry()
        val navigation = Container()
        val tools = Container()
        target(registry, navigation, 0)
        target(registry, tools, 1)
        registry.scroll(0f, 1f)
        assertTrue(navigation.deltas.isEmpty())
        assertEquals(1, tools.deltas.size)
    }

    @Test fun onlyTheContainerContainingFocusReceivesScrolling() {
        val registry = ThorFocusRegistry()
        val first = Container()
        val second = Container()
        target(registry, first, 0, Rect(0f, 0f, 100f, 100f))
        target(registry, second, 0, Rect(200f, 0f, 300f, 100f))
        registry.focus(Any(), Rect(210f, 10f, 230f, 30f))
        registry.scroll(0f, 0.8f)
        assertTrue(first.deltas.isEmpty())
        assertEquals(1, second.deltas.size)
    }

    @Test fun sliderEditingSuspendsRightStickScrolling() {
        val registry = ThorFocusRegistry()
        val list = Container()
        target(registry, list, 0)
        val slider = Any()
        registry.adjusting(slider, true)
        registry.scroll(0f, 1f)
        assertTrue(list.deltas.isEmpty())
        registry.adjusting(slider, false)
        registry.scroll(0f, 1f)
        assertEquals(1, list.deltas.size)
    }

    @Test fun horizontalInputDoesNotAlsoMoveTheVerticalContainer() {
        val registry = ThorFocusRegistry()
        val row = Container()
        val column = Container()
        target(registry, row, 0, horizontal = true)
        target(registry, column, 0)
        registry.scroll(-1f, 0.7f)
        assertEquals(1, row.deltas.size)
        assertTrue(row.deltas.single() < 0)
        assertTrue(column.deltas.isEmpty())
    }

    @Test fun touchModalityIsSharedWithoutRemovingLocalFocus() {
        val registry = ThorFocusRegistry()
        registry.focus(Any(), Rect(0f, 0f, 20f, 20f))
        ThorInteraction.gamepad()
        assertEquals(InputMode.GAMEPAD, ThorInteraction.mode.value)
        ThorInteraction.touch()
        assertEquals(InputMode.TOUCH, ThorInteraction.mode.value)
        assertTrue(registry.hasFocus)
    }

    @Test fun aNonScrollingDialogCannotScrollThePageBehindIt() {
        val registry = ThorFocusRegistry()
        val list = Container()
        target(registry, list, 1)
        val dialog = Any()
        registry.modal(dialog, true)
        registry.scroll(0f, 1f)
        assertTrue(list.deltas.isEmpty())
        registry.modal(dialog, false)
        registry.scroll(0f, 1f)
        assertEquals(1, list.deltas.size)
    }

    @Test fun scrollingPastFocusKeepsTheSameContainerUntilTheStickIsReleased() {
        val registry = ThorFocusRegistry()
        val first = Container()
        val second = Container()
        target(registry, first, 0, Rect(100f, 100f, 300f, 300f))
        target(registry, second, 0, Rect(0f, 0f, 50f, 50f))
        val item = Any()
        registry.focus(item, Rect(120f, 120f, 140f, 140f))
        registry.scroll(0f, 1f)
        registry.focus(item, Rect.Zero)
        registry.scroll(0f, 1f)
        assertEquals(2, first.deltas.size)
        assertTrue(second.deltas.isEmpty())
        registry.scroll(0f, 0f)
        registry.scroll(0f, 1f)
        assertEquals(1, second.deltas.size)
    }
}
