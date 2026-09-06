package dev.lelonio.square.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * A cover that moves.
 *
 * Some records have one: a few seconds of the sleeve animating, which the
 * reference plays at the top of the page instead of showing a still. It is a
 * picture, not a video — there is no sound, no controls, and it loops.
 *
 * Its own player rather than the app's. The one in the service is what the
 * listener is playing and is bound to a media session, a notification and the
 * car; handing it a silent loop of album art would replace the song on the lock
 * screen. This one holds no audio focus and is released with the screen.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MotionCover(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            // Explicitly not asking for focus: what is playing keeps playing.
            setAudioAttributes(AudioAttributes.DEFAULT, false)
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = true
            prepare()
        }
    }

    // Stopped while the app is away. A loop nobody is looking at is a radio
    // that costs battery and data for a picture on a screen that is off.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            // Inflated, for the surface type; see the layout's own note. Built
            // in code this is a SurfaceView — a second window under the app's,
            // which does not fade or move with the composition, so a page
            // leaving the screen left its video behind for a moment after
            // everything else had gone.
            val view = android.view.LayoutInflater.from(ctx)
                .inflate(dev.lelonio.square.R.layout.motion_cover, null) as PlayerView
            view.apply {
                useController = false
                // Filling the frame, cropping what does not fit: the header is
                // whatever shape the phone is, and letterboxing a cover leaves
                // two bars of black across the top of the page.
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { it.player = player },
        // Released with the composition rather than left to the view pool: the
        // surface has to go when the page does.
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
