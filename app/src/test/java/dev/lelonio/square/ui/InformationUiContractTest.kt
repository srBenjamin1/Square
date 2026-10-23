package dev.lelonio.square.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Structural UI guards complement the workflow tests; these do not simulate Android rendering. */
class InformationUiContractTest {
    private fun source(path: String): String = listOf(File("src/main/java/$path"), File("app/src/main/java/$path"))
        .first { it.isFile }.readText()
    private val playerPath = "dev/lelonio/square/ui/player/"

    @Test fun originalCreditsHaveNoNavigationCallbackOrInteractiveNames() {
        val credits = source(playerPath + "CreditsView.kt")
        assertFalse(credits.contains("onOpen"))
        assertFalse(credits.contains("MetadataLink"))
        assertFalse(credits.contains("clickable"))
        assertFalse(credits.contains("pressable"))
        for (field in listOf("credits.roles", "role.people", "person.name", "person.subroles", "credits.sources")) assertTrue(credits.contains(field))
    }
    @Test fun upperHeaderHasNoAddedInfoButtonAndThorDoesNotExposeInfoTab() {
        val player = source(playerPath + "PlayerScreen.kt")
        assertFalse(player.contains("onOpenInfo"))
        val app = source("dev/lelonio/square/ui/SquareApp.kt")
        val topPanels = app.substringAfter("allowedPanels = if (playerOnly)").substringBefore("showPanelTabs")
        assertFalse(topPanels.contains("PlayerPanel.INFO"))
    }
    @Test fun informationKeepsSeparateMetadataAndReadOnlyCreditSections() {
        val info = source(playerPath + "TrackInformationView.kt")
        assertTrue(info.indexOf("ArtistMetadataLinks(") < info.indexOf("HorizontalDivider("))
        assertTrue(info.indexOf("HorizontalDivider(") < info.indexOf("CreditsView("))
        assertTrue(info.contains("MetadataLink(state.album"))
        assertTrue(info.contains("CreditsView(credits, loading, modifier"))
    }
    @Test fun lowerAndConventionalInfoKeepTheirExistingToolsAndStage() {
        assertTrue(source(playerPath + "PlayerToolsContent.kt").contains("PlayerTool.INFO -> TrackInformationView("))
        assertTrue(source(playerPath + "PlayerScreen.kt").contains("Stage.INFO -> TrackInformationView("))
    }
    @Test fun bothThorPlayAndEnqueuePreserveCatalogueIdentityExtras() {
        val activity = source("dev/lelonio/square/thor/ui/ThorActivity.kt")
        assertEquals(2, Regex("setExtras\\(dev\\.lelonio\\.square\\.ui\\.catalogIdentityExtras\\(track\\)\\.toBundle\\(\\)\\)").findAll(activity).count())
    }
}
