package dev.lelonio.square.backend.spotify

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a Spotify track is being watched rather than listened to.
 *
 * Process-wide, like [dev.lelonio.square.backend.youtube.YouTubeVideoMode] and
 * for the same reason: the button is in the player and the thing that has to
 * act on it is the playback service, and no session command carries "play the
 * video of this instead".
 *
 * The video is not a second player running beside the music. It replaces what
 * the session is playing, carries the song's own audio, and gives the transport
 * controls something real to control — which is how the official client does it
 * too, and the only arrangement where pause means pause.
 *
 * Deliberately not persisted: video is something turned on for one song.
 */
object SpotifyVideoMode {

    /** What the player was asked to do; read by the service. */
    sealed interface Request {
        data class Watch(val fileId: String, val positionMs: Long) : Request
        data class Listen(val positionMs: Long) : Request

        /**
         * Leave the video and move along the queue.
         *
         * Skipping is about the song, not about the picture: the video is one
         * item and its own player has nowhere to go, while the queue the
         * listener is actually in is held by the engine parked behind it.
         */
        data class Skip(val forward: Boolean) : Request
    }

    private val _requests = MutableSharedFlow<Request>(extraBufferCapacity = 4)
    val requests: SharedFlow<Request> = _requests.asSharedFlow()

    private val _manifest = MutableStateFlow<VideoManifest?>(null)

    /**
     * The manifest of what is being watched.
     *
     * Kept because the picture is not the only thing in it: the thumbnail
     * sheets are what the glow is sampled from, the video's own pixels being
     * unreadable by design.
     */
    val manifest: StateFlow<VideoManifest?> = _manifest.asStateFlow()

    fun setManifest(manifest: VideoManifest?) {
        _manifest.value = manifest
    }

    private val _enabled = MutableStateFlow(false)

    /** True while the video is what the session is playing. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun watch(fileId: String, positionMs: Long) {
        _requests.tryEmit(Request.Watch(fileId, positionMs))
    }

    fun listen(positionMs: Long) {
        _requests.tryEmit(Request.Listen(positionMs))
    }

    fun skip(forward: Boolean) {
        _requests.tryEmit(Request.Skip(forward))
    }

    private val _generation = MutableStateFlow(0)

    /**
     * Bumped every time a video actually starts.
     *
     * The picture is drawn by attaching a surface to whatever the session is
     * playing, and that attachment is made once per player. Here the player is
     * swapped underneath — the engine cannot show video, so something else has
     * to be in front — and the surface would stay attached to the one that
     * left: sound, and a black rectangle. Screens watch this and attach again.
     */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** Set by the service once the swap has actually happened. */
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (enabled) _generation.value = _generation.value + 1
    }

    /** Back to sound alone, for when the track or the source changes under it. */
    fun reset() {
        _enabled.value = false
    }
}
