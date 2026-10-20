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
import dev.lelonio.square.thor.input.ThorPanel

@UnstableApi
class ThorActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var toolsOpen by mutableStateOf(false)
    private var panel by mutableStateOf(ThorPanel.LYRICS)
    private var scrollRequest by mutableFloatStateOf(0f)
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
            currentPanel = { panel },
            onPanel = { panel = it },
            onSearch = {
                startActivity(
                    android.content.Intent(this, dev.lelonio.square.ui.MainActivity::class.java)
                        .putExtra(dev.lelonio.square.ui.EXTRA_THOR_BYPASS, true),
                )
            },
            onContext = { panel = ThorPanel.EQUALIZER },
            onNavigate = { scrollRequest += it * 140f },
        )
        setContent {
            dev.lelonio.square.ui.theme.ChosenTheme {
                dev.lelonio.square.ui.theme.SquareTheme(darkTheme = true) {
                    SquareApp(
                        player = controller,
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The tools use Compose focus. Let confirmation and directional keys
        // reach their buttons rather than the legacy playback/panel shortcuts.
        if (toolsOpen && event.keyCode in setOf(
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            )
        ) return super.dispatchKeyEvent(event)
        return input.key(event, controller).also {
            if (dev.lelonio.square.BuildConfig.DEBUG) {
                android.util.Log.i(
                    "ThorInput",
                    "display=${windowManager.defaultDisplay.displayId} key=${event.keyCode} " +
                        "action=${event.action} consumed=$it",
                )
            }
        } || super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        input.motion(event).also {
            if (dev.lelonio.square.BuildConfig.DEBUG) {
                android.util.Log.i(
                    "ThorInput",
                    "display=${windowManager.defaultDisplay.displayId} motion action=${event.action} " +
                        "x=${event.x} y=${event.y} consumed=$it",
                )
            }
        } || super.dispatchGenericMotionEvent(event)

    override fun onDestroy() {
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
