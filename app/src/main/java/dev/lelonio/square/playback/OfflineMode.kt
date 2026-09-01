package dev.lelonio.square.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is playing from the phone rather than from Spotify.
 *
 * An object rather than something injected, for the same reason
 * [dev.lelonio.square.data.RemoteConnect] is one: there is a single engine
 * behind it and the answer is wanted from the service, the player and half the
 * screens.
 *
 * ### Three ways in, and only one of them is about the engine
 *
 * - [Reason.MANUAL] — the listener asked for it in Settings. Beats everything.
 * - [Reason.NO_SESSION] — the handshake could not be made and the engine came
 *   up with no Spirc at all. This is the real one: nothing can be streamed
 *   because there is nothing to stream from.
 * - [Reason.SLOW] — the connection is there but worse than the copies on the
 *   phone. Deliberately **does not** tear the session down. Going offline for
 *   real over a bad minute would cost the Connect device and the way back, for
 *   a problem that fixes itself; what this changes is only what the app offers
 *   to play, which is the whole of what the listener asked for. A downloaded
 *   track already plays from disk in every mode — see `load_downloaded_track`
 *   — so the streamed ones are the only ones this decides about.
 */
object OfflineMode {

    enum class Reason { MANUAL, NO_SESSION, SLOW }

    private val _manual = MutableStateFlow(false)
    private val _noSession = MutableStateFlow(false)
    private val _slow = MutableStateFlow(false)

    private val _reason = MutableStateFlow<Reason?>(null)
    private val _active = MutableStateFlow(false)

    /** Why the app is offline, or null when it is not. */
    val reason: StateFlow<Reason?> = _reason.asStateFlow()

    /**
     * Whether only downloaded music should be offered.
     *
     * Its own flow rather than a mapping of [reason], so a row being drawn can
     * read `active.value` without collecting anything: the answer is wanted
     * once per row and it changes a handful of times a day.
     */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun setManual(on: Boolean) {
        if (_manual.value == on) return
        _manual.value = on
        recompute()
    }

    /** Told by the service once the engine has answered for itself. */
    fun setNoSession(on: Boolean) {
        if (_noSession.value == on) return
        _noSession.value = on
        recompute()
    }

    /**
     * Told by the service when the link would stream worse than the phone
     * already holds. See `PlaybackService.watchConnection`.
     */
    fun setSlow(on: Boolean) {
        if (_slow.value == on) return
        _slow.value = on
        recompute()
    }

    private fun recompute() {
        val next = when {
            _manual.value -> Reason.MANUAL
            _noSession.value -> Reason.NO_SESSION
            _slow.value -> Reason.SLOW
            else -> null
        }
        if (_reason.value == next) return
        _reason.value = next
        _active.value = next != null
    }
}
