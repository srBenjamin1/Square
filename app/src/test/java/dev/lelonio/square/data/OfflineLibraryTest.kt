package dev.lelonio.square.data

import org.junit.Assert.*
import org.junit.Test

class OfflineLibraryTest {
    @Test fun downloadedYouTubePlaylistsKeepLabelsAndArtworkAlongsideDeviceShelves() {
        val uri = "youtube:playlist:PL1"
        val downloaded = OfflineLibrary.playlists(listOf(uri, "unlabelled")) {
            if (it == uri) DownloadStore.OwnerLabel("Kept offline", "cover") else null
        }
        val shelves = listOf(CatalogPlaylist("local:files", "Local files"), CatalogPlaylist("downloads", "Downloaded songs"))
        val result = OfflineLibrary.withDeviceShelves(downloaded + shelves + downloaded, shelves)
        assertEquals(shelves + CatalogPlaylist(uri, "Kept offline", artworkUrl = "cover"), result)
    }
    @Test fun deviceShelvesRemainWithoutDownloadedPlaylists() {
        val shelves = listOf(CatalogPlaylist("local:files", "Local files"))
        assertEquals(shelves, OfflineLibrary.withDeviceShelves(emptyList(), shelves))
    }
}
