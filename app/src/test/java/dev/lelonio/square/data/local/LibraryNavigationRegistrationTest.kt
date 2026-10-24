package dev.lelonio.square.data.local

import dev.lelonio.square.data.MusicSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A destination must be registered while NavHost builds, not inside another screen's content. */
class LibraryNavigationRegistrationTest {
    private val source = listOf(File("src/main/java/dev/lelonio/square/ui/SquareApp.kt"),
        File("app/src/main/java/dev/lelonio/square/ui/SquareApp.kt")).first { it.isFile }.readText()

    // Ignore Kotlin comments and literals so their braces cannot masquerade as graph nesting.
    private fun codeOnly(text: String): String = text.replace(
        Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\n]*|/\\*[\\s\\S]*?\\*/"),
    ) { match -> match.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }

    private fun assertRegisteredBesideHome(sourceName: MusicSource) {
        val code = codeOnly(source)
        val declarations = Regex("composable\\s*\\(\\s*Routes\\.([A-Z_]+)\\s*\\)").findAll(code).toList()
        fun depthAt(position: Int): Int = code.take(position).sumOf { when (it) { '{' -> 1; '}' -> -1; else -> 0 } }
        val home = declarations.single { it.groupValues[1] == "HOME" }
        val library = declarations.single { it.groupValues[1] == "LIBRARY" }
        assertEquals("Library must be registered before composing any page: $sourceName",
            depthAt(home.range.first), depthAt(library.range.first))
    }

    @Test fun spotifyLibraryIsRegisteredIndependentlyOfSearchAndItsData() = assertRegisteredBesideHome(MusicSource.SPOTIFY)
    @Test fun youtubeLibraryIsRegisteredIndependentlyOfSearchAndItsData() = assertRegisteredBesideHome(MusicSource.YOUTUBE_MUSIC)
    @Test fun localLibraryIsRegisteredIndependentlyOfSearchAndItsData() = assertRegisteredBesideHome(MusicSource.LOCAL)

    @Test fun noDestinationIsDeclaredInsideAnotherDestinationContent() {
        val code = codeOnly(source)
        val declarations = Regex("composable\\s*\\(\\s*Routes\\.([A-Z_]+)\\s*\\)").findAll(code).toList()
        val depths = declarations.map { match -> code.take(match.range.first).sumOf { when (it) { '{' -> 1; '}' -> -1; else -> 0 } } }
        assertTrue("No declarative route may depend on opening another screen: ${declarations.mapIndexed { i, m -> m.groupValues[1] to depths[i] }}",
            depths.distinct().size == 1)
    }
    @Test fun sourceRouteChangesRecreateTheCachedTabDefinitionsAndAnimationCollectors() {
        assertTrue(source.contains("androidx.compose.runtime.key(visibleTabs)"))
        assertTrue(source.contains("contentKey = listOf(activeTab, searching, searchOpen, tabAccent, barInk, visibleTabs)"))
    }

}
