package dev.lelonio.square.backend.spotify

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class SpotifyVideoModeTest {
    @Before fun setup() { SpotifyVideoMode.reset() }
    @After fun teardown() { SpotifyVideoMode.reset() }

    @Test fun watchPassesOriginalPositionAndWaitsForServiceBeforeEnteringVideo() = runBlocking {
        val request = async(start = CoroutineStart.UNDISPATCHED) { SpotifyVideoMode.requests.first() }
        SpotifyVideoMode.watch("file-id", 12345)
        assertEquals(SpotifyVideoMode.Request.Watch("file-id", 12345), request.await())
        assertTrue(SpotifyVideoMode.loading.value)
        assertFalse(SpotifyVideoMode.enabled.value)
        SpotifyVideoMode.setEnabled(true)
        SpotifyVideoMode.completeRequest(true)
        assertTrue(SpotifyVideoMode.enabled.value)
        assertFalse(SpotifyVideoMode.loading.value)
        assertFalse(SpotifyVideoMode.failed.value)
    }
    @Test fun listenPassesCurrentVideoPositionBackToExistingService() = runBlocking {
        SpotifyVideoMode.setEnabled(true)
        val request = async(start = CoroutineStart.UNDISPATCHED) { SpotifyVideoMode.requests.first() }
        SpotifyVideoMode.listen(67890)
        assertEquals(SpotifyVideoMode.Request.Listen(67890), request.await())
        assertTrue(SpotifyVideoMode.enabled.value)
        SpotifyVideoMode.setEnabled(false)
        SpotifyVideoMode.completeRequest(true)
        assertFalse(SpotifyVideoMode.enabled.value)
    }
    @Test fun failedRequestClearsLoadingWithoutPretendingVideoIsActive() {
        SpotifyVideoMode.watch("file-id", 0)
        SpotifyVideoMode.completeRequest(false)
        assertFalse(SpotifyVideoMode.loading.value)
        assertTrue(SpotifyVideoMode.failed.value)
        assertFalse(SpotifyVideoMode.enabled.value)
        SpotifyVideoMode.reset()
        assertFalse(SpotifyVideoMode.failed.value)
    }
}
