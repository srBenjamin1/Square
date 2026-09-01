package dev.lelonio.square.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dev.lelonio.square.data.DownloadSettingsStore
import dev.lelonio.square.data.DownloadStore
import dev.lelonio.square.nativecore.NativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Works through what the downloads store says is owed.
 *
 * There is no list of jobs here. The queue asks [DownloadStore.pending] what is
 * still wanted and has no file, downloads some of it, and asks again — so
 * adding a playlist, removing one, or killing the app mid-way all need no
 * special handling: the next question gets the right answer. [wake] exists only
 * to say "the answer may have changed", never to hand over work.
 *
 * Two reasons to stop are kept apart, because they mean opposite things to the
 * listener:
 *
 * - **The track failed.** Recorded against the track, retried with a growing
 *   wait, and eventually given up on. One unavailable song does not stop a
 *   playlist.
 * - **We cannot download at all** — no connection, mobile data while the
 *   listener asked for Wi-Fi only, or no engine yet. Nothing is recorded
 *   against any track; the queue waits and carries on where it left off. This
 *   is the distinction that stops a tunnel from burning five attempts on every
 *   remaining track of a playlist.
 */
class DownloadQueue(
    context: Context,
    private val store: DownloadStore,
    private val settings: DownloadSettingsStore,
) {

    data class Status(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        /** Why nothing is happening, when nothing is happening. */
        val waiting: Waiting? = null,
    )

    enum class Waiting { NETWORK, WIFI, ENGINE }

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _link = MutableStateFlow(link())
    private var pump: Job? = null
    private var watching = false

    /** Set when the last attempt was turned away by the key limiter. */
    private var throttled = false

    /**
     * How long to wait between tracks, learnt rather than chosen.
     *
     * Spotify refuses audio keys to a session that asks for them too quickly,
     * and a refusal is expensive: the engine holds off three seconds, then six,
     * then twelve, so a queue that keeps tripping the limiter spends half a
     * minute waiting for every track it fetches in ten seconds. Measured on a
     * fixed 350 ms gap: one song every 37 seconds, thirty of them idle.
     *
     * The engine reports how long it waited for each key, so this backs off
     * when that number says the limiter was hit and creeps down again after a
     * run of clean fetches. Slower on purpose than the fixed value it replaces
     * — but a two-second pause that never trips the limiter finishes a playlist
     * several times faster than a third of a second that always does.
     */
    private var gap = TRACK_GAP_MS

    /** Clean fetches in a row, which is what earns the gap back down. */
    private var clean = 0

    private fun noteKeyWait(waitMs: Long) {
        if (waitMs >= KEY_WAIT_SLOW_MS) {
            clean = 0
            gap = (gap * 2).coerceAtMost(MAX_GAP_MS)
        } else if (++clean >= CLEAN_RUN) {
            clean = 0
            gap = (gap / 2).coerceAtLeast(TRACK_GAP_MS)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _link.value = link()
        }

        override fun onLost(network: Network) {
            _link.value = link()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) {
            _link.value = link()
        }
    }

    /**
     * Reconsiders what there is to do.
     *
     * Cheap and idempotent: a pump already running is left alone, because it
     * will ask the store again the moment it finishes what it is on.
     */
    fun wake() {
        if (pump?.isActive == true) return
        // Published before the coroutine is scheduled, not inside it. The
        // foreground service watches this flow and stops itself the moment it
        // reads "not running" — which, with the pump still queued on a
        // dispatcher, is the very first thing it read.
        _status.value = Status(running = true)
        pump = scope.launch { drain() }
    }

    fun stop() {
        pump?.cancel()
        pump = null
        releaseWatch()
        _status.value = Status()
    }

    // ----------------------------------------------------------------- the pump

    private suspend fun drain() = coroutineScope {
        holdWatch()
        // Progress ticks come off the engine's event channel; see DownloadEvents.
        val ticks = launch {
            DownloadEvents.progress.collect { store.onProgress(it.uri, it.fraction) }
        }
        try {
            while (isActive) {
                val blocked = allowed()
                if (blocked != null) {
                    publish(waiting = blocked)
                    awaitAllowed()
                    continue
                }

                val pending = store.pending()
                // Covers before everything else, including before the rest of
                // the extras: they are the one thing whose absence is visible
                // the moment it happens.
                if (pending.isEmpty() && backfillCovers()) continue
                if (pending.isEmpty() && backfillExtras()) continue
                if (pending.isEmpty()) {
                    // Nothing to do now, but something may be waiting out a
                    // failure. Sleeping until then rather than spinning is the
                    // difference between a queue that idles and one that keeps
                    // the phone awake for a track it cannot have.
                    val retryAt = store.retryAt() ?: break
                    val wait = retryAt - System.currentTimeMillis()
                    if (wait <= 0) continue
                    publish(waiting = null)
                    delay(wait.coerceAtMost(MAX_SLEEP_MS))
                    continue
                }

                publish(waiting = null)
                val batch = pending.take(PARALLEL)
                val outcomes = batch
                    .map { uri -> async { attempt(uri) } }
                    .awaitAll()

                // Nothing was wrong with these tracks — the engine was not
                // there to fetch them, or Spotify asked us to slow down.
                // Without this the pump would find them pending again
                // immediately and spin at full speed against whatever said no.
                if (outcomes.all { it == Outcome.NOT_NOW }) {
                    publish(waiting = Waiting.ENGINE)
                    delay(if (throttled) THROTTLED_WAIT_MS else ENGINE_POLL_MS)
                    throttled = false
                } else {
                    // A breath between songs, as long as this queue has learnt
                    // it needs to be; see [gap].
                    delay(gap)
                }
            }
        } finally {
            ticks.cancel()
            releaseWatch()
            _status.value = Status()
        }
    }

    /** Tracks whose extras were attempted this run, successfully or not. */
    private val extrasTried = mutableSetOf<String>()

    private val coversTried = mutableSetOf<String>()

    /**
     * Catches up the album covers of songs that were downloaded without them.
     *
     * Its own pass, ahead of the rest. A cover is a few tens of kilobytes and a
     * Canvas is a few megabytes of video, so a single loop that fetched both
     * per track delivered covers at about one a minute — while the thing the
     * listener actually sees missing is the blank tile in the notification.
     *
     * @return true when it did something, so the caller comes back for more.
     */
    private suspend fun backfillCovers(): Boolean {
        val missing = store.files.value.keys.asSequence()
            .filterNot(coversTried::contains)
            .filter { needsExtras(it) }
            .take(COVER_BATCH)
            .toList()
        if (missing.isEmpty()) return false

        for (uri in missing) {
            coversTried += uri
            val cover = runCatching { NativeBridge.downloadState(uri) }
                .getOrNull()
                ?.let { runCatching { JSONObject(it).optString("coverUrl") }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?: continue
            DownloadExtras.keep(cover, "art")
            // No audio key is asked for here, so the pace that keeps the key
            // limiter happy does not apply: this is an ordinary CDN image.
            delay(COVER_GAP_MS)
        }
        return true
    }

    /**
     * Fetches the words, cover and Canvas of songs that were downloaded without
     * them.
     *
     * Extras are normally taken as each track lands, which covers everything
     * downloaded from now on and nothing downloaded before — including every
     * track fetched by a build of this app that had no extras yet. Offline that
     * shows up as the one thing a listener notices immediately: a song playing
     * fine with a blank cover in the notification.
     *
     * Runs only once the queue has nothing left to fetch, so the music always
     * comes first, and a few at a time so a large library does not turn into a
     * burst of requests. A track is tried once per run whatever the outcome:
     * some songs simply have no Canvas and no lyrics, and asking about them
     * again on every turn of the loop would be a loop.
     *
     * @return true when it did something, so the caller comes back for more.
     */
    private suspend fun backfillExtras(): Boolean {
        val missing = store.files.value.keys.asSequence()
            .filterNot(extrasTried::contains)
            .filter { needsExtras(it) }
            .take(EXTRAS_BATCH)
            .toList()
        if (missing.isEmpty()) return false

        for (uri in missing) {
            extrasTried += uri
            val sidecar = runCatching { NativeBridge.downloadState(uri) }.getOrNull() ?: continue
            keepExtras(uri, sidecar)
            delay(TRACK_GAP_MS)
        }
        return true
    }

    /** Whether the cover of a downloaded track is missing from the phone. */
    private fun needsExtras(trackUri: String): Boolean {
        val cover = store.trackOf(trackUri)?.artworkUrl ?: return false
        return DownloadExtras.fileOf(cover, "art") == null
    }

    private enum class Outcome { DONE, FAILED, NOT_NOW }

    private suspend fun attempt(trackUri: String): Outcome {
        val kbps = settings.quality.value.kbps
        val outcome = runCatching {
            withContext(Dispatchers.IO) { NativeBridge.downloadTrack(trackUri, kbps) }
        }

        outcome.onSuccess { sidecar ->
            // How long the engine waited for the audio key, which is the one
            // measurement that says whether this queue is going too fast; see
            // [gap].
            noteKeyWait(runCatching { JSONObject(sidecar).optLong("keyWaitMs", 0L) }.getOrDefault(0L))
            store.onCompleted(trackUri, recordOf(sidecar, kbps))
            keepExtras(trackUri, sidecar)
            return Outcome.DONE
        }

        val reason = outcome.exceptionOrNull()?.message.orEmpty()
        // Not the track's fault, so nothing is held against it: the pump will
        // find the same track pending on its next turn, once whatever went
        // missing is back.
        if (reason.isTransport()) {
            if (reason.isThrottled()) throttled = true
            store.clearProgress(trackUri)
            return Outcome.NOT_NOW
        }
        store.onFailed(trackUri, reason.ifEmpty { "download failed" })
        return Outcome.FAILED
    }

    /**
     * The words, the Canvas, the cover and the artist behind the song.
     *
     * Everything here fails quietly. A track with no lyrics is the ordinary
     * case, a Canvas that will not fetch is a still cover, and neither is worth
     * failing a download that has already succeeded — the music is on the
     * phone, which is what was asked for.
     *
     * Calling the catalogue is the whole of the caching: `Catalog` writes every
     * answer down on the way past for a track that is downloaded, which this
     * one now is. Only the two binaries have to be fetched by name.
     */
    private suspend fun keepExtras(trackUri: String, sidecar: String) {
        runCatching {
            // The cover first, and deliberately. It is a few tens of kilobytes
            // and it is the one extra that is missed the moment it is absent —
            // a song playing with a blank tile in the notification. The Canvas
            // below is megabytes of video; behind it, covers arrived at about
            // one a minute.
            //
            // The size the engine recorded is the large one, which is what the
            // player screen wants. The row thumbnail finds it too: covers are
            // keyed on the picture rather than the size. See DownloadExtras.
            runCatching { JSONObject(sidecar).optString("coverUrl") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { DownloadExtras.keep(it, "art") }

            // Asking is the whole of the caching: the catalogue writes every
            // answer down on the way past, so a track that is downloaded has
            // its words on the phone too.
            dev.lelonio.square.data.Catalog.lyrics(trackUri)

            dev.lelonio.square.data.Catalog.canvas(trackUri)?.let { clip ->
                DownloadExtras.keep(clip.url, if (clip.isVideo) "video" else "art")
            }
        }.onFailure {
            android.util.Log.i("SquareDownloads", "extras for $trackUri: $it")
        }
    }

    /**
     * Whether a failure was about the app's own footing rather than the track.
     *
     * Matched on the engine's own words, which are stable strings on the Rust
     * side for exactly this reason.
     */
    private fun String.isTransport(): Boolean =
        contains("engine not started") ||
            contains("engine is reconnecting") ||
            contains("cancelled") ||
            isThrottled()

    /**
     * Whether Spotify is refusing to hand out keys.
     *
     * Says nothing about the track — the same song downloads fine a minute
     * later — so it must never count against it. The string is the engine's
     * own; see `KEY_THROTTLED` in downloads.rs.
     */
    private fun String.isThrottled(): Boolean = contains("audio keys are being refused")

    private fun recordOf(sidecar: String, requestedKbps: Int): DownloadStore.FileRecord {
        val json = runCatching { JSONObject(sidecar) }.getOrNull()
        val format = json?.optString("format").orEmpty()
        return DownloadStore.FileRecord(
            trackId = json?.optString("trackId").orEmpty(),
            format = format,
            bytes = json?.optLong("bytes") ?: 0L,
            // What was actually stored, not what was asked for. A track that
            // only exists at 160 must not look like a 320 download, or asking
            // for 320 again would never re-fetch anything.
            kbps = kbpsOf(format) ?: requestedKbps,
            downloadedAt = json?.optLong("downloadedAt") ?: System.currentTimeMillis(),
        )
    }

    private fun kbpsOf(format: String): Int? = when {
        format.endsWith("_96") -> 96
        format.endsWith("_160") || format.endsWith("_160_ENC") -> 160
        format.endsWith("_256") -> 256
        format.endsWith("_320") -> 320
        else -> null
    }

    // -------------------------------------------------------------- the link

    private enum class Link { NONE, METERED, UNMETERED }

    private fun link(): Link {
        val manager = connectivity ?: return Link.NONE
        val capabilities = manager.activeNetwork
            ?.let(manager::getNetworkCapabilities)
            ?: return Link.NONE
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return Link.NONE
        }
        // Not validated means a captive portal or a network that is up but goes
        // nowhere. Treated as no network, because that is what it is for us.
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return Link.NONE
        }
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            Link.UNMETERED
        } else {
            Link.METERED
        }
    }

    /** Null when downloading may go ahead, otherwise what it is waiting for. */
    private fun allowed(): Waiting? = when (_link.value) {
        Link.NONE -> Waiting.NETWORK
        Link.METERED -> if (settings.wifiOnly.value) Waiting.WIFI else null
        Link.UNMETERED -> null
    }

    private suspend fun awaitAllowed() {
        // The setting can change as well as the link, and a StateFlow of one
        // will not report the other. Combining them would mean building a flow
        // for a wait that ends either way, so this simply waits on whichever
        // moves and re-asks.
        val before = _link.value
        val wifiBefore = settings.wifiOnly.value
        while (_link.value == before && settings.wifiOnly.value == wifiBefore) {
            delay(NETWORK_POLL_MS)
        }
    }

    private fun holdWatch() {
        if (watching) return
        watching = true
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
        _link.value = link()
    }

    private fun releaseWatch() {
        if (!watching) return
        watching = false
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }

    private fun publish(waiting: Waiting?) {
        val wanted = store.owners.value.values.flatten().toSet()
        val files = store.files.value
        _status.value = Status(
            running = true,
            done = wanted.count(files::containsKey),
            total = wanted.size,
            waiting = waiting,
        )
    }

    private companion object {
        /**
         * One at a time, with a pause between tracks.
         *
         * Not a throughput decision — two were comfortably faster. Spotify
         * hands out one decryption key per file and refuses them when a session
         * asks too quickly, and a playlist fetched two at a time with no pause
         * tripped that within a dozen songs: librespot then sat out hold-offs
         * of twenty and thirty seconds, and the whole queue was slower than if
         * it had never hurried. This pace stays under it.
         */
        const val PARALLEL = 1

        /** Between one track and the next, to stay under the key limiter. */
        const val TRACK_GAP_MS = 1_200L

        /** A key that took this long was one the limiter held back. */
        const val KEY_WAIT_SLOW_MS = 1_500L

        /**
         * As slow as the pacing is ever allowed to get.
         *
         * Higher than it looks reasonable on purpose: a refusal costs the
         * session a hold of three seconds, then six, then twelve, so a gap that
         * trips the limiter is far more expensive than one that waits. Twenty
         * seconds of patience beats twenty-one seconds of holds and a queue
         * that also stops the music from starting.
         */
        const val MAX_GAP_MS = 20_000L

        /** Clean fetches before the gap is allowed to halve again. */
        const val CLEAN_RUN = 3

        /** How many songs' extras are caught up on in one turn of the loop. */
        const val EXTRAS_BATCH = 5

        /** Covers are small, so more of them fit in a turn. */
        const val COVER_BATCH = 12
        const val COVER_GAP_MS = 80L

        /**
         * How long to sit out a refusal that has already arrived.
         *
         * Longer than the gap because by this point Spotify has said no, and
         * librespot's own hold-off is already counting; asking again inside it
         * only confirms the pace it objected to.
         */
        const val THROTTLED_WAIT_MS = 20_000L

        const val NETWORK_POLL_MS = 1_500L

        /** How long to leave the engine alone before asking it again. */
        const val ENGINE_POLL_MS = 3_000L

        /** So a queue asleep on a long backoff still notices a new playlist. */
        const val MAX_SLEEP_MS = 30_000L
    }
}
