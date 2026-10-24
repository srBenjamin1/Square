package dev.lelonio.square.data.local

import dev.lelonio.square.data.*
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable

internal fun normalizedLocal(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
    .replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
internal fun localKey(kind: String, value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    val alphabet = "0123456789abcdef"
    val hex = CharArray(bytes.size * 2)
    bytes.forEachIndexed { i, byte ->
        val number = byte.toInt() and 255
        hex[i * 2] = alphabet[number ushr 4]
        hex[i * 2 + 1] = alphabet[number and 15]
    }
    return "local:$kind:" + String(hex)
}

@Serializable
data class LocalTrack(
    val id: Long,
    val contentUri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val mediaAlbumId: Long = 0,
    val track: Int = 0,
    val disc: Int = 0,
    val genre: String = "",
    val year: String = "",
    val durationMs: Long = 0,
    val fileName: String = "",
    val folder: String = "",
    val filePath: String? = null,
    val size: Long = 0,
    val mimeType: String = "",
    val addedSeconds: Long = 0,
    val modifiedSeconds: Long = 0,
    val generation: Long = 0,
    val rowSignature: String = "",
    val artRevision: Long = 0,
    val storageKey: String = "",
    val documentKey: String? = null,
    val treeUris: List<String> = emptyList(),
    val available: Boolean = true,
    val mediaReferenceId: Long? = null,
) {
    @kotlinx.serialization.Transient val uri: String = "local:track:$id"
    @kotlinx.serialization.Transient val fingerprint: String = "$size-$modifiedSeconds-$generation-$rowSignature"
    @kotlinx.serialization.Transient val artistUri: String = localKey("artist", normalizedLocal(artist))
    @kotlinx.serialization.Transient val albumUri: String = localKey("album", buildString {
        append(normalizedLocal(album))
        append('|')
        when {
            albumArtist.isNotBlank() -> append("artist:").append(normalizedLocal(albumArtist))
            mediaAlbumId > 0 && album.isNotBlank() -> append("store:").append(mediaAlbumId)
            else -> append(normalizedLocal(artist)).append('|').append(normalizedLocal(folder))
        }
    })
    @kotlinx.serialization.Transient val artwork: String = "squarelocalart://$id/$fingerprint-$artRevision"
    fun catalog() = CatalogTrack(
        uri = uri, name = title.ifBlank { fileName }, artist = artist,
        artistUri = artistUri.takeIf { artist.isNotBlank() },
        artists = if (artist.isBlank()) emptyList() else listOf(CatalogArtist(artist, artistUri)),
        album = album, albumUri = albumUri, year = year, durationMs = durationMs,
        artworkUrl = artwork, localFile = filePath,
        addedAt = addedSeconds.takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it).toString() },
    )
}

data class LocalAlbum(val uri: String, val name: String, val artist: String, val tracks: List<LocalTrack>) {
    val artwork: String? = tracks.firstOrNull()?.artwork?.let { first ->
        first + "?album=" + localKey("art", tracks.joinToString("|") { "${it.id}:${it.fingerprint}:${it.artRevision}" }).substringAfterLast(':')
    }
    val year get() = tracks.firstNotNullOfOrNull { it.year.takeIf(String::isNotBlank) }.orEmpty()
    val durationMs get() = tracks.sumOf { it.durationMs }
    fun catalog() = CatalogPlaylist(uri, name, artworkUrl = artwork, subtitle = artist)
}
data class LocalArtist(val uri: String, val name: String, val tracks: List<LocalTrack>, val albums: List<LocalAlbum>)
data class LocalGenre(val uri: String, val name: String, val tracks: List<LocalTrack>)

@Serializable
data class LocalPlaylist(val id: String, val name: String, val tracks: List<String> = emptyList()) {
    val uri get() = "local:playlist:$id"
    fun catalog() = CatalogPlaylist(uri, name)
    fun add(uris: List<String>) = copy(tracks = (tracks + uris.filter { it.startsWith("local:track:") }).distinct())
    fun remove(uri: String) = copy(tracks = tracks.filterNot { it == uri })
    fun move(from: Int, to: Int): LocalPlaylist {
        if (from !in tracks.indices || to !in tracks.indices || from == to) return this
        return copy(tracks = tracks.toMutableList().apply { add(to, removeAt(from)) })
    }
}

/** Manual choices and verified catalog identities share the persistent artwork cache. */
@Serializable
data class LocalArtistVisual(
    val artistUri: String,
    val remoteUri: String,
    val remoteName: String,
    val heroUrl: String? = null,
    val logoUrl: String? = null,
    val heroFile: String? = null,
    val logoFile: String? = null,
    val heroAspect: Float? = null,
    val automatic: Boolean = false,
    val needsReview: Boolean = false,
)

@Serializable
data class LocalMusicSnapshot(
    val tracks: List<LocalTrack> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val visuals: List<LocalArtistVisual> = emptyList(),
    val scannedAt: Long = 0,
    val folders: List<LocalMusicFolder> = emptyList(),
    val folderMode: LocalFolderMode = LocalFolderMode.ALL,
    val resolutions: List<LocalArtistResolution> = emptyList(),
    val motions: List<LocalMotionMatch> = emptyList(),
)

enum class LocalTrackOrder { TITLE, ARTIST, ALBUM, ADDED, DURATION }

/** Derived once after index changes, rather than on every scroll or tab opening. */
class LocalMusicCatalog(val snapshot: LocalMusicSnapshot = LocalMusicSnapshot()) {
    val allByUri = snapshot.tracks.associateBy { it.uri }
    val tracks = LocalFolderPolicy.visible(snapshot).distinctBy { it.uri }
    val byUri = tracks.associateBy { it.uri }
    val albums = tracks.groupBy { it.albumUri }.map { (uri, songs) ->
        val ordered = songs.sortedWith(compareBy<LocalTrack> { it.disc }.thenBy { it.track }.thenBy { it.fileName })
        LocalAlbum(uri, ordered.first().album, ordered.firstNotNullOfOrNull { it.albumArtist.takeIf(String::isNotBlank) }
            ?: ordered.map { it.artist }.distinct().joinToString(", "), ordered)
    }.sortedBy { normalizedLocal(it.name) }
    val albumByUri = albums.associateBy { it.uri }
    val artists = tracks.flatMap { song ->
        listOf(song.artist, song.albumArtist).filter(String::isNotBlank).distinctBy(::normalizedLocal).map { it to song }
    }.groupBy { normalizedLocal(it.first) }.map { (name, entries) ->
        val songs = entries.map { it.second }.distinctBy { it.uri }
        LocalArtist(localKey("artist", name), entries.first().first, songs, songs.mapNotNull { albumByUri[it.albumUri] }.distinctBy { it.uri })
    }.sortedBy { normalizedLocal(it.name) }
    val genres = tracks.groupBy { normalizedLocal(it.genre) }.map { (_, songs) ->
        LocalGenre(localKey("genre", normalizedLocal(songs.first().genre)), songs.first().genre, songs)
    }.sortedBy { normalizedLocal(it.name) }
    val playlists get() = snapshot.playlists
    fun ordered(order: LocalTrackOrder): List<LocalTrack> = when (order) {
        LocalTrackOrder.TITLE -> tracks.sortedBy { normalizedLocal(it.title) }
        LocalTrackOrder.ARTIST -> tracks.sortedWith(compareBy<LocalTrack> { normalizedLocal(it.artist) }.thenBy { normalizedLocal(it.title) })
        LocalTrackOrder.ALBUM -> tracks.sortedWith(compareBy<LocalTrack> { normalizedLocal(it.album) }.thenBy { it.disc }.thenBy { it.track })
        LocalTrackOrder.ADDED -> tracks.sortedByDescending { it.addedSeconds }
        LocalTrackOrder.DURATION -> tracks.sortedBy { it.durationMs }
    }
    fun playlistTracks(playlist: LocalPlaylist) = playlist.tracks.mapNotNull(byUri::get)
    fun missing(playlist: LocalPlaylist) = playlist.tracks.count { it !in byUri }
    fun folderTracks(path: String) = tracks.filter { it.folder == path || it.folder.startsWith(path.trimEnd('/') + "/") }
    fun childFolders(parent: String): List<String> {
        val prefix = if (parent.isEmpty()) "" else parent.trimEnd('/') + "/"
        return tracks.mapNotNull {
            val folder = it.folder.trimStart('/')
            val relative = folder.takeIf { f -> f.startsWith(prefix) }?.removePrefix(prefix).orEmpty()
            relative.substringBefore('/').takeIf(String::isNotBlank)?.let { next -> prefix + next }
        }.distinct().sortedBy(::normalizedLocal)
    }
    fun tracksOf(uri: String): List<LocalTrack> = when {
        uri == ALL_TRACKS -> ordered(LocalTrackOrder.ADDED)
        uri.startsWith("local:album:") -> albums.firstOrNull { it.uri == uri }?.tracks.orEmpty()
        uri.startsWith("local:artist:") -> artists.firstOrNull { it.uri == uri }?.tracks.orEmpty()
        uri.startsWith("local:genre:") -> genres.firstOrNull { it.uri == uri }?.tracks.orEmpty()
        uri.startsWith("local:playlist:") -> playlists.firstOrNull { it.uri == uri }?.let(::playlistTracks).orEmpty()
        uri.startsWith("local:folder:") -> tracks.filter { folderUri(it.folder) == uri }
        else -> emptyList()
    }
    private val searchable = tracks.associate { it.uri to normalizedLocal("${it.title} ${it.artist} ${it.album} ${it.fileName}") }
    fun search(query: String): SearchResults {
        val parts = normalizedLocal(query).split(' ').filter(String::isNotBlank)
        if (parts.isEmpty()) return SearchResults()
        fun matches(value: String) = parts.all { normalizedLocal(value).contains(it) }
        return SearchResults(
            tracks = tracks.filter { song -> parts.all { searchable.getValue(song.uri).contains(it) } }.map(LocalTrack::catalog),
            albums = albums.filter { matches(it.name + " " + it.artist) }.map { SearchItem(it.uri, it.name, it.artist, it.artwork) },
            artists = artists.filter { matches(it.name) }.map { SearchItem(it.uri, it.name, "", artistArtwork(it.uri, offline = true)) },
            playlists = playlists.filter { matches(it.name) }.map { SearchItem(it.uri, it.name, "", null) },
        )
    }
    fun artistArtwork(uri: String, offline: Boolean): String? {
        val visual = snapshot.visuals.firstOrNull { it.artistUri == uri && !it.needsReview }
        return localArtistPicture(visual?.heroFile, visual?.heroUrl, offline)
    }
    companion object {
        const val ALL_TRACKS = "local:collection:all"
        fun isContext(uri: String?) = uri == ALL_TRACKS || listOf("local:album:", "local:artist:", "local:genre:", "local:folder:", "local:playlist:").any { uri?.startsWith(it) == true }
        fun folderUri(path: String) = localKey("folder", path)
    }
}

/** A missing optional remote picture must never block an offline artist page. */
internal fun localArtistPicture(file: String?, remote: String?, offline: Boolean): String? =
    file?.takeIf { java.io.File(it).let { f -> f.isFile && f.length() > 0 } }
        ?: remote?.takeUnless { offline }
