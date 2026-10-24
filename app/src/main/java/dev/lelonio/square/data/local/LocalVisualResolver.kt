package dev.lelonio.square.data.local

import dev.lelonio.square.data.AppleCatalog
import dev.lelonio.square.data.MusicSource
import dev.lelonio.square.data.PreferencesStore
import dev.lelonio.square.playback.OfflineMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One background worker per application, no per-track image lookup or eager image downloads. */
class LocalVisualResolver(private val music: LocalMusicRepository, private val artwork: LocalArtworkRepository,
    private val preferences: PreferencesStore, private val client: okhttp3.OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> music.reportError() })
    private var job: Job? = null
    private val assets = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val motionLock = Mutex()
    private data class Work(val uri: String, val evidence: String, val blocked: Boolean, val retry: Long)
    private val requestLock = Mutex()
    private val assetSlots = kotlinx.coroutines.sync.Semaphore(2)
    private val attemptedAssets = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val week = 7L * 86_400_000
    private var lastRequest = 0L
    @Synchronized fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(music.state, preferences.source, OfflineMode.active) { state, source, offline ->
                if (offline || source != MusicSource.LOCAL || state.loading) emptyList()
                else state.catalog.artists.map { artist ->
                    val decision = state.catalog.snapshot.resolutions.firstOrNull { it.artistUri == artist.uri }
                    val manual = state.catalog.snapshot.visuals.any { it.artistUri == artist.uri && !it.automatic }
                    Work(artist.uri, LocalVisualMatching.evidence(artist.tracks), manual || decision?.status in listOf(LocalResolutionStatus.MANUAL, LocalResolutionStatus.REJECTED), decision?.retryGeneration ?: 0)
                }
            }.distinctUntilChanged().collectLatest { entries ->
                for ((uri, evidence, blocked, _) in entries) {
                    if (blocked) continue
                    val catalog = music.awaitCatalog()
                    val decision = catalog.snapshot.resolutions.firstOrNull { it.artistUri == uri }
                    if (decision?.evidence == evidence && decision.status != LocalResolutionStatus.PENDING && System.currentTimeMillis() - decision.checkedAt < week) continue
                    val artist = catalog.artists.firstOrNull { it.uri == uri } ?: continue
                    try {
                        music.autoAssociation(LocalArtistResolution(uri, LocalResolutionStatus.PENDING, evidence))
                        val candidates = mutableListOf<LocalSongCandidate>()
                        // At most three representative songs, one artist at a time. Never fan out a library.
                        for (song in artist.tracks.filter { it.artistUri == uri && it.title.isNotBlank() && it.artist.isNotBlank() }.distinctBy { normalizedLocal(it.title) }.take(3)) {
                            throttle()
                            candidates += AppleCatalog.localCandidates(song, forceRefresh = decision != null && decision.retryGeneration != 0L &&
                                decision.status == LocalResolutionStatus.PENDING && decision.checkedAt == 0L) ?: throw java.io.IOException("Catalog temporarily unavailable")
                        }
                        val (status, id) = LocalVisualMatching.artist(artist, candidates)
                        val visual = if (id != null) {
                            throttle()
                            AppleCatalog.localArtist(id, artist.name)?.let { LocalArtistVisual(uri, "applemusic:artist:$id", artist.name,
                                heroUrl = it.heroUrl, logoUrl = it.logoUrl, heroAspect = it.heroAspect, automatic = true) }
                        } else null
                        music.autoAssociation(LocalArtistResolution(uri, if (id != null && visual == null) LocalResolutionStatus.PENDING else status, evidence, System.currentTimeMillis()), visual)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        music.autoAssociation(LocalArtistResolution(uri, LocalResolutionStatus.PENDING, evidence, System.currentTimeMillis()))
                        delay(30_000) // do not hammer an unavailable provider
                    }
                }
            }
        }
    }
    private suspend fun throttle() = requestLock.withLock {
        delay((1_500 - (System.currentTimeMillis() - lastRequest)).coerceAtLeast(0))
        check(!OfflineMode.active.value)
        lastRequest = System.currentTimeMillis()
    }
    fun ensureArtwork(uri: String) {
        if (!assets.add(uri)) return
        scope.launch {
            try {
                val visual = music.awaitCatalog().snapshot.visuals.firstOrNull { it.artistUri == uri } ?: return@launch
                if (visual.needsReview) return@launch
                if ((visual.heroUrl == null || visual.heroFile?.let { java.io.File(it).isFile } == true) &&
                    (visual.logoUrl == null || visual.logoFile?.let { java.io.File(it).isFile } == true)) return@launch
                val key = "$uri|${visual.remoteUri}|${OfflineMode.active.value}"
                val now = System.currentTimeMillis()
                if (now - (attemptedAssets[key] ?: 0) < 15 * 60_000) return@launch
                attemptedAssets[key] = now
                assetSlots.acquire()
                try { music.cacheAssociation(artwork.cacheVisual(visual, client)) } finally { assetSlots.release() }
            } finally { assets.remove(uri) }
        }
    }
    suspend fun motion(track: LocalTrack): AppleCatalog.Album? = motionLock.withLock {
        if (OfflineMode.active.value) {
            LocalVisualDiagnostics.record(LocalVisualDiagnostics.Stage.OFFLINE, track.uri)
            return@withLock null
        }
        val signature = LocalVisualMatching.evidence(listOf(track))
        val cached = music.awaitCatalog().snapshot.motions.firstOrNull { it.trackUri == track.uri && it.evidence == signature }
        if (cached != null && (cached.artwork != null || System.currentTimeMillis() - cached.checkedAt < week)) {
            LocalVisualDiagnostics.record(LocalVisualDiagnostics.Stage.CACHE_HIT, track.uri)
            return@withLock cached.artwork
        }

        throttle()
        val found = try { AppleCatalog.localMotion(track) } catch (e: Exception) {
            if (e is CancellationException) throw e
            LocalVisualDiagnostics.record(LocalVisualDiagnostics.Stage.LOAD_ERROR, track.uri)
            throw e
        }
        music.motion(LocalMotionMatch(track.uri, signature, System.currentTimeMillis(), found?.first, found?.second))
        found?.second
    }
}
