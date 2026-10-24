package dev.lelonio.square.ui.local

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lelonio.square.R
import dev.lelonio.square.data.*
import dev.lelonio.square.data.local.*
import dev.lelonio.square.thor.input.ThorLazyVerticalGrid
import dev.lelonio.square.thor.input.ThorLazyColumn
import dev.lelonio.square.thor.input.ThorFocusScene
import dev.lelonio.square.ui.components.*
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import dev.lelonio.square.thor.input.ThorLazyRow
import dev.lelonio.square.ui.home.Heading
import dev.lelonio.square.ui.home.PlaylistTile
import dev.lelonio.square.ui.home.TrackTile
import dev.lelonio.square.ui.home.ArtistTile
import dev.lelonio.square.ui.home.ArtistCell
import dev.lelonio.square.ui.library.GridTile
import dev.lelonio.square.ui.library.LibraryTitle
import com.adamglin.phosphoricons.regular.ArrowClockwise
import com.adamglin.phosphoricons.Regular
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import dev.lelonio.square.ui.home.LocalMusicHeader
import dev.lelonio.square.ui.library.TrackRow
import dev.lelonio.square.ui.theme.Ink
import dev.lelonio.square.ui.theme.InkDim
import kotlinx.coroutines.launch

internal enum class LocalLanding { HOME, LIBRARY, RECENT }
private enum class Category(val label: Int) {
    ALBUMS(R.string.albums), ARTISTS(R.string.artists), SONGS(R.string.local_songs),
    GENRES(R.string.local_genres), FOLDERS(R.string.local_folders), PLAYLISTS(R.string.playlists),
}
private enum class Shelf(val label: Int) {
    ADDED_SONGS(R.string.local_recent), ADDED_ALBUMS(R.string.local_recent_albums),
    ADDED_ARTISTS(R.string.local_added_artists), HEARD_SONGS(R.string.local_heard_recent),
    HEARD_ALBUMS(R.string.local_heard_albums), HEARD_ARTISTS(R.string.local_heard_artists),
    TOP(R.string.local_most_played), PLAYLISTS(R.string.local_your_playlists), ALL_ALBUMS(R.string.albums),
}

/** Content of the original routes. The shared bar, controller and player remain their owners. */
@Composable
internal fun LocalMusicScreen(
    state: LocalMusicState,
    repository: LocalMusicRepository,
    landing: LocalLanding,
    padding: PaddingValues,
    backdrop: Backdrop,
    nowPlaying: String?,
    listeningEvents: List<ListeningEvent>,
    onSwitchSource: (androidx.compose.ui.unit.IntOffset) -> Unit,
    onSettings: () -> Unit,
    onPermission: () -> Unit,
    onOpen: (CatalogPlaylist) -> Unit,
    onPlay: (List<CatalogTrack>, Int, Boolean) -> Unit,
    onMenu: (CatalogTrack) -> Unit,
    onArtistVisible: (String) -> Unit,
) {
    var categoryName by rememberSaveable(landing) { mutableStateOf(Category.ALBUMS.name) }
    val category = Category.valueOf(categoryName)
    var browsing by rememberSaveable(landing) { mutableStateOf(false) }
    var shelfName by rememberSaveable(landing) { mutableStateOf<String?>(null) }
    val selectedShelf = shelfName?.let(Shelf::valueOf)
    var windowName by rememberSaveable { mutableStateOf(LocalListening.Window.ALL.name) }
    val window = LocalListening.Window.valueOf(windowName)
    var orderName by rememberSaveable(landing) { mutableStateOf(LocalTrackOrder.TITLE.name) }
    var folder by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var deleting by remember { mutableStateOf<LocalPlaylist?>(null) }
    var operationError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val layout = LocalLayoutPolicy.forWindow(configuration.screenWidthDp, configuration.screenHeightDp)
    val catalog = state.catalog
    val offline by dev.lelonio.square.playback.OfflineMode.active.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { kotlinx.coroutines.delay(60_000); value = System.currentTimeMillis() }
    }
    val emptySummary = remember { LocalListening.summarize(LocalMusicCatalog(), emptyList(), 0) }
    val history by produceState(emptySummary, catalog, listeningEvents, now) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            LocalListening.summarize(catalog, listeningEvents, now)
        }
    }
    val filtered by produceState(emptySummary, catalog, listeningEvents, now, window) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            LocalListening.summarize(catalog, listeningEvents, now, window)
        }
    }
    val all = remember(catalog, orderName) { catalog.ordered(LocalTrackOrder.valueOf(orderName)) }
    val additions = remember(catalog) { LocalAdditions.from(catalog) }
    val added = additions.songs
    val addedAlbums = additions.albums
    val addedArtists = additions.artists
    val overview = landing != LocalLanding.LIBRARY && !browsing && selectedShelf == null
    fun artistItem(artist: LocalArtist) = SearchItem(artist.uri, artist.name, "", catalog.artistArtwork(artist.uri, offline))
    fun openArtist(artist: LocalArtist) = onOpen(CatalogPlaylist(artist.uri, artist.name, catalog.artistArtwork(artist.uri, offline), isArtist = true))
    fun show(shelf: Shelf) { shelfName = shelf.name; browsing = false }
    fun operation(action: suspend () -> Unit) { scope.launch {
        try { action(); operationError = false }
        catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            operationError = true
        }
    } }
    BackHandler(enabled = browsing || selectedShelf != null || category == Category.FOLDERS && folder.isNotEmpty()) {
        if (category == Category.FOLDERS && folder.isNotEmpty() && selectedShelf == null) folder = folder.substringBeforeLast('/', "")
        else { browsing = false; shelfName = null }
    }
    // Content scrolls behind the chrome. Focus requests reveal its bottom inset
    // without taking that inset out of the viewport.
    androidx.compose.runtime.CompositionLocalProvider(
        dev.lelonio.square.thor.input.LocalThorObscuredBottom provides padding.calculateBottomPadding()) {
    key(landing, browsing, shelfName, if (overview) "overview" else categoryName,
        if (!overview && selectedShelf == null && category == Category.FOLDERS) folder else "") {
    ThorLazyVerticalGrid(columns = GridCells.Adaptive(if (layout.compact) 130.dp else 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(if (layout.compact) 6.dp else 10.dp)) {
        if (landing == LocalLanding.HOME && overview) item(span = { GridItemSpan(maxLineSpan) }) {
            LocalMusicHeader(PaddingValues(0.dp), backdrop, onSwitchSource, onSettings)
        } else item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (landing == LocalLanding.LIBRARY && selectedShelf == null) {
                    LibraryTitle(stringResource(R.string.local_track_count, catalog.tracks.size), Modifier.weight(1f))
                    dev.lelonio.square.ui.glass.LiquidButton(onClick = { repository.rescan() }, backdrop = backdrop,
                        modifier = Modifier.size(44.dp), contentHeight = 44.dp, contentPadding = 0.dp) {
                        Icon(com.adamglin.PhosphorIcons.Regular.ArrowClockwise,
                            contentDescription = stringResource(R.string.local_rescan), tint = Ink, modifier = Modifier.size(22.dp))
                    }
                } else {
                    if (!overview) LocalAction("‹ " + stringResource(R.string.back)) { browsing = false; shelfName = null }
                    Text(stringResource(selectedShelf?.label ?: if (landing == LocalLanding.RECENT) R.string.tab_new else category.label),
                        style = MaterialTheme.typography.headlineLarge, color = Ink, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        if (state.loading || state.scanning) item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.dp)
                Text(stringResource(R.string.local_scanning, state.progress, state.total), color = InkDim)
            }
        }
        if (state.folderFallback) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.local_folder_fallback), color = InkDim, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp))
        }
        if (state.needsPermission) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(stringResource(R.string.local_files_needs_permission), color = InkDim)
                LocalAction(stringResource(R.string.local_files_allow), onClick = onPermission)
            }
        } else {
            if (state.error || operationError) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.local_operation_failed), color = InkDim, modifier = Modifier.padding(horizontal = 24.dp))
            }
            if (overview) {
                if (landing == LocalLanding.HOME) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ThorLazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listItems(Category.entries, key = { it.name }) { item ->
                                LocalAction(stringResource(item.label)) { categoryName = item.name; browsing = true }
                            }
                        }
                    }
                    if (history.recent.isEmpty() && catalog.tracks.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.local_history_empty), style = MaterialTheme.typography.bodySmall,
                            color = InkDim, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    if (history.recent.isNotEmpty()) songShelf(Shelf.HEARD_SONGS, history.recent, ::show, onPlay)
                    if (history.albums.isNotEmpty()) albumShelf(Shelf.HEARD_ALBUMS, history.albums, ::show, onOpen)
                    if (history.artists.isNotEmpty()) artistShelf(Shelf.HEARD_ARTISTS, history.artists, ::show, ::artistItem, ::openArtist, onArtistVisible)
                    if (history.top.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Heading(stringResource(R.string.local_most_played), onClick = { show(Shelf.TOP) }, compact = true)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LocalWindows(window, { windowName = it.name })
                        }
                        if (filtered.top.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.local_window_empty), color = InkDim, modifier = Modifier.padding(horizontal = 24.dp))
                        } else songShelfRow(filtered.top, onPlay)
                    }
                    if (history.playlists.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { Heading(stringResource(R.string.local_your_playlists), onClick = { show(Shelf.PLAYLISTS) }, compact = true) }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ThorLazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                listItems(history.playlists.take(12), key = { it.uri }) { p ->
                                    PlaylistTile(p.catalog().copy(artworkUrl = catalog.playlistTracks(p).firstOrNull()?.artwork), tileSize = layout.albumSize.dp) { onOpen(p.catalog()) }
                                }
                            }
                        }
                    }
                } else item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.local_new_note), color = InkDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp))
                }
                if (addedAlbums.isNotEmpty()) albumShelf(Shelf.ADDED_ALBUMS, addedAlbums, ::show, onOpen)
                else if (catalog.albums.isNotEmpty()) albumShelf(Shelf.ALL_ALBUMS, catalog.albums, ::show, onOpen)
                if (added.isNotEmpty()) songShelf(Shelf.ADDED_SONGS, added, ::show, onPlay)
                else if (catalog.tracks.isNotEmpty()) {
                    // Files without a known DATE_ADDED still remain accessible; no date is invented.
                    item(span = { GridItemSpan(maxLineSpan) }) { LocalAction(stringResource(R.string.local_all_songs)) { categoryName = Category.SONGS.name; browsing = true } }
                }
                if (landing == LocalLanding.RECENT && addedArtists.isNotEmpty()) artistShelf(Shelf.ADDED_ARTISTS, addedArtists, ::show, ::artistItem, ::openArtist, onArtistVisible)
            } else {
                if (selectedShelf == null) item(span = { GridItemSpan(maxLineSpan) }) {
                    ThorLazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listItems(Category.entries, key = { it.name }) { item ->
                            LocalAction(stringResource(item.label), selected = item == category) { categoryName = item.name }
                        }
                    }
                }
                val displayedCategory = when (selectedShelf) {
                    Shelf.ADDED_ALBUMS, Shelf.HEARD_ALBUMS, Shelf.ALL_ALBUMS -> Category.ALBUMS
                    Shelf.ADDED_ARTISTS, Shelf.HEARD_ARTISTS -> Category.ARTISTS
                    Shelf.ADDED_SONGS, Shelf.HEARD_SONGS, Shelf.TOP -> Category.SONGS
                    Shelf.PLAYLISTS -> Category.PLAYLISTS
                    null -> category
                }
                when (displayedCategory) {
                    Category.ALBUMS -> {
                        val albums = when (selectedShelf) { Shelf.ADDED_ALBUMS -> addedAlbums; Shelf.HEARD_ALBUMS -> history.albums; else -> catalog.albums }
                        items(albums, key = { it.uri }) { album ->
                            Box(Modifier.padding(horizontal = 10.dp)) { GridTile(album.catalog(), false, { onOpen(album.catalog()) }, null) }
                        }
                    }
                    Category.ARTISTS -> {
                        val artists = when (selectedShelf) { Shelf.ADDED_ARTISTS -> addedArtists; Shelf.HEARD_ARTISTS -> history.artists; else -> catalog.artists }
                        items(artists, key = { it.uri }) { artist ->
                            Column(Modifier.padding(horizontal = 10.dp)) {
                                LaunchedEffect(artist.uri, catalog.snapshot.visuals.firstOrNull { it.artistUri == artist.uri }) { onArtistVisible(artist.uri) }
                                ArtistCell(artistItem(artist)) { openArtist(artist) }

                            }
                        }
                    }
                    Category.SONGS -> {
                        if (selectedShelf == Shelf.TOP) item(span = { GridItemSpan(maxLineSpan) }) { LocalWindows(window, { windowName = it.name }) }
                        if (selectedShelf == null) item(span = { GridItemSpan(maxLineSpan) }) {
                            ThorLazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listItems(LocalTrackOrder.entries) { sort ->
                                    LocalAction(stringResource(when (sort) {
                                        LocalTrackOrder.TITLE -> R.string.title; LocalTrackOrder.ARTIST -> R.string.artist
                                        LocalTrackOrder.ALBUM -> R.string.album; LocalTrackOrder.ADDED -> R.string.recently_added
                                        LocalTrackOrder.DURATION -> R.string.duration
                                    }), selected = orderName == sort.name) { orderName = sort.name }
                                }
                            }
                        }
                        val songs = when (selectedShelf) { Shelf.ADDED_SONGS -> added; Shelf.HEARD_SONGS -> history.recent; Shelf.TOP -> filtered.top; else -> all }
                        itemsIndexed(songs, key = { _, it -> it.uri }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, song ->
                            TrackRow(song.catalog(), index, song.uri == nowPlaying,
                                onClick = { onPlay(songs.map(LocalTrack::catalog), index, false) }, onMenu = { onMenu(song.catalog()) },
                                subtitle = if (selectedShelf == Shelf.TOP) stringResource(R.string.local_play_count, filtered.counts[song.uri] ?: 0)
                                    else listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" · "))
                        }
                        if (songs.isEmpty() && catalog.tracks.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.local_window_empty), color = InkDim, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                    Category.GENRES -> items(catalog.genres, key = { it.uri }) { genre ->
                        val name = genre.name.ifBlank { stringResource(R.string.local_unknown_genre) }
                        Box(Modifier.padding(horizontal = 10.dp)) { GridTile(CatalogPlaylist(genre.uri, name, genre.tracks.firstOrNull()?.artwork), false,
                            { onOpen(CatalogPlaylist(genre.uri, name)) }, null) }
                    }
                    Category.FOLDERS -> {
                        if (folder.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { LocalAction("‹ " + folder) { folder = folder.substringBeforeLast('/', "") } }
                        items(catalog.childFolders(folder), key = { "folder:$it" }) { path ->
                            LocalAction(path.substringAfterLast('/')) { folder = path }
                        }
                        val songs = catalog.tracks.filter { it.folder.trimStart('/') == folder }
                        itemsIndexed(songs, key = { _, it -> it.uri }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, song ->
                            TrackRow(song.catalog(), index, song.uri == nowPlaying, onClick = { onPlay(songs.map(LocalTrack::catalog), index, false) },
                                onMenu = { onMenu(song.catalog()) }, subtitle = song.fileName)
                        }
                    }
                    Category.PLAYLISTS -> {
                        item(span = { GridItemSpan(maxLineSpan) }) { LocalAction(stringResource(R.string.new_playlist)) { naming = null to "" } }
                        items(history.playlists, key = { it.uri }) { playlist ->
                            Column(Modifier.padding(horizontal = 10.dp)) {
                                GridTile(playlist.catalog().copy(artworkUrl = catalog.playlistTracks(playlist).firstOrNull()?.artwork), false,
                                    { onOpen(playlist.catalog()) }, { editing = playlist.uri })
                                LocalAction(stringResource(R.string.local_manage_playlist)) { editing = playlist.uri }
                            }
                        }
                        if (catalog.playlists.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.local_playlists_empty), color = InkDim, modifier = Modifier.padding(horizontal = 24.dp)) }
                    }
                }
            }
            if (catalog.tracks.isEmpty() && !state.scanning && !state.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.local_empty), color = InkDim, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
    }
    }
    naming?.let { request ->
        NameDialog(title = stringResource(if (request.first == null) R.string.new_playlist else R.string.local_rename_playlist), initial = request.second,
            confirmLabel = stringResource(R.string.save), onConfirm = { name ->
                naming = null
                operation { if (request.first == null) repository.createPlaylist(name) else repository.renamePlaylist(request.first!!, name) }
            }, onDismiss = { naming = null })
    }
    editing?.let { uri -> catalog.playlists.firstOrNull { it.uri == uri }?.let { playlist ->
        LocalPlaylistEditor(playlist, catalog, onClose = { editing = null },
            onRename = { editing = null; naming = playlist.uri to playlist.name },
            onDelete = { editing = null; deleting = playlist },
            onMove = { from, to -> operation { repository.move(uri, from, to) } },
            onRemove = { track -> operation { repository.remove(uri, track) } })
    } }
    deleting?.let { playlist ->
        ConfirmDialog(stringResource(R.string.delete_playlist_title), stringResource(R.string.local_delete_playlist_note), stringResource(R.string.delete),
            onConfirm = { deleting = null; operation { repository.deletePlaylist(playlist.uri) } }, onDismiss = { deleting = null })
    }

}

@Composable
private fun LocalAction(text: String, selected: Boolean = false, onClick: () -> Unit) {
    FilterChip(text, selected, onClick)
}

@Composable
private fun LocalWindows(selected: LocalListening.Window, onSelect: (LocalListening.Window) -> Unit) {
    Column {
        ThorLazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listItems(LocalListening.Window.entries) { window ->
                LocalAction(stringResource(when (window) {
                    LocalListening.Window.WEEK -> R.string.local_week
                    LocalListening.Window.MONTH -> R.string.local_month
                    LocalListening.Window.ALL -> R.string.local_all_history
                }), window == selected) { onSelect(window) }
            }
        }
        Text(stringResource(R.string.local_history_scope), color = InkDim, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.songShelf(shelf: Shelf, songs: List<LocalTrack>,
    show: (Shelf) -> Unit, onPlay: (List<CatalogTrack>, Int, Boolean) -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { Heading(stringResource(shelf.label), onClick = { show(shelf) }, compact = true) }
    songShelfRow(songs, onPlay)
}
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.songShelfRow(songs: List<LocalTrack>, onPlay: (List<CatalogTrack>, Int, Boolean) -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        ThorLazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listItemsIndexed(songs.take(12), key = { _, it -> it.uri }) { index, song ->
                val c = androidx.compose.ui.platform.LocalConfiguration.current
                TrackTile(song.catalog(), tileSize = LocalLayoutPolicy.forWindow(c.screenWidthDp, c.screenHeightDp).songSize.dp) { onPlay(songs.map(LocalTrack::catalog), index, false) }
            }
        }
    }
}
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.albumShelf(shelf: Shelf, albums: List<LocalAlbum>, show: (Shelf) -> Unit, onOpen: (CatalogPlaylist) -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { Heading(stringResource(shelf.label), onClick = { show(shelf) }, compact = true) }
    item(span = { GridItemSpan(maxLineSpan) }) {
        ThorLazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listItems(albums.take(12), key = { it.uri }) { album ->
                val c = androidx.compose.ui.platform.LocalConfiguration.current
                PlaylistTile(album.catalog(), tileSize = LocalLayoutPolicy.forWindow(c.screenWidthDp, c.screenHeightDp).albumSize.dp) { onOpen(album.catalog()) }
            }
        }
    }
}
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.artistShelf(shelf: Shelf, artists: List<LocalArtist>, show: (Shelf) -> Unit,
    itemOf: (LocalArtist) -> SearchItem, onOpen: (LocalArtist) -> Unit, onVisible: (String) -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { Heading(stringResource(shelf.label), onClick = { show(shelf) }, compact = true) }
    item(span = { GridItemSpan(maxLineSpan) }) {
        ThorLazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listItems(artists.take(12), key = { it.uri }) { artist ->
                val item = itemOf(artist)
                LaunchedEffect(artist.uri, item.artworkUrl) { onVisible(artist.uri) }
                val c = androidx.compose.ui.platform.LocalConfiguration.current
                ArtistTile(item, tileSize = LocalLayoutPolicy.forWindow(c.screenWidthDp, c.screenHeightDp).artistSize.dp) { onOpen(artist) }
            }
        }
    }
}

@Composable
private fun LocalPlaylistEditor(playlist: LocalPlaylist, catalog: LocalMusicCatalog, onClose: () -> Unit,
    onRename: () -> Unit, onDelete: () -> Unit, onMove: (Int, Int) -> Unit, onRemove: (String) -> Unit) {
    Dialog(onDismissRequest = onClose) {
        ThorFocusScene("local-playlist-editor") {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).menuSkin(RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(playlist.name, style = MaterialTheme.typography.titleLarge, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalAction(stringResource(R.string.rename), onClick = onRename)
                    LocalAction(stringResource(R.string.delete), onClick = onDelete)
                }
                ThorLazyColumn(Modifier.weight(1f, fill = false)) {
                    items(playlist.tracks.size, key = { playlist.tracks[it] }) { index ->
                        val uri = playlist.tracks[index]
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(catalog.byUri[uri]?.title ?: stringResource(R.string.local_missing_file), color = Ink, modifier = Modifier.weight(1f), maxLines = 2)
                            if (index > 0) LocalAction("↑") { onMove(index, index - 1) }
                            if (index < playlist.tracks.lastIndex) LocalAction("↓") { onMove(index, index + 1) }
                            LocalAction("−") { onRemove(uri) }
                        }
                    }
                }
                LocalAction(stringResource(R.string.close), onClick = onClose)
            }
        }
    }
}
