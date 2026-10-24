package dev.lelonio.square.data.local

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dev.lelonio.square.data.LocalLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class LocalMusicState(
    val catalog: LocalMusicCatalog = LocalMusicCatalog(),
    val loading: Boolean = true,
    val scanning: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val needsPermission: Boolean = false,
    val error: Boolean = false,
    val folderFallback: Boolean = false,
)

/** One process-wide index shared by both activities. Audio itself is never copied. */
@OptIn(FlowPreview::class)
class LocalMusicRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = LocalMusicStore(File(context.filesDir, "local-music/index.json"))
    private val indexer = LocalLibraryIndexer(context)
    private val mutableState = MutableStateFlow(LocalMusicState())
    val state = mutableState.asStateFlow()
    private val ready = CompletableDeferred<Unit>()
    private val dataLock = Mutex()
    private val scanLock = Mutex()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var observing = false
    private var lastScan = 0L

    init {
        scope.launch {
            val loaded = runCatching { store.read() }
            mutableState.value = LocalMusicState(catalog = LocalMusicCatalog(loaded.getOrDefault(LocalMusicSnapshot())),
                loading = false, needsPermission = !LocalLibrary.granted(context) && loaded.getOrNull()?.folders.orEmpty().none { it.accessible }, error = loaded.isFailure)
            ready.complete(Unit)
        }
        scope.launch { changes.debounce(700).collect { refresh() } }
    }

    suspend fun awaitCatalog(): LocalMusicCatalog { ready.await(); return state.value.catalog }

    fun start() {
        if (!observing && LocalLibrary.granted(context)) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { changes.tryEmit(Unit) }
            }
            runCatching {
                context.contentResolver.registerContentObserver(android.net.Uri.parse("content://media"), true, observer)
                observing = true
            }
        }
        scope.launch { refresh() }
    }

    fun rescan() { scope.launch { refresh(force = true) } }
    fun reportError() { mutableState.update { it.copy(error = true) } }

    suspend fun refresh(force: Boolean = false) {
        ready.await()
        scanLock.withLock {
            if (!force && System.currentTimeMillis() - lastScan < 2_000) return
            mutableState.update { it.copy(scanning = true, needsPermission = false, error = false) }
            try {
                val scannedFolders = state.value.catalog.snapshot.folders
                val result = indexer.scan(state.value.catalog.snapshot.tracks, force, scannedFolders) { done, total ->
                    if (done == total || done % 25 == 0) mutableState.update { it.copy(progress = done, total = total) }
                }
                dataLock.withLock {
                    val current = state.value.catalog.snapshot
                    if (current.folders.map { it.uri } != scannedFolders.map { it.uri }) {
                        mutableState.update { it.copy(scanning = false) }
                        changes.tryEmit(Unit)
                        return@withLock
                    }
                    val folders = current.folders.map { folder -> result.folders.firstOrNull { it.uri == folder.uri } ?: folder }
                    val mode = LocalFolderPolicy.effective(current.folderMode, folders)
                    val currentUris = result.tracks.mapTo(hashSetOf()) { it.uri }
                    val tombstones = current.tracks.filter { it.documentKey != null && it.uri !in currentUris }.takeLast(10_000).map { it.copy(available = false) }
                    val snapshot = current.copy(tracks = result.tracks + tombstones, folders = folders, folderMode = mode, scannedAt = System.currentTimeMillis())
                    store.write(snapshot)
                    mutableState.update { it.copy(catalog = LocalMusicCatalog(snapshot), scanning = false,
                        needsPermission = !LocalLibrary.granted(context) && folders.none { f -> f.accessible },
                        folderFallback = it.folderFallback || mode != current.folderMode) }
                }
                lastScan = System.currentTimeMillis()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(scanning = false, error = true, needsPermission = !LocalLibrary.granted(context)) }
            }
        }
    }

    private suspend fun mutate(change: (LocalMusicSnapshot) -> LocalMusicSnapshot) = withContext(Dispatchers.IO) {
        ready.await()
        dataLock.withLock {
            val snapshot = change(state.value.catalog.snapshot)
            if (snapshot == state.value.catalog.snapshot) return@withLock
            store.write(snapshot)
            mutableState.update { it.copy(catalog = LocalMusicCatalog(snapshot)) }
        }
    }
    suspend fun createPlaylist(name: String): LocalPlaylist {
        require(name.isNotBlank())
        val item = LocalPlaylist(UUID.randomUUID().toString(), name.trim())
        mutate { it.copy(playlists = it.playlists + item) }
        return item
    }
    suspend fun renamePlaylist(uri: String, name: String) {
        require(name.isNotBlank())
        mutate { s -> s.copy(playlists = s.playlists.map { if (it.uri == uri) it.copy(name = name.trim()) else it }) }
    }
    suspend fun deletePlaylist(uri: String) = mutate { it.copy(playlists = it.playlists.filterNot { p -> p.uri == uri }) }
    suspend fun add(uri: String, tracks: List<String>) = mutate { s -> s.copy(playlists = s.playlists.map { if (it.uri == uri) it.add(tracks) else it }) }
    suspend fun remove(uri: String, track: String) = mutate { s -> s.copy(playlists = s.playlists.map { if (it.uri == uri) it.remove(track) else it }) }
    suspend fun move(uri: String, from: Int, to: Int) = mutate { s -> s.copy(playlists = s.playlists.map { if (it.uri == uri) it.move(from, to) else it }) }
    suspend fun associate(visual: LocalArtistVisual) = mutate { s -> s.copy(visuals = s.visuals.filterNot { it.artistUri == visual.artistUri } + visual.copy(automatic = false, needsReview = false),
        resolutions = s.resolutions.filterNot { it.artistUri == visual.artistUri } + LocalArtistResolution(visual.artistUri, LocalResolutionStatus.MANUAL)) }
    suspend fun cacheAssociation(visual: LocalArtistVisual) = mutate { s ->
        s.copy(visuals = s.visuals.map { current ->
            if (current.artistUri == visual.artistUri && current.remoteUri == visual.remoteUri && current.automatic == visual.automatic && current.needsReview == visual.needsReview) visual else current
        })
    }
    suspend fun clearAssociation(uri: String) = mutate { s -> s.copy(visuals = s.visuals.filterNot { it.artistUri == uri },
        resolutions = s.resolutions.filterNot { it.artistUri == uri } + LocalArtistResolution(uri, LocalResolutionStatus.REJECTED)) }
    suspend fun retryArtist(uri: String) = mutate { s -> s.copy(resolutions = s.resolutions.filterNot { it.artistUri == uri } +
        LocalArtistResolution(uri, LocalResolutionStatus.PENDING, retryGeneration = System.nanoTime())) }
    suspend fun autoAssociation(resolution: LocalArtistResolution, visual: LocalArtistVisual? = null) =
        mutate { LocalArtistPolicy.apply(it, resolution, visual) }
    suspend fun motion(match: LocalMotionMatch) = mutate { s -> s.copy(motions = (s.motions.filterNot { it.trackUri == match.trackUri } + match).takeLast(200)) }
    suspend fun playableUri(uri: String): android.net.Uri? {
        ready.await()
        return state.value.catalog.allByUri[uri]?.takeIf { it.available }?.contentUri?.let(android.net.Uri::parse)
    }
    suspend fun addFolder(uri: android.net.Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val document = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, android.provider.DocumentsContract.getTreeDocumentId(uri))
        val name = context.contentResolver.query(document, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: error("Folder unavailable")
        mutate { it.copy(folders = it.folders.filterNot { f -> f.uri == uri.toString() } + LocalMusicFolder(uri.toString(), name)) }
        mutableState.update { it.copy(folderFallback = false) }
        refresh(true)
    }
    suspend fun removeFolder(uri: String) {
        mutate { s ->
            val folders = s.folders.filterNot { it.uri == uri }
            val mode = LocalFolderPolicy.effective(s.folderMode, folders)
            if (mode != s.folderMode) mutableState.update { it.copy(folderFallback = true) }
            s.copy(folders = folders, folderMode = mode, tracks = s.tracks.map { t ->
                val trees = t.treeUris.filterNot { it == uri }
                if (uri in t.treeUris) t.copy(treeUris = trees, available = trees.isNotEmpty() || t.id >= 0 && LocalLibrary.granted(context),
                    contentUri = if (trees.isEmpty() && t.id >= 0) LocalLibrary.contentUri(t.uri).toString() else t.contentUri) else t
            })
        }
        withContext(Dispatchers.IO) { runCatching { context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        refresh(true)
    }
    suspend fun folderMode(mode: LocalFolderMode): Boolean {
        var accepted = true
        mutate { s ->
            accepted = mode == LocalFolderMode.ALL || s.folders.any { it.accessible }
            s.copy(folderMode = if (accepted) mode else LocalFolderMode.ALL)
        }
        if (accepted) mutableState.update { it.copy(folderFallback = false) }
        return accepted
    }
}
