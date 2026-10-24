package dev.lelonio.square.data.local

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LocalArtistArtworkTest {
    @Test fun cachedOriginalPhotoOrLogoTakesPriorityEvenWithoutInternet() {
        val file=Files.createTempFile("local-artist-art",".png").toFile()
        try {
            file.writeBytes(byteArrayOf(1,2,3))
            assertEquals(file.absolutePath,localArtistPicture(file.absolutePath,"https://catalog/portrait",true))
            assertEquals(file.absolutePath,localArtistPicture(file.absolutePath,"https://catalog/logo",false))
        } finally {file.delete()}
    }
    @Test fun missingCacheNeverRequestsRemoteArtInOfflineMode() {
        assertNull(localArtistPicture("/missing/photo","https://catalog/portrait",true))
        assertEquals("https://catalog/portrait",localArtistPicture("/missing/photo","https://catalog/portrait",false))
    }
    @Test fun emptyCachedLogoFallsBackRatherThanHidingTheArtistName() {
        val file=Files.createTempFile("empty-local-logo",".png").toFile()
        try { assertNull(localArtistPicture(file.absolutePath,"https://catalog/logo",true)) }
        finally {file.delete()}
    }
    @Test fun albumArtistHasTheirOwnLocalPageWithoutReplacingTrackPerformer() {
        val one=LocalTrack(1,"content://one","One",artist="Guest one",album="Compilation",albumArtist="Album artist")
        val two=LocalTrack(2,"content://two","Two",artist="Guest two",album="Compilation",albumArtist="Album artist",year="2026")
        val collection=LocalMusicCatalog(LocalMusicSnapshot(listOf(one,two)))
        val artist=collection.artists.single { it.name=="Album artist" }
        assertEquals(2,artist.tracks.size)
        assertEquals(1,artist.albums.size)
        assertEquals("2026",artist.albums.single().year)
        assertEquals("Guest one",one.catalog().artist)
        assertEquals(listOf("Guest one"),one.catalog().artists.map { it.name })
    }

    @Test fun unassociatedArtistsNeverBorrowAlbumCoversInCardsOrSearch() {
        val song = LocalTrack(1, "content://one", "Song", "Artist", "Album")
        val catalog = LocalMusicCatalog(LocalMusicSnapshot(listOf(song)))
        assertNotNull(catalog.albums.single().artwork)
        assertNull(catalog.artistArtwork(song.artistUri, offline = false))
        assertNull(catalog.artistArtwork(song.artistUri, offline = true))
        assertNull(catalog.search("Artist").artists.single().artworkUrl)
    }
    @Test fun associatedProfilePhotoIsDistinctFromLocalAlbumArtwork() {
        val song = LocalTrack(1, "content://one", "Song", "Artist", "Album")
        val visual = LocalArtistVisual(song.artistUri, "spotify:artist:verified", "Artist", heroUrl = "https://catalog/portrait", logoUrl = "https://catalog/logo")
        val catalog = LocalMusicCatalog(LocalMusicSnapshot(listOf(song), visuals = listOf(visual)))
        assertEquals(visual.heroUrl, catalog.artistArtwork(song.artistUri, offline = false))
        assertNotEquals(catalog.albums.single().artwork, catalog.artistArtwork(song.artistUri, offline = false))
        assertNull(catalog.artistArtwork(song.artistUri, offline = true))
        assertEquals(song.artwork, song.catalog().artworkUrl)
    }
}
