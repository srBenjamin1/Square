package dev.lelonio.square.thor.ui

import dev.lelonio.square.data.MetadataDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Requests survive until the existing lower NavHost is ready, then are acknowledged by ID. */
internal open class MetadataNavigationQueue {
    data class Request(val id: Long, val destination: MetadataDestination)
    private val sequence = AtomicLong()
    private val mutablePending = MutableStateFlow<List<Request>>(emptyList())
    val pending = mutablePending.asStateFlow()

    fun request(destination: MetadataDestination): Request {
        val request = Request(sequence.incrementAndGet(), destination)
        mutablePending.update { it + request }
        return request
    }

    /** A cancelled receiver leaves the request queued for the next STARTED lower host. */
    suspend fun deliver(request: Request, open: suspend (MetadataDestination) -> Boolean): Boolean {
        val accepted = open(request.destination)
        consume(request.id)
        return accepted
    }

    fun consume(id: Long) { mutablePending.update { requests -> requests.filterNot { it.id == id } } }
}

internal object ThorMetadataNavigation : MetadataNavigationQueue()
