package dev.lelonio.square.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import dev.lelonio.square.backend.BackendId
import dev.lelonio.square.data.MetadataDestination
import dev.lelonio.square.thor.ui.MetadataNavigationQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class MetadataDeliveryIntegrationTest {
    private fun target(type: MetadataDestination.Type) = MetadataDestination.create(type,
        "spotify:${type.name.lowercase()}:identity", "Name", BackendId.SPOTIFY)!!

    @Test fun actualComposeToolsAnimationFinishesBeforeArtistAndAlbumDelivery() = runBlocking {
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(1)
                val result = onFrame(System.nanoTime())
                Snapshot.sendApplyNotifications()
                return result
            }
        }
        withContext(clock) {
            for (type in MetadataDestination.Type.entries) {
                val queue = MetadataNavigationQueue()
                val request = queue.request(target(type))
                val tools = Animatable(1f)
                val open = MutableStateFlow(true)
                val history = mutableListOf("library")
                val animator = launch(start = CoroutineStart.UNDISPATCHED) {
                    open.collect { visible ->
                        tools.animateTo(if (visible) 1f else 0f, tween(16))
                    }
                }
                val handler = MetadataNavigationHandler({ BackendId.SPOTIFY }, {
                    closeMetadataTools({ open.value = false }, snapshotFlow { !open.value && tools.value <= 0.001f })
                }, {
                    assertFalse(open.value)
                    assertTrue(tools.value <= 0.001f)
                    history += it.uri
                })
                withTimeout(3_000) { assertTrue(queue.deliver(request, handler::open)) }
                assertTrue(queue.pending.value.isEmpty())
                assertEquals(2, history.size)
                history.removeAt(history.lastIndex)
                assertEquals("library", history.single())
                animator.cancelAndJoin()
            }
        }
    }
    @Test fun lowerHostCancellationKeepsRequestForNextReceiver() = runBlocking {
        val queue = MetadataNavigationQueue()
        val request = queue.request(target(MetadataDestination.Type.ARTIST))
        val closed = MutableStateFlow(false)
        val closing = CompletableDeferred<Unit>()
        var navigated = 0
        val handler = MetadataNavigationHandler({ BackendId.SPOTIFY }, {
            closeMetadataTools({ closing.complete(Unit) }, closed)
        }, { navigated++ })
        val stoppedHost = launch { queue.deliver(request, handler::open) }
        closing.await()
        stoppedHost.cancelAndJoin()
        assertEquals(request, queue.pending.value.single())
        assertEquals(0, navigated)
        closed.value = true
        assertTrue(queue.deliver(queue.pending.value.single(), handler::open))
        assertEquals(1, navigated)
        assertTrue(queue.pending.value.isEmpty())
    }
}
