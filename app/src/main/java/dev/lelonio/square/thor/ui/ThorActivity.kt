package dev.lelonio.square.thor.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.lelonio.square.playback.PlaybackService
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.ui.SquareApp
import dev.lelonio.square.thor.input.ThorInputDispatcher
import dev.lelonio.square.thor.input.ThorInputSession
import dev.lelonio.square.thor.input.ThorInteraction

@UnstableApi
class ThorActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var toolsOpen by mutableStateOf(false)
    private val inputSession = ThorInputSession()
    private lateinit var input: ThorInputDispatcher
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CLOSE) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter(ACTION_CLOSE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        input = ThorInputDispatcher(
            session = inputSession,
            displayId = { windowManager.defaultDisplay.displayId },
            dispatch = { super.dispatchKeyEvent(it) },
            back = { onBackPressedDispatcher.onBackPressed() },
        )
        dev.lelonio.square.thor.input.ThorInputRouter.attach(input)
        inputSession.dispatcher = input
        setContent {
            dev.lelonio.square.ui.theme.ChosenTheme {
                dev.lelonio.square.ui.theme.SquareTheme(darkTheme = true) {
                    SquareApp(
                        player = controller,
                        thorInput = inputSession,
                        showExpandedPlayer = false,
                        toolsOpen = toolsOpen,
                        onToolsOpenChange = { toolsOpen = it },
                        onPlay = { tracks, index, _, _, _, positionMs ->
                            playTracks(tracks, index, positionMs)
                        },
                        onEnqueue = ::enqueueTrack,
                        onEnqueueAll = { tracks -> tracks.forEach(::enqueueTrack) },
                    )
                }
            }
        }

        connect()
    }

    private fun connect() {
        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        MediaController.Builder(this, token).buildAsync().apply {
            addListener(
                {
                    controller = get()
                },
                MoreExecutors.directExecutor(),
            )
        }
    }

    private fun playTracks(tracks: List<CatalogTrack>, index: Int, positionMs: Long) {
        val target = controller ?: return
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.uri)
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.name)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setExtras(dev.lelonio.square.ui.catalogIdentityExtras(track).toBundle())
                        .setDurationMs(track.durationMs.takeIf { it > 0 })
                        .setArtworkUri(track.artworkUrl?.let(android.net.Uri::parse))
                        .build(),
                )
                .build()
        }
        if (items.isEmpty()) return
        target.setMediaItems(items, index.coerceIn(items.indices), positionMs)
        target.prepare()
        target.play()
    }

    private fun enqueueTrack(track: CatalogTrack) {
        val target = controller ?: return
        target.addMediaItem(
            MediaItem.Builder()
                .setMediaId(track.uri)
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.name)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setExtras(dev.lelonio.square.ui.catalogIdentityExtras(track).toBundle())
                        .setDurationMs(track.durationMs.takeIf { it > 0 })
                        .setArtworkUri(track.artworkUrl?.let(android.net.Uri::parse))
                        .build(),
                )
                .build(),
        )
        if (target.mediaItemCount == 1) {
            target.prepare()
            target.play()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        input.key(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        input.motion(event) || super.dispatchGenericMotionEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)) {
            ThorInteraction.touch()
            input.stop()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onStop() {
        input.stop()
        super.onStop()
    }

    override fun onDestroy() {
        inputSession.modalWindows.close()
        inputSession.dispatcher = null
        dev.lelonio.square.thor.input.ThorInputRouter.detach(input)
        input.stop()
        inputSession.commands.close()
        unregisterReceiver(closeReceiver)
        controller?.release()
        controller = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ROLE = "dev.lelonio.square.THOR_ROLE"
        const val EXTRA_DISPLAY_ID = "dev.lelonio.square.THOR_DISPLAY_ID"
        const val ROLE_PLAYER = "player"
        const val ROLE_PANEL = "panel"
        const val ACTION_CLOSE = "dev.lelonio.square.THOR_CLOSE"
    }
}
