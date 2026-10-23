package dev.lelonio.square.ui

import androidx.compose.ui.unit.LayoutDirection
import dev.lelonio.square.data.LyricLine
import dev.lelonio.square.ui.player.lyricsDirection
import org.junit.Assert.*
import org.junit.Test

class LyricsDirectionTest {
    private fun direction(text: String, fallback: LayoutDirection) = lyricsDirection(listOf(LyricLine(0, text)), fallback)
    @Test fun englishInRtlDeviceIsLtr() { assertEquals(LayoutDirection.Ltr, direction("Where are you?", LayoutDirection.Rtl)) }
    @Test fun arabicAndHebrewInLtrDeviceAreRtl() {
        assertEquals(LayoutDirection.Rtl, direction("مرحبا بالعالم", LayoutDirection.Ltr))
        assertEquals(LayoutDirection.Rtl, direction("שלום עולם", LayoutDirection.Ltr))
    }
    @Test fun neutralLinesUseDeviceDirection() {
        assertEquals(LayoutDirection.Rtl, direction("123 ?!", LayoutDirection.Rtl))
        assertEquals(LayoutDirection.Ltr, lyricsDirection(emptyList(), LayoutDirection.Ltr))
    }
}
