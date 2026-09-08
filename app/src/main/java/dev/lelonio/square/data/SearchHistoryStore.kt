package dev.lelonio.square.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a search led to, kept locally.
 *
 * The thing found rather than the words typed to find it, which is the part
 * worth keeping: half-typed terms and misspellings say nothing a week later,
 * and what they led to is what anyone actually wants back. It also means the
 * list can be tapped — every row goes back where it came from.
 *
 * Everything a search can turn up, not only songs: an artist looked up on
 * Tuesday is exactly as worth having back as a song played on Tuesday, and the
 * list used to drop three quarters of what people searched for. A row that was
 * a song carries the song, so it can still be played from here; a row that was
 * an artist, a record or a list carries what is needed to open its page.
 *
 * Local, like [RecentStore], and for the same reason: nothing is sent anywhere
 * and no quota is spent to keep it.
 */
class SearchHistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SearchHistoryEntry.serializer())
    private val legacy = ListSerializer(CatalogTrack.serializer())

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<SearchHistoryEntry>> = _entries.asStateFlow()

    /** The songs among them, for the places that can only use songs. */
    val tracks: StateFlow<List<CatalogTrack>> = MutableStateFlow(
        _entries.value.mapNotNull { it.track },
    ).also { flow ->
        // Kept in step by the same writes: this store is small and written
        // rarely, so a derived flow costs less than every caller filtering.
        _tracksMirror = flow
    }.asStateFlow()

    /** Records a play, moving a repeat to the front rather than adding it twice. */
    suspend fun record(track: CatalogTrack) = withContext(Dispatchers.IO) {
        record(
            SearchHistoryEntry(
                uri = track.uri,
                title = track.name,
                subtitle = track.artist,
                artworkUrl = track.artworkUrl,
                track = track,
            ),
        )
    }

    /** And the same for whatever else a search turns up. */
    suspend fun record(entry: SearchHistoryEntry) = withContext(Dispatchers.IO) {
        save((listOf(entry) + _entries.value.filterNot { it.uri == entry.uri }).take(LIMIT))
    }

    /** Takes one row out, for a search somebody would rather not keep. */
    suspend fun remove(uri: String) = withContext(Dispatchers.IO) {
        save(_entries.value.filterNot { it.uri == uri })
    }

    suspend fun clear() = withContext(Dispatchers.IO) { save(emptyList()) }

    private fun save(entries: List<SearchHistoryEntry>) {
        _entries.value = entries
        _tracksMirror?.value = entries.mapNotNull { it.track }
        prefs.edit().putString(KEY_ENTRIES, json.encodeToString(serializer, entries)).apply()
    }

    private fun load(): List<SearchHistoryEntry> {
        prefs.getString(KEY_ENTRIES, null)?.let { raw ->
            return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
        }
        // What earlier versions kept, which was songs and nothing else.
        val raw = prefs.getString(KEY_TRACKS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(legacy, raw) }
            .getOrDefault(emptyList())
            .map {
                SearchHistoryEntry(
                    uri = it.uri,
                    title = it.name,
                    subtitle = it.artist,
                    artworkUrl = it.artworkUrl,
                    track = it,
                )
            }
    }

    private var _tracksMirror: MutableStateFlow<List<CatalogTrack>>? = null

    private companion object {
        const val FILE_NAME = "square_search_history"
        const val KEY_TRACKS = "tracks"
        const val KEY_ENTRIES = "entries"

        /** A screenful and a half; older than that and it is a different day. */
        const val LIMIT = 20
    }
}

/**
 * One thing a search led to.
 *
 * [track] is filled only for a song, and it is what lets the row be played
 * from the list rather than merely opened; everything else carries the little
 * a row needs to be drawn and a page to be opened from it.
 */
@Serializable
data class SearchHistoryEntry(
    val uri: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val track: CatalogTrack? = null,
) {
    /** Whether opening this means opening a page rather than playing a song. */
    val isContext: Boolean get() = track == null
}
