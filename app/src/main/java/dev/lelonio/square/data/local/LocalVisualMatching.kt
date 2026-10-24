package dev.lelonio.square.data.local

import dev.lelonio.square.data.AppleCatalog
import kotlinx.serialization.Serializable

@Serializable enum class LocalResolutionStatus { MANUAL, VERIFIED, PENDING, AMBIGUOUS, NOT_FOUND, REJECTED, REVIEW_REQUIRED }
@Serializable data class LocalArtistResolution(val artistUri: String, val status: LocalResolutionStatus,
    val evidence: String = "", val checkedAt: Long = 0, val retryGeneration: Long = 0)
@Serializable data class LocalMotionMatch(val trackUri: String, val evidence: String, val checkedAt: Long,
    val albumId: String? = null, val artwork: AppleCatalog.Album? = null)

data class LocalSongCandidate(val id: String, val name: String, val artist: String, val album: String,
    val durationMs: Long, val artistId: String, val albumId: String)

/** Conservative matching keeps edition qualifiers, punctuation and compound artist names intact. */
object LocalVisualMatching {
    fun evidence(tracks: List<LocalTrack>) = localKey("evidence-v2", tracks.sortedBy { it.uri }
        .joinToString("|") { "${it.title}/${it.artist}/${it.album}/${it.albumArtist}/${it.durationMs}" })
    // Exact song and artist names are the identity boundary. Album and duration
    // rank editions; absent duration is never evidence against a song.
    fun song(track: LocalTrack, candidate: LocalSongCandidate): Boolean =
        track.title.isNotBlank() && track.artist.isNotBlank() &&
        normalizedLocal(track.title) == normalizedLocal(candidate.name) &&
        normalizedLocal(track.artist) == normalizedLocal(candidate.artist) &&
        candidate.id.isNotBlank() && candidate.albumId.isNotBlank()

    fun ranked(track: LocalTrack, candidates: List<LocalSongCandidate>): List<LocalSongCandidate> {
        val exact = candidates.filter { song(track, it) }
        if (exact.isEmpty()) return emptyList()
        fun score(c: LocalSongCandidate): Int =
            (if (track.album.isNotBlank() && normalizedLocal(track.album) == normalizedLocal(c.album)) 4 else 0) +
            (if (track.durationMs > 0 && c.durationMs > 0 && kotlin.math.abs(track.durationMs - c.durationMs) <= 2_000) 1 else 0)
        val best = exact.maxOf(::score)
        return exact.filter { score(it) == best }.distinct()
    }
    fun uniqueAlbum(track: LocalTrack, candidates: List<LocalSongCandidate>): String? =
        ranked(track, candidates).map { it.albumId }.distinct().singleOrNull()

    fun artist(artist: LocalArtist, candidates: List<LocalSongCandidate>): Pair<LocalResolutionStatus, String?> {
        val evidence = artist.tracks.distinctBy { normalizedLocal(it.title) }.mapNotNull { local ->
            // A known album must corroborate an artist identity. Motion may search
            // other editions of an already exact song, but an artist homonym may not.
            ranked(local, candidates).filter { it.artistId.isNotBlank() && normalizedLocal(it.artist) == normalizedLocal(artist.name) &&
                (local.album.isBlank() || normalizedLocal(local.album) == normalizedLocal(it.album)) }
                .map { it.artistId }.toSet().takeIf { it.isNotEmpty() }
        }
        if (evidence.isEmpty()) return LocalResolutionStatus.NOT_FOUND to null
        val identities = evidence.reduce { a, b -> a intersect b }
        return if (identities.size == 1) LocalResolutionStatus.VERIFIED to identities.single()
            else LocalResolutionStatus.AMBIGUOUS to null
    }

    /** Only a resource shared by every plausible edition can escape an edition tie. */
    fun sharedMotion(albums: List<AppleCatalog.Album>): AppleCatalog.Album? {
        if (albums.isEmpty()) return null
        val portrait = albums.map { it.motionUrl }.distinct().singleOrNull()
        val square = albums.map { it.motionSquareUrl }.distinct().singleOrNull()
        return if (portrait == null && square == null) null
            else AppleCatalog.Album(heroUrl = null, coverUrl = null, motionUrl = portrait, motionSquareUrl = square)
    }

}

/** Shared by the persistence boundary and tests: asynchronous work never overrides a user choice. */
object LocalArtistPolicy {
    fun apply(snapshot: LocalMusicSnapshot, resolution: LocalArtistResolution, visual: LocalArtistVisual?): LocalMusicSnapshot {
        val uri = resolution.artistUri
        if (snapshot.visuals.any { it.artistUri == uri && !it.automatic } || snapshot.resolutions.any {
            it.artistUri == uri && it.status in listOf(LocalResolutionStatus.MANUAL, LocalResolutionStatus.REJECTED)
        }) return snapshot
        val tracks = LocalFolderPolicy.visible(snapshot).filter { it.artistUri == uri ||
            it.albumArtist.isNotBlank() && localKey("artist", normalizedLocal(it.albumArtist)) == uri }
        if (tracks.isEmpty() || LocalVisualMatching.evidence(tracks) != resolution.evidence) return snapshot
        val previous = snapshot.visuals.firstOrNull { it.artistUri == uri }
        val verified = visual?.takeIf { it.artistUri == uri && resolution.status == LocalResolutionStatus.VERIFIED }
        val contradiction = previous != null && resolution.status != LocalResolutionStatus.PENDING &&
            (verified == null || verified.remoteUri != previous.remoteUri)
        val status = if (contradiction) LocalResolutionStatus.REVIEW_REQUIRED else resolution.status
        val chosen = when {
            contradiction -> previous?.copy(needsReview = true)
            resolution.status == LocalResolutionStatus.PENDING -> previous
            verified != null -> verified.copy(automatic = true, needsReview = false,
                heroFile = verified.heroFile ?: previous?.takeIf { it.remoteUri == verified.remoteUri && it.heroUrl == verified.heroUrl }?.heroFile,
                logoFile = verified.logoFile ?: previous?.takeIf { it.remoteUri == verified.remoteUri && it.logoUrl == verified.logoUrl }?.logoFile)
            else -> null
        }
        return snapshot.copy(resolutions = snapshot.resolutions.filterNot { it.artistUri == uri } +
            resolution.copy(status = status, retryGeneration = snapshot.resolutions.firstOrNull { it.artistUri == uri }?.retryGeneration ?: 0),
            visuals = snapshot.visuals.filterNot { it.artistUri == uri } + listOfNotNull(chosen))
    }
}
