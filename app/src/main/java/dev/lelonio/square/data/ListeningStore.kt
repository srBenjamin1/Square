package dev.lelonio.square.data

import android.content.Context
import dev.lelonio.square.data.local.LocalListening
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * What was listened to, kept apart per source.
 *
 * One history for the signed-in Spotify account, one for YouTube Music and one for local files.
 * There used to be a single history, for Spotify alone: YouTube Music was not
 * recorded at all, and a history that belonged to one account at a time could
 * only have been shared by throwing the other's away at every switch.
 * [events] is the history of the source on screen; see [show].
 */
class ListeningStore(context: Context) {
    enum class Source { SPOTIFY, YOUTUBE_MUSIC, LOCAL }

    private val prefs = context.getSharedPreferences("square_listening", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ListeningEvent.serializer())
    private val lock = Mutex()
    private val cache = HashMap<String, List<ListeningEvent>>()
    private var source = Source.SPOTIFY

    init {
        // The single history from before, which was the Spotify account's.
        val legacy = prefs.getString("events", null)
        val owner = account
        if (legacy != null) {
            prefs.edit().apply { if (owner != null) putString(key(owner), legacy) }.remove("events").apply()
        }
    }

    /** The Spotify account whose history is kept. */
    val account get() = prefs.getString("account", null)

    private val mutable = MutableStateFlow<List<ListeningEvent>>(emptyList())
    val events = mutable.asStateFlow()
    // Independent of the catalog on screen: local playback may continue while browsing remotely.
    private val localMutable = MutableStateFlow<List<ListeningEvent>>(emptyList())
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            lock.withLock {
                preserveLocalHistory()
                localMutable.value = read(LOCAL_OWNER)
                mutable.value = visible(ownerOf(source)?.let(::read).orEmpty())
            }
        }
    }
    val localEvents = localMutable.asStateFlow()

    /** Who a source's listens are filed under; null while Spotify has no account. */
    fun ownerOf(source: Source): String? = when (source) {
        Source.SPOTIFY -> account
        Source.YOUTUBE_MUSIC -> YOUTUBE_OWNER
        Source.LOCAL -> LOCAL_OWNER
    }

    /** Puts this source's history in [events]. */
    suspend fun show(source: Source) = withContext(Dispatchers.IO) { lock.withLock {
        this@ListeningStore.source = source
        mutable.value = visible(ownerOf(source)?.let(::read).orEmpty())
    } }

    /** A different Spotify account signed in: the last one's history goes. */
    suspend fun useAccount(id: String) = withContext(Dispatchers.IO) { lock.withLock {
        val old = account
        if (old == id) return@withLock
        preserveLocalHistory()
        prefs.edit().putString("account", id).apply { if (old != null) remove(key(old)) }.apply()
        old?.let(cache::remove)
        if (source == Source.SPOTIFY) mutable.value = visible(read(id))
    } }

    suspend fun record(event: ListeningEvent, owner: String) = withContext(Dispatchers.IO) { lock.withLock {
        if (!known(owner)) return@withLock
        if (owner == LOCAL_OWNER && !event.track.uri.startsWith("local:track:")) return@withLock
        val updated = ListeningHistory.record(read(owner), event)
        save(owner, if (owner == LOCAL_OWNER) dev.lelonio.square.data.local.LocalListening.retained(updated, System.currentTimeMillis()) else updated)
    } }

    suspend fun import(plays: List<Pair<CatalogTrack, Long>>, owner: String) = withContext(Dispatchers.IO) { lock.withLock {
        if (!known(owner)) return@withLock
        save(owner, plays.fold(read(owner)) { events, (track, at) -> ListeningHistory.merge(events, track, at) })
    } }

    /** Plays read from YouTube Music's history; see [YouTubeListening.plays]. */
    suspend fun importRemote(plays: List<YouTubeListening.RemotePlay>, owner: String) = withContext(Dispatchers.IO) { lock.withLock {
        if (!known(owner)) return@withLock
        save(owner, plays.fold(read(owner)) { events, play ->
            ListeningHistory.mergeRemote(events, play.track, play.at, play.window)
        })
    } }

    /** The songs YouTube Music's history listed last time, newest first, and when. */
    fun youtubeReading(): Pair<List<String>, Long>? {
        val at = prefs.getLong("youtube-read-at", 0L).takeIf { it > 0 } ?: return null
        val songs = prefs.getString("youtube-read", null)?.split('\n')?.filter { it.isNotEmpty() } ?: return null
        return songs to at
    }

    fun keepYoutubeReading(songs: List<String>, at: Long) {
        prefs.edit().putString("youtube-read", songs.joinToString("\n")).putLong("youtube-read-at", at).apply()
    }

    /** Signing out of Spotify: its history goes, YouTube Music's stays. */
    suspend fun clear() = withContext(Dispatchers.IO) { lock.withLock {
        val old = account
        preserveLocalHistory()
        prefs.edit().remove("account").apply { if (old != null) remove(key(old)) }.apply()
        old?.let(cache::remove)
        if (source == Source.SPOTIFY) mutable.value = emptyList()
    } }

    private fun visible(events: List<ListeningEvent>) = if (source == Source.LOCAL) events else LocalListening.remoteOnly(events)

    // Copy only identified physical-file events into their proper channel. The original remote
    // history is neither removed nor rewritten, and repeated starts recover each event just once.
    private fun preserveLocalHistory() {
        val legacy = (listOfNotNull(account) + YOUTUBE_OWNER).flatMap { owner ->
            read(owner).filter { it.track.uri.startsWith("local:track:") }
        }
        save(LOCAL_OWNER, LocalListening.recover(read(LOCAL_OWNER), legacy, System.currentTimeMillis()))
    }

    private fun known(owner: String) = owner == LOCAL_OWNER || owner == YOUTUBE_OWNER || owner == account

    private fun key(owner: String) = "events:$owner"

    private fun read(owner: String): List<ListeningEvent> = cache.getOrPut(owner) {
        runCatching { json.decodeFromString(serializer, prefs.getString(key(owner), null) ?: "[]") }
            .getOrDefault(emptyList())
    }

    private fun save(owner: String, events: List<ListeningEvent>) {
        if (events == read(owner)) return
        cache[owner] = events
        prefs.edit().putString(key(owner), json.encodeToString(serializer, events)).apply()
        if (ownerOf(source) == owner) mutable.value = visible(events)
        if (owner == LOCAL_OWNER) localMutable.value = events
    }

    companion object {
        const val YOUTUBE_OWNER = "youtube-music"
        const val LOCAL_OWNER = "local-files"
    }
}
