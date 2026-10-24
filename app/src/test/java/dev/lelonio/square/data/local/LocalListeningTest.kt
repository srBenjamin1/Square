package dev.lelonio.square.data.local

import dev.lelonio.square.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LocalListeningTest {
    private val day = 86_400_000L
    private val now = 2_000_000_000_000L
    private val old = LocalTrack(1, "content://one", "Old file", "First artist", "First album", durationMs = 60_000, addedSeconds = 1)
    private val fresh = LocalTrack(2, "content://two", "New file", "Second artist", "Second album", durationMs = 60_000, addedSeconds = now / 1_000)
    private val catalog = LocalMusicCatalog(LocalMusicSnapshot(listOf(old, fresh)))
    private fun event(id: String, song: LocalTrack = old, at: Long = now, context: String? = null) =
        ListeningEvent(id, song.catalog(), at, listenedMs = 30_000, qualified = true, contextUri = context)

    @Test fun dateAddedDoesNotManufactureListeningHistory() {
        val stats = LocalListening.summarize(catalog, emptyList(), now)
        assertTrue(stats.recent.isEmpty()); assertTrue(stats.top.isEmpty())
        assertEquals(fresh.uri, catalog.ordered(LocalTrackOrder.ADDED).first().uri)
    }
    @Test fun recencyUsesActualListensEvenForOldFiles() {
        val stats = LocalListening.summarize(catalog, listOf(event("a", fresh, now - day), event("b", old)), now)
        assertEquals(listOf(old.uri, fresh.uri), stats.recent.map { it.uri })
        assertEquals(old.albumUri, stats.albums.first().uri)
        assertEquals(old.artistUri, stats.artists.first().uri)
    }
    @Test fun latestListenOfAnySongDeterminesAlbumRecency() {
        val another = old.copy(id = 3, title = "Another", albumArtist = old.artist)
        val first = old.copy(albumArtist = old.artist)
        val cat = LocalMusicCatalog(LocalMusicSnapshot(listOf(first, another, fresh)))
        val stats = LocalListening.summarize(cat, listOf(event("one", first, now - 2 * day), event("two", fresh, now - day), event("three", another)), now)
        assertEquals(listOf(first.albumUri, fresh.albumUri), stats.albums.map { it.uri })
        assertEquals(first.artistUri, stats.artists.first().uri)
    }
    @Test fun duplicateIdsAndMetadataSnapshotsCountOnce() {
        val first = event("same")
        val stats = LocalListening.summarize(catalog, listOf(first, first.copy(track = first.track.copy(name = "Updated title"))), now)
        assertEquals(1, stats.counts[old.uri]); assertEquals(1, stats.recent.size)
    }
    @Test fun repeatedAudibleVisitsHaveDistinctCountsButOneRecentCard() {
        val stats = LocalListening.summarize(catalog, listOf(event("one"), event("two", at = now - day), event("three", fresh)), now)
        assertEquals(2, stats.counts[old.uri]); assertEquals(old.uri, stats.top.first().uri)
        assertEquals(2, stats.recent.size)
    }
    @Test fun unknownMissingRemoteAndUnqualifiedTracksDoNotProduceCards() {
        val remote = event("remote").copy(track = CatalogTrack("spotify:track:1", "Remote", "Artist"))
        val missing = event("missing").copy(track = old.catalog().copy(uri = "local:track:99"))
        val short = event("short").copy(qualified = false, listenedMs = 1_000)
        val future = event("future", at = now + 1)
        assertTrue(LocalListening.summarize(catalog, listOf(remote, missing, short, future), now).recent.isEmpty())
    }
    @Test fun weekMonthAndRetainedHistoryHaveDifferentRealCounts() {
        val events = listOf(event("recent"), event("month", at = now - 20 * day), event("older", at = now - 60 * day))
        assertEquals(1, LocalListening.summarize(catalog, events, now, LocalListening.Window.WEEK).counts[old.uri])
        assertEquals(2, LocalListening.summarize(catalog, events, now, LocalListening.Window.MONTH).counts[old.uri])
        assertEquals(3, LocalListening.summarize(catalog, events, now).counts[old.uri])
    }
    @Test fun timeWindowIncludesItsExactBoundary() {
        val events = listOf(event("edge", at = now - 7 * day), event("outside", at = now - 7 * day - 1))
        assertEquals(1, LocalListening.summarize(catalog, events, now, LocalListening.Window.WEEK).counts[old.uri])
    }
    @Test fun retentionIsBoundedByAgeAndEventCount() {
        val many = (0..LocalListening.MAX_EVENTS).map { event("$it", at = now - it) } + event("expired", at = now - 366 * day)
        val retained = LocalListening.retained(many, now)
        assertEquals(LocalListening.MAX_EVENTS, retained.size)
        assertEquals("0", retained.first().id)
        assertFalse(retained.any { it.id == "expired" })
    }
    @Test fun playlistsUseQualifiedContextAndKeepUnusedOnes() {
        val unused = LocalPlaylist("unused", "Unused")
        val used = LocalPlaylist("used", "Used")
        val cat = LocalMusicCatalog(LocalMusicSnapshot(listOf(old), playlists = listOf(unused, used)))
        val stats = LocalListening.summarize(cat, listOf(event("one", context = used.uri)), now)
        assertEquals(listOf(used.uri, unused.uri), stats.playlists.map { it.uri })
        assertEquals(listOf(unused.uri, used.uri), LocalListening.summarize(cat, emptyList(), now).playlists.map { it.uri })
    }
    @Test fun unqualifiedAndRemoteContextNeverChangePlaylistUseOrder() {
        val a = LocalPlaylist("a", "A"); val b = LocalPlaylist("b", "B")
        val cat = LocalMusicCatalog(LocalMusicSnapshot(listOf(old), playlists = listOf(a,b)))
        val events = listOf(event("one", context = b.uri).copy(qualified = false), event("two", context = "spotify:playlist:b"))
        assertEquals(listOf(a.uri, b.uri), LocalListening.summarize(cat, events, now).playlists.map { it.uri })
    }
    @Test fun existingSamplerRequiresAudibleTimeNotATapOrRestoredMetadata() {
        val sampler = ListeningSampler()
        repeat(5) { assertNull(sampler.sample(old.catalog(), false, now + it * 1_000, it * 1_000L)) }
        assertNull(sampler.sample(old.catalog(), true, now + 5_000, 5_000))
        assertFalse(sampler.sample(old.catalog(), true, now + 6_000, 6_000)!!.qualified)
    }
    @Test fun pauseAndMetadataChangesKeepOneEventAndOneCount() {
        val sampler = ListeningSampler()
        sampler.sample(old.catalog(), true, now, 0)
        var stored = emptyList<ListeningEvent>()
        for (i in 1..35) {
            val track = if (i > 20) old.catalog().copy(name = "Updated metadata") else old.catalog()
            val e = sampler.sample(track, true, now + i * 1_000, i * 1_000L)!!
            stored = ListeningHistory.record(stored, e)
        }
        val id = stored.single().id
        sampler.sample(old.catalog(), false, now + 36_000, 36_000)
        sampler.sample(old.catalog(), false, now + 90_000, 90_000)
        val resumed = sampler.sample(old.catalog(), true, now + 91_000, 91_000)!!
        assertEquals(id, resumed.id)
        assertEquals(1, LocalListening.summarize(catalog, ListeningHistory.record(stored, resumed), now + 92_000).counts[old.uri])
    }
    @Test fun repeatTransitionStartsAnotherAudibleVisit() {
        val sampler = ListeningSampler()
        sampler.sample(old.catalog(), true, now, 0)
        for (i in 1..30) sampler.sample(old.catalog(), true, now + i * 1_000, i * 1_000L)
        val first = sampler.sample(null, false, now + 31_000, 31_000)!!
        sampler.sample(old.catalog(), true, now + 32_000, 32_000)
        for (i in 33..62) sampler.sample(old.catalog(), true, now + i * 1_000, i * 1_000L)
        val second = sampler.sample(null, false, now + 63_000, 63_000)!!
        assertNotEquals(first.id, second.id)
        assertEquals(2, LocalListening.summarize(catalog, listOf(first, second), now + 64_000).counts[old.uri])
    }
    @Test fun listeningContextAndQualifiedEventsSurviveTheExistingJsonSchema() {
        val playlist = LocalPlaylist("saved", "Saved")
        val sampler = ListeningSampler()
        sampler.sample(old.catalog(), true, now, 0, playlist.uri)
        for (i in 1..30) sampler.sample(old.catalog(), true, now + i * 1_000, i * 1_000L, playlist.uri)
        val measured = sampler.sample(null, false, now + 31_000, 31_000)!!
        val json = Json { ignoreUnknownKeys = true }
        val restored = json.decodeFromString<ListeningEvent>(json.encodeToString(measured))
        assertEquals(playlist.uri, restored.contextUri)
        assertTrue(restored.qualified)
        assertEquals(measured, restored)
        assertNull(json.decodeFromString<ListeningEvent>("""{"id":"legacy","track":{"uri":"local:track:1","name":"Old","artist":"Artist"},"startedAt":1}""").contextUri)
    }
    @Test fun unknownDurationUsesExistingThirtySecondThreshold() {
        val sampler = ListeningSampler(); val track = old.catalog().copy(durationMs = 0)
        sampler.sample(track, true, now, 0)
        for (i in 1..29) assertFalse(sampler.sample(track, true, now + i * 1_000, i * 1_000L)!!.qualified)
        assertTrue(sampler.sample(track, true, now + 30_000, 30_000)!!.qualified)
    }

    @Test fun incorporationGroupsUseFilesAddedNotListensOrReleaseYears() {
        val oldWithNewYear = old.copy(year = "2099", addedSeconds = 10)
        val newWithOldYear = fresh.copy(year = "1900", addedSeconds = 20)
        val unknown = old.copy(id = 9, artist = "Unknown date", album = "Unknown album", addedSeconds = 0)
        val cat = LocalMusicCatalog(LocalMusicSnapshot(listOf(oldWithNewYear, newWithOldYear, unknown)))
        val additions = LocalAdditions.from(cat)
        assertEquals(listOf(fresh.uri, old.uri), additions.songs.map { it.uri })
        assertEquals(listOf(fresh.albumUri, old.albumUri), additions.albums.map { it.uri })
        assertEquals(listOf(fresh.artistUri, old.artistUri), additions.artists.map { it.uri })
        val heard = LocalListening.summarize(cat, listOf(event("heard-old", oldWithNewYear)), now)
        assertEquals(old.uri, heard.recent.single().uri)
        assertEquals(fresh.uri, additions.songs.first().uri)
    }
    @Test fun existingHistoryBeyondRetentionDoesNotAppearAsAllTimeData() {
        assertTrue(LocalListening.summarize(catalog, listOf(event("expired", at = now - 366 * day)), now).top.isEmpty())
    }

    @Test fun recencyFollowsAudibleResumeAndDoesNotAdvanceWhilePaused() {
        val sampler = ListeningSampler()
        sampler.sample(old.catalog(), true, now - 60_000, 0)
        for (i in 1..30) sampler.sample(old.catalog(), true, now - 60_000 + i * 1_000, i * 1_000L)
        val paused = sampler.sample(old.catalog(), false, now - 20_000, 40_000)!!
        val stillPaused = sampler.sample(old.catalog(), false, now - 10_000, 50_000)!!
        assertEquals(paused.lastAudibleAt, stillPaused.lastAudibleAt)
        sampler.sample(old.catalog(), true, now - 1_000, 59_000)
        val resumed = sampler.sample(old.catalog(), true, now, 60_000)!!
        val other = event("other", fresh, now - 5_000)
        assertEquals(old.uri, LocalListening.summarize(catalog, listOf(resumed, other), now).recent.first().uri)
        assertEquals(paused.id, resumed.id)
    }

    @Test fun legacyPhysicalListensAreRecoveredOnceWithoutChangingTheirIdsOrOriginals() {
        val older = event("legacy", at = now - day)
        val archive = listOf(older)
        val recovered = LocalListening.recover(emptyList(), archive, now)
        assertEquals(archive, recovered)
        assertEquals(recovered, LocalListening.recover(recovered, archive, now))
        assertEquals(listOf(older), archive)
    }
    @Test fun recoveryKeepsMostCompleteSnapshotOfAnEvent() {
        val early = event("same").copy(listenedMs = 1_000, qualified = false)
        val later = early.copy(listenedMs = 40_000, qualified = true)
        assertEquals(later, LocalListening.recover(listOf(early), listOf(later), now).single())
    }
    @Test fun remoteHistoryViewsDoNotMixInLegacyPhysicalFileStatistics() {
        val remote = event("remote").copy(track = old.catalog().copy(uri = "spotify:track:remote"))
        val youtube = remote.copy(id = "youtube", track = remote.track.copy(uri = "ytmusic:track:remote"))
        val legacy = event("legacy")
        val archive = listOf(remote, legacy, youtube)
        assertEquals(listOf(remote, youtube), LocalListening.remoteOnly(archive))
        assertEquals(listOf(legacy), LocalListening.recover(emptyList(), archive, now))
        assertEquals(3, archive.size)
    }
    @Test fun localRecoveryRetentionNeverMutatesTheArchivedHistory() {
        val archived = listOf(event("old", at = now - 366 * day), event("kept"))
        assertEquals(listOf("kept"), LocalListening.recover(emptyList(), archived, now).map { it.id })
        assertEquals(listOf("old", "kept"), archived.map { it.id })
    }
}
