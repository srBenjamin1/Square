package dev.lelonio.square.data.local

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LocalMusicPersistenceTest {
    @Test fun indexPlaylistsAndExplicitVisualCacheSurviveRestart() {
        val folder=Files.createTempDirectory("local-music-store").toFile()
        try {
            val file=folder.resolve("index.json")
            val song=LocalTrack(9,"content://media/external/audio/media/9","Song",artist="Artist",album="Album",
                albumArtist="Album artist",track=3,disc=2,genre="Jazz",year="2026",fileName="song.flac",size=200,modifiedSeconds=5)
            val playlist=LocalPlaylist("id","My playlist",listOf(song.uri,"local:track:missing"))
            val visual=LocalArtistVisual(song.artistUri,"spotify:artist:id","Verified artist",heroFile="/cache/hero",logoFile="/cache/logo")
            val snapshot=LocalMusicSnapshot(listOf(song),listOf(playlist),listOf(visual),500)
            LocalMusicStore(file).write(snapshot)
            assertEquals(snapshot,LocalMusicStore(file).read())
            assertFalse(folder.resolve("index.json.tmp").exists())
        } finally { folder.deleteRecursively() }
    }
    @Test fun playlistEditsAreLocalAndRetainMissingReferences() {
        val p=LocalPlaylist("id","Before").add(listOf("local:track:1","local:track:1","spotify:track:remote","local:track:2"))
        assertEquals(listOf("local:track:1","local:track:2"),p.tracks)
        assertEquals(listOf("local:track:2","local:track:1"),p.move(0,1).tracks)
        assertEquals(p,p.move(-1,3))
        assertEquals("After",p.copy(name="After").name)
        val c=LocalMusicCatalog(LocalMusicSnapshot(tracks=listOf(LocalTrack(1,"content://one","One")),playlists=listOf(p)))
        assertEquals(1,c.missing(p))
        assertEquals(1,c.playlistTracks(p).size)
        assertEquals(2,p.tracks.size)
        assertEquals(listOf("local:track:1"),p.remove("local:track:2").tracks)
    }
    @Test fun removingPlaylistNeverRemovesOriginalAudio() {
        val folder=Files.createTempDirectory("local-playlist").toFile()
        try {
            val audio=folder.resolve("song.flac").apply { writeBytes(byteArrayOf(1,2,3)) }
            val song=LocalTrack(1,"content://one","One",filePath=audio.absolutePath)
            val store=LocalMusicStore(folder.resolve("index.json"))
            store.write(LocalMusicSnapshot(listOf(song),listOf(LocalPlaylist("id","List",listOf(song.uri)))))
            store.write(store.read().copy(playlists=emptyList()))
            assertTrue(store.read().playlists.isEmpty())
            assertArrayEquals(byteArrayOf(1,2,3),audio.readBytes())
            assertEquals(song.filePath,store.read().tracks.single().filePath)
        } finally { folder.deleteRecursively() }
    }
    @Test fun reindexWithDeletedAudioDoesNotErasePlaylistReferences() {
        val folder=Files.createTempDirectory("local-reindex-store").toFile()
        try {
            val store=LocalMusicStore(folder.resolve("index.json"))
            val p=LocalPlaylist("id","List",listOf("local:track:1"))
            store.write(LocalMusicSnapshot(listOf(LocalTrack(1,"content://one","One")),listOf(p)))
            store.write(store.read().copy(tracks=emptyList()))
            val reloaded=LocalMusicCatalog(LocalMusicStore(folder.resolve("index.json")).read())
            assertEquals(p,reloaded.playlists.single())
            assertEquals(1,reloaded.missing(p))
        } finally { folder.deleteRecursively() }
    }
}
