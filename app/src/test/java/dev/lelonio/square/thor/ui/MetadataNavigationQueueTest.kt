package dev.lelonio.square.thor.ui

import dev.lelonio.square.backend.BackendId
import dev.lelonio.square.data.MetadataDestination
import org.junit.Assert.*
import org.junit.Test

class MetadataNavigationQueueTest {
    private fun destination(type: MetadataDestination.Type, id: String = "abc") = MetadataDestination.create(
        type, "spotify:${type.name.lowercase()}:$id", "Name", BackendId.SPOTIFY)!!

    @Test fun requestsWaitForLowerHostWithTheirTypeIdentityAndService() {
        val queue = MetadataNavigationQueue()
        val artist = queue.request(destination(MetadataDestination.Type.ARTIST))
        val album = queue.request(destination(MetadataDestination.Type.ALBUM))
        assertEquals(listOf(artist, album), queue.pending.value)
        assertEquals(BackendId.SPOTIFY, artist.destination.service)
        assertEquals(MetadataDestination.Type.ARTIST, artist.destination.type)
        assertEquals(MetadataDestination.Type.ALBUM, album.destination.type)
    }
    @Test fun acknowledgementCannotClearNewRequestsAndCannotReplayOldOnes() {
        val queue = MetadataNavigationQueue()
        val first = queue.request(destination(MetadataDestination.Type.ARTIST))
        val second = queue.request(destination(MetadataDestination.Type.ALBUM))
        queue.consume(first.id)
        queue.consume(first.id)
        assertEquals(listOf(second), queue.pending.value)
        queue.consume(second.id)
        assertTrue(queue.pending.value.isEmpty())
    }
    @Test fun repeatedDestinationAfterConsumptionIsANewNavigationRequest() {
        val queue = MetadataNavigationQueue()
        val target = destination(MetadataDestination.Type.ALBUM)
        val first = queue.request(target)
        queue.consume(first.id)
        val second = queue.request(target)
        assertNotEquals(first.id, second.id)
        assertEquals(listOf(second), queue.pending.value)
    }
    @Test fun rapidRequestsAreRetainedInOrderInsteadOfOverwritingEachOther() {
        val queue = MetadataNavigationQueue()
        repeat(30) { queue.request(destination(MetadataDestination.Type.ARTIST, "id$it")) }
        val requests = queue.pending.value
        assertEquals(30, requests.size)
        assertEquals(30, requests.map { it.id }.toSet().size)
        requests.forEach { queue.consume(it.id) }
        assertTrue(queue.pending.value.isEmpty())
    }
}
