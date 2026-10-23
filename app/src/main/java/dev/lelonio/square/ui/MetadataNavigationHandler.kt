package dev.lelonio.square.ui

import dev.lelonio.square.backend.BackendId
import dev.lelonio.square.data.MetadataDestination
import kotlinx.coroutines.flow.first

/** The same workflow is used by the lower info panel and by requests from the upper screen. */
internal class MetadataNavigationHandler(
    private val activeService: () -> BackendId,
    private val closeTools: suspend () -> Unit,
    private val openPage: (MetadataDestination) -> Unit,
    private val trace: (String, String) -> Unit = { _, _ -> },
) {
    suspend fun open(destination: MetadataDestination): Boolean {
        val matches = destination.service == activeService()
        trace("METADATA_RECEIVED", "type=${destination.type} service=${destination.service} matches=$matches")
        if (!matches) { trace("METADATA_ERROR", "service_mismatch"); return false }
        trace("tools.close", "begin")
        closeTools()
        trace("tools.close", "complete")
        // A backend change during an animation must not send the URI to another catalogue.
        if (destination.service != activeService()) { trace("METADATA_ERROR", "service_changed"); return false }
        trace("METADATA_NAVIGATE", "type=${destination.type}")
        openPage(destination)
        trace("METADATA_COMPLETE", "type=${destination.type} complete")
        return true
    }
}

/** Wait for the state-owned tools animation; never compete for its Animatable mutation lock. */
internal suspend fun closeMetadataTools(close: () -> Unit, closed: kotlinx.coroutines.flow.Flow<Boolean>) {
    close()
    closed.first { it }
}
