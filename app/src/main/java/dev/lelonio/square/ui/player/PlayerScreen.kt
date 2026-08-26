package dev.lelonio.square.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withContext
import dev.lelonio.square.backend.spotify.panelAt
import androidx.core.graphics.scale
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import dev.lelonio.square.ui.glass.backdrop.backdrops.layerBackdrop
import dev.lelonio.square.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import dev.lelonio.square.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.lelonio.square.R
import dev.lelonio.square.ui.MainViewModel
import dev.lelonio.square.ui.components.Artwork
import dev.lelonio.square.ui.glass.LiquidButton
import dev.lelonio.square.ui.library.formatDuration
import dev.lelonio.square.ui.theme.softShadow
import com.adamglin.phosphoricons.regular.MonitorPlay
import com.adamglin.phosphoricons.regular.MusicNotes
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.fill.SkipBack
import com.adamglin.phosphoricons.fill.SkipForward
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.Devices
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.Queue
import com.adamglin.phosphoricons.regular.Broadcast
import com.adamglin.phosphoricons.regular.YoutubeLogo
import com.adamglin.phosphoricons.regular.Repeat
import com.adamglin.phosphoricons.regular.RepeatOnce
import com.adamglin.phosphoricons.regular.Shuffle
import kotlin.math.abs

/**
 * How much of the swipe the clip takes, when it stands in for the cover.
 *
 * Damped: the Canvas is the backdrop every piece of glass above it samples, and
 * moving it one to one drags the whole screen's refraction along with it.
 */
private const val CANVAS_SWIPE_FOLLOW = 0.35f

/**
 * How far a Canvas is darkened while playback is paused.
 *
 * Enough to be unmistakable, not so much that the clip stops being visible —
 * this is a paused picture, not a closed one.
 */
private const val PAUSED_DIM = 0.55f

/**
 * The player, as a liquid-glass prototype.
 *
 * This screen deliberately does not follow the monochrome paper look the rest of
 * the app uses — it is here to be judged on a device before deciding whether to
 * take the whole interface this way. The two are opposites: the paper design
 * builds hierarchy out of ink and soft shadow on an opaque page, and glass
 * builds it out of depth over something vivid. Glass over a near-white page has
 * nothing to refract and looks like grey panels, which is why the artwork comes
 * back here as a full-bleed backdrop.
 */
/** What occupies the middle of the player. */
/** How long each half of a change between the cover and a panel takes. */
private const val STAGE_FADE_MS = 180

/** And the whole of a change between the cover and a Canvas; see the use of it. */
private const val CLIP_FADE_MS = 460

private enum class Stage {
    COVER, LYRICS, EFFECTS, INFO, QUEUE, DEVICES, ADD_TO_PLAYLIST, CANVAS, VIDEO
}

@UnstableApi
@Composable
fun PlayerScreen(
    state: PlaybackState,
    /** Read only inside the waveform, to keep position updates off the rest. */
    positionMs: State<Long>,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    queue: List<QueueEntry>,
    lyrics: dev.lelonio.square.data.Lyrics?,
    lyricsLoading: Boolean,
    /** Who made the track, for the credits panel; null until it is asked for. */
    credits: dev.lelonio.square.backend.spotify.SpotifyCredits.Credits?,
    creditsLoading: Boolean,
    /** Asked for when the credits panel is opened, and not before. */
    onWantCredits: (String?) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    /** The music video for this track, when the catalogue has one. */
    videoFileId: String? = null,
    /** Whether the listener has asked to watch rather than listen. */
    videoMode: Boolean = false,
    onToggleVideo: () -> Unit = {},
    reverb: Float,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onReverb: (Float) -> Unit,
    presets: List<dev.lelonio.square.playback.EffectPreset>,
    onApplyPreset: (dev.lelonio.square.playback.EffectPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    /**
     * The app-wide backdrop, shared rather than built here: this screen covers
     * the same blurred artwork the rest of the app already draws, and recording
     * a second copy of it would be two full-screen blurs for one image.
     */
    backdrop: Backdrop,
    /** The track's Canvas clip, or null when it has none. */
    canvas: dev.lelonio.square.data.CanvasClip?,
    /** See MiniPlayer: the cover is shared with the bar this screen grew out of. */
    sharedScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    /** The Spotify Connect device list, and what to do with it. */
    devices: dev.lelonio.square.ui.MainViewModel.DevicesState,
    onOpenDevices: () -> Unit,
    onCloseDevices: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    /**
     * Opens the "add to playlist" sheet.
     *
     * The sheet itself is drawn by the app rather than by this screen: the track
     * rows in the library open the same one, and two copies would be two states
     * of one thing.
     */
    onAddToPlaylist: () -> Unit,
    /**
     * Opens an artist, a playlist or an album by uri, with a name to show while
     * it loads. The player closes itself first; see the two callers.
     */
    onOpenUri: (String, String) -> Unit = { _, _ -> },
    /**
     * Whether this track is already in one of the account's playlists.
     *
     * Only ever true when it is known to be; see MainViewModel.inPlaylists. A
     * button that says "add" over a track that is already there is a small lie,
     * and the answer is cheap for anything the app has already read.
     */
    alreadySaved: Boolean = false,
    /**
     * Whether it is in Liked Songs, which is a different answer and a different
     * mark: a heart, the way every Spotify client says it.
     */
    inLikedSongs: Boolean = false,
    /** Starts a station from the current track; null where there is none. */
    onRadio: (() -> Unit)? = null,
    /** The playlist picker's state, shown in the panel rather than as a sheet. */
    addToPlaylist: MainViewModel.AddToPlaylistState,
    onPickPlaylist: (dev.lelonio.square.data.CatalogPlaylist) -> Unit,
    /**
     * False when the source has no writable playlists.
     *
     * Adding a track to a playlist goes through the Spotify Web API; on another
     * source the button could only open a picker that fails on every choice.
     */
    playlistEditAvailable: Boolean = true,
    /**
     * Turns the current track's video on or off, or null when it has none.
     *
     * The same track either way — the source reopens on the muxed stream, which
     * carries the picture along with the sound — so the queue, the position and
     * everything else stay as they were.
     */
    onWatchVideo: (() -> Unit)? = null,
    /** Showing the picture right now. */
    videoOn: Boolean = false,
    /** The player to hang the video surface off; null when there is no video. */
    videoPlayer: Player? = null,
    /** Changes when the session starts playing a new video; see VideoStage. */
    videoAttachKey: Any? = null,
    /**
     * False when the source is not Spotify.
     *
     * Connect is Spotify's own protocol for handing playback to another
     * speaker; on any other source the button would open a list that can only
     * ever be empty, so it is not drawn at all.
     */
    connectAvailable: Boolean = true,
    /** Whether the sound is coming out of another of the account's devices. */
    onAnotherDevice: Boolean = false,
) {
    var panel by remember { mutableStateOf(PlayerPanel.NONE) }

    /** Bumped to open the karaoke control; see KaraokeDial. */
    var karaokeExpand by remember { androidx.compose.runtime.mutableIntStateOf(0) }



    // The one panel whose contents are fetched rather than already here, and
    // the one nobody opens for most songs. Asking on open keeps a request per
    // track from being made for a page most listeners never see.
    LaunchedEffect(panel, state.mediaId) {
        if (panel == PlayerPanel.INFO) onWantCredits(state.mediaId)
    }

    // How far the track-change swipe has been dragged, when the clip stands in
    // for the cover. Written from the gesture and read only inside a
    // graphicsLayer, so following the finger costs a redraw of one layer rather
    // than a recomposition of the player.
    // No Canvas behind a video.
    //
    // The Canvas is the loop that stands in for a picture when there is none;
    // with the video playing there is one, and two moving images arguing over
    // the same screen is what the official client does not do either.
    @Suppress("NAME_SHADOWING")
    val canvas = canvas?.takeIf { !videoOn }

    val canvasShift = remember { mutableFloatStateOf(0f) }

    // Whether the clip has put a frame on screen yet.
    //
    // A Canvas is known about — the URL arrives with the track — well before it
    // has decoded anything, and in between the surface is empty. Opening the
    // player onto that gap showed a blank middle where the cover belongs, so
    // the cover stays until this turns true and the clip fades in over it.
    // Reset per track: the next one starts from nothing again.
    var canvasReady by remember(canvas?.url) { mutableStateOf(false) }

    // The Canvas gets a layer of its own so the glass over it refracts the clip
    // rather than the app's blurred artwork. It is combined with the app
    // backdrop rather than replacing it, because with no Canvas this layer is
    // empty and the panes would have nothing to sample.
    val canvasBackdrop = rememberLayerBackdrop()
    val glassBackdrop = rememberCombinedBackdrop(backdrop, canvasBackdrop)

    // Lyrics take over the middle of the screen rather than opening a panel at
    // the bottom, and the Canvas goes out of focus behind them: a clip is
    // motion, and reading over motion is the one thing that does not work. Blur
    // keeps it present as light and colour without competing for attention.
    // Any panel, not just the lyrics: they all sit in the middle of the screen
    // now, and reading a slider over a moving clip is no easier than reading a
    // lyric over one.
    val panelOpen = panel != PlayerPanel.NONE
    val canvasBlur by animateDpAsState(
        targetValue = if (panelOpen) 26.dp else 0.dp,
        animationSpec = tween(320),
        label = "canvasBlur",
    )

    // The video's own light, sampled by the stage below and spread over the
    // whole screen from here — the way an ambient television lights the wall
    // behind it rather than just its own frame. Null whenever no video is
    // showing, which is what puts the ordinary backdrop back.
    var ambient by remember { mutableStateOf<AmbientEdges?>(null) }
    LaunchedEffect(videoOn) { if (!videoOn) ambient = null }

    val auraColors by dev.lelonio.square.ui.theme.rememberArtworkPalette(
        state.artworkUrl.takeIf { canvas == null },
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (canvasBlur > 0.dp) Modifier.blur(canvasBlur) else Modifier)
                // The clip follows a track-change swipe, since with a Canvas
                // playing it is the only thing on screen the gesture could be
                // about. Damped, because the clip is the backdrop of everything
                // above it and moving it one to one drags the whole screen's
                // refraction with it.
                .graphicsLayer {
                    val shift = canvasShift.floatValue
                    if (shift == 0f) return@graphicsLayer
                    translationX = shift * CANVAS_SWIPE_FOLLOW
                    val travel = (abs(shift) / size.width).coerceIn(0f, 1f)
                    val shrink = 1f - travel * 0.12f
                    scaleX = shrink
                    scaleY = shrink
                    alpha = 1f - travel * 0.5f
                }
                .layerBackdrop(canvasBackdrop),
        ) {
            // Inside the recorded layer, and that is the point: the glass above
            // samples this backdrop, so light drawn here is light the buttons
            // and the panels refract. Drawn outside it they sat on top of the
            // colour without ever picking any of it up.
            AmbientLight(ambient)

            // The stand-in for a Canvas, for the tracks that have none, and
            // only when it has been asked for. In the same layer as the light
            // above and for the same reason.
            // The stand-in is for a screen with nothing moving on it. With a
            // video playing there is something, and the glow above is taken
            // from its own frames: drawing the cover's aura over that would
            // paint the song's colours on top of the video's.
            if (canvas == null && !videoOn) {
                CoverAura(colors = auraColors, playing = state.isPlaying)
            }


            // Held at zero until the first frame, then faded up. Without this
            // the clip's own surface appears the instant it decodes, which next
            // to a cover crossfading out reads as two separate events.
            val clipAlpha by animateFloatAsState(
                targetValue = if (canvasReady) 1f else 0f,
                animationSpec = tween(420),
                label = "clipAlpha",
            )

            Crossfade(
                targetState = canvas,
                animationSpec = tween(500),
                label = "canvas",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (canvas?.isVideo == true) clipAlpha else 1f },
            ) { clip ->
                when {
                    clip == null -> Unit

                    // The video only while the player is at rest.
                    //
                    // A TextureView inside a layer that is being scaled and
                    // faded — which is exactly what the closing animation does —
                    // goes black for the length of the travel: its surface is
                    // detached and re-attached around the layer change, and
                    // there is nothing to draw in between. Standing in the
                    // artwork for those few hundred milliseconds is invisible;
                    // a black rectangle sliding down the screen is not.
                    clip.isVideo && LocalGlassEnabled.current -> CanvasSurface(
                        url = clip.url,
                        isPlaying = state.isPlaying,
                        onFirstFrame = { canvasReady = true },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Nothing at all, rather than the cover: what shows through
                    // is the app's own blurred artwork, which is the same
                    // picture out of focus and reads as the clip softening for a
                    // moment. Dropping a sharp cover in for the length of the
                    // animation was worse than the black rectangle it fixed.
                    clip.isVideo -> Unit

                    // A handful of canvases are stills rather than clips.
                    else -> AsyncImage(
                        model = clip.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Only over a Canvas, and deliberately light. Clips are graded for
            // their own sake and some are near-white; this buys the controls
            // enough contrast without turning the video into a mood board. With
            // no Canvas there is nothing to darken — the app's backdrop is
            // already dimmed.
            //
            // It deepens on pause. A stilled clip is a frozen picture that looks
            // no different from a playing one held on a slow shot, so pausing
            // read as the video having stalled rather than as playback having
            // stopped. Dimming it says the same thing the picture cannot.
            if (canvas != null) {
                // Darkened for a paused track and for an open panel alike. Both
                // are the same statement — the clip is not what you are looking
                // at right now — and the blur alone left a bright moving picture
                // behind a column of text.
                val dim by animateFloatAsState(
                    targetValue = if (state.isPlaying && !panelOpen) 0f else 1f,
                    animationSpec = tween(420),
                    label = "canvasDim",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Black.copy(alpha = 0.52f),
                                ),
                            ),
                        )
                        .drawWithContent {
                            drawContent()
                            drawRect(Color.Black, alpha = dim * PAUSED_DIM)
                        },
                )
            }
        }

        CompositionLocalProvider(LocalContentColor provides GlassInk) {
            DismissibleScreen(onDismiss = onCollapse, modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 20.dp),
                ) {
                    TopBar(
                        backdrop = glassBackdrop,
                        panel = panel,
                        source = state.source,
                        onOpenSource = state.contextUri?.let { uri ->
                            {
                                onCollapse()
                                onOpenUri(uri, state.source.substringAfterLast("· ").trim())
                            }
                        },
                        onCollapse = onCollapse,
                        onOpenDevices = {
                            panel = if (panel == PlayerPanel.DEVICES) {
                                PlayerPanel.NONE
                            } else {
                                onOpenDevices()
                                PlayerPanel.DEVICES
                            }
                        },
                        connectAvailable = connectAvailable,
                        onAnotherDevice = onAnotherDevice,
                        onWatchVideo = onWatchVideo,
                        videoOn = videoOn,
                    )

                    // Everything sits at the bottom, as in the reference: the
                    // artwork behind is the subject, and the controls are a
                    // stack of panes laid over its lower third rather than a
                    // screen of their own.
                    // Not scrollable: a weighted spacer inside a scrolling
                    // column measures against an infinite height. The panels
                    // below cap their own height and scroll internally, so the
                    // screen never needs to.
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // With a Canvas the clip *is* the picture and a cover on
                        // top would compete with it. Without one, the cover
                        // fills the space above the controls — the shadow that
                        // settles on pause is the only motion tying this screen
                        // to the transport, and losing it left a still page.
                        //
                        // `weight(1f)`, not `weight(1f, fill = false)`: with the
                        // column arranged from the bottom, "as much space as it
                        // wants" is nothing once the controls have taken theirs.
                        // One slot, cross-faded, rather than two AnimatedVisibility
                        // blocks taking turns.
                        //
                        // Both of those carried a weight, so while one was
                        // leaving and the other arriving the column briefly had
                        // two weighted children and split the space between
                        // them — the cover shrank sideways as it left instead of
                        // simply going. A plain fade is what opening a lyric
                        // sheet should look like, and holding the slot open
                        // keeps everything below it still.
                        // The cover is drawn here rather than as one of the
                        // states below.
                        //
                        // As a state it could not be made to dissolve: whichever
                        // container held it — Crossfade first, then
                        // AnimatedContent — dropped it from the composition in
                        // the same frame the panel arrived, so there was nothing
                        // left to fade. The panels go on swapping between
                        // themselves; the cover is simply the floor of this
                        // slot, and opening a panel takes its opacity away.
                        val coverShowing = panel == PlayerPanel.NONE &&
                            !(videoOn && videoPlayer != null && LocalGlassEnabled.current) &&
                            (canvas == null || !canvasReady)
                        // One at a time, from one clock.
                        //
                        // Faded together, the cover and the panel are both half
                        // visible through the middle of the change: lyrics
                        // printed over a record sleeve, which reads as a mistake
                        // rather than as a transition. Two animations with the
                        // same duration and opposite delays were still two
                        // clocks, and they overlapped by however far apart they
                        // started. This is a single number: the first half of it
                        // takes the cover away, the second half brings the panel
                        // in, and neither half can begin before the other ends.
                        // Slower when the cover is handing over to a clip.
                        //
                        // With a panel it is a change the listener asked for and
                        // wants over with. A Canvas arriving is not: the clip
                        // fades up behind the glass over four hundred
                        // milliseconds, and a cover that left in a hundred and
                        // eighty made the two look like separate events instead
                        // of one dissolve.
                        val toClip = panel == PlayerPanel.NONE
                        val phase by animateFloatAsState(
                            targetValue = if (coverShowing) 0f else 1f,
                            animationSpec = tween(
                                if (toClip) CLIP_FADE_MS else STAGE_FADE_MS * 2,
                            ),
                            label = "stageFade",
                        )
                        val coverAlpha = (1f - phase * 2f).coerceIn(0f, 1f)
                        val panelAlpha = ((phase - 0.5f) * 2f).coerceIn(0f, 1f)

                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            // Says so in words, over whatever the slot is
                            // showing.
                            //
                            // The lit microphone says it to whoever is looking
                            // at the control; this says it to whoever comes
                            // back to a song that sounds wrong and does not
                            // remember why. It rides above the cover rather
                            // than beside the title, where the transport
                            // already has all the room it needs.
                            val karaokeAmount by dev.lelonio.square.playback.AudioEffects.karaoke
                                .collectAsStateWithLifecycle()
                            androidx.compose.animation.AnimatedVisibility(
                                visible = karaokeAmount > 0f,
                                enter = fadeIn(tween(220)),
                                exit = fadeOut(tween(180)),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .then(androidx.compose.ui.Modifier)

                            ) {
                                KaraokeBadge(karaokeAmount, glassBackdrop) {
                                    panel = PlayerPanel.LYRICS
                                    karaokeExpand++
                                }
                            }

                            if (coverAlpha > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = coverAlpha },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Cover(
                                        state,
                                        // Always as if nothing were open: the
                                        // cover shrinks towards the size it
                                        // takes beside a panel, and doing that
                                        // while it fades is two movements where
                                        // one was asked for. It is only ever
                                        // seen at full size now.
                                        PlayerPanel.NONE,
                                        onNext,
                                        onPrevious,
                                        // Only for the journey the bar's
                                        // thumbnail makes into this cover.
                                        sharedScope.takeIf { coverShowing },
                                        animatedScope.takeIf { coverShowing },
                                    )
                                }
                            }

                        AnimatedContent(
                            targetState = when (panel) {
                                PlayerPanel.LYRICS -> Stage.LYRICS
                                PlayerPanel.EFFECTS -> Stage.EFFECTS
                                PlayerPanel.INFO -> Stage.INFO
                                PlayerPanel.QUEUE -> Stage.QUEUE
                                PlayerPanel.DEVICES -> Stage.DEVICES
                                PlayerPanel.ADD_TO_PLAYLIST -> Stage.ADD_TO_PLAYLIST
                                PlayerPanel.NONE -> when {
                                    // Not while the player is travelling: a
                                    // TextureView inside a layer being scaled
                                    // and faded loses its surface for the length
                                    // of the animation and leaves a black
                                    // rectangle sliding down the screen. The
                                    // cover stands in for those few hundred
                                    // milliseconds — the same trick the Canvas
                                    // above uses, and for the same reason.
                                    videoOn && videoPlayer != null &&
                                        LocalGlassEnabled.current -> Stage.VIDEO
                                    canvas == null || !canvasReady -> Stage.COVER
                                    else -> Stage.CANVAS
                                }
                            },
                            // Spelled out rather than left to Crossfade.
                            //
                            // Crossfade dropped the cover the instant a panel
                            // opened — the log had it leaving the composition in
                            // the same millisecond the lyrics arrived, while the
                            // panels dissolved into each other perfectly. Said
                            // this way, both halves of the change are named and
                            // both are given the same time.
                            // Between one panel and another, where no cover
                            // is involved, this is the whole animation. Going to
                            // or from the cover it is held at nothing until its
                            // turn; see panelAlpha.
                            transitionSpec = {
                                fadeIn(tween(STAGE_FADE_MS)) togetherWith
                                    fadeOut(tween(STAGE_FADE_MS)) using null
                            },
                            label = "stage",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = panelAlpha },
                        ) { stage ->
                            when (stage) {
                                // Empty: the cover above this AnimatedContent
                                // is what fills the slot when no panel is open.
                                Stage.COVER -> Box(Modifier.fillMaxSize())

                                Stage.VIDEO -> Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Nullable even in this stage: the crossfade
                                    // keeps drawing the one it is leaving, and
                                    // the controller it holds is gone the moment
                                    // the activity stops.
                                    videoPlayer?.let {
                                        VideoStage(
                                            it,
                                            videoAttachKey,
                                            protectedContent = videoMode,
                                        ) { ambient = it }
                                    }
                                }

                                Stage.LYRICS -> LyricsStage(
                                    lyrics = lyrics,
                                    loading = lyricsLoading,
                                    positionMs = positionMs,
                                    isPlaying = state.isPlaying,
                                    onSeek = onSeek,
                                    backdrop = glassBackdrop,
                                    expandSignal = karaokeExpand,
                                )

                                // Effects and the queue share the lyrics' space
                                // rather than opening a sheet under the
                                // controls. A panel that pushed the transport
                                // around every time it opened was the reason
                                // this screen never sat still.
                                Stage.INFO -> CreditsView(
                                    credits = credits,
                                    loading = creditsLoading,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                Stage.EFFECTS -> Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EffectsPanel(
                                        speed = state.speed,
                                        pitch = state.pitch,
                                        reverb = reverb,
                                        onSpeed = onSpeed,
                                        onPitch = onPitch,
                                        onReverb = onReverb,
                                        presets = presets,
                                        onApplyPreset = onApplyPreset,
                                        onSavePreset = onSavePreset,
                                        onDeletePreset = onDeletePreset,
                                        backdrop = glassBackdrop,
                                    )
                                }

                                Stage.QUEUE -> Box(Modifier.fillMaxSize()) {
                                    QueueList(queue, onPlayQueueItem)
                                }

                                Stage.DEVICES -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    DeviceList(
                                        state = devices,
                                        onSelect = onSelectDevice,
                                        onRefresh = onRefreshDevices,
                                    )
                                }

                                Stage.ADD_TO_PLAYLIST -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    PlaylistPicker(
                                        state = addToPlaylist,
                                        onSelect = onPickPlaylist,
                                    )
                                }

                                // The clip is the picture, and it is drawn
                                // behind everything: this slot only has to keep
                                // the space.
                                // With a clip playing there is no cover to
                                // swipe, and the track-change gesture went with
                                // it. The slot keeps the same drag handler over
                                // the clip — nothing moves, because the clip is
                                // drawn behind this and is not ours to shift,
                                // but the swipe still changes track.
                                Stage.CANVAS -> CoverGestures(
                                    onNext = onNext,
                                    onPrevious = onPrevious,
                                    canGoNext = state.hasNext,
                                    canGoPrevious = state.hasPrevious,
                                    followFinger = false,
                                    onDrag = { canvasShift.floatValue = it },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Only for the few tracks that have a video, and above
                        // the title because that is where the official client
                        // puts it: it is a choice about this song, not about
                        // the app.
                        // Faded and grown in, not dropped in.
                        //
                        // Whether a track has a video is answered a moment
                        // after it starts, so this button arrives while the
                        // listener is already looking at the screen. Appearing
                        // in one frame reads as a glitch; arriving reads as an
                        // answer.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = videoFileId != null,
                            enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.9f) +
                                expandVertically(tween(260)),
                            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f) +
                                shrinkVertically(tween(180)),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            GlassSurface(
                                backdrop = glassBackdrop,
                                surfaceColor = GlassFilm,
                                shape = RoundedCornerShape(50),
                                // The gap to the title lives here rather than
                                // in a spacer beside it: what a visibility
                                // block holds is stacked in a box, so a spacer
                                // next to the button sat *on* it and the button
                                // ended up against the title.
                                modifier = Modifier
                                    .padding(bottom = 14.dp)
                                    .pressable(onClick = onToggleVideo),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // The icon says what the button leads to,
                                    // like the words beside it: a screen while
                                    // listening, a note while watching.
                                    Icon(
                                        if (videoMode) PhosphorIcons.Regular.MusicNotes
                                        else PhosphorIcons.Regular.MonitorPlay,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(18.dp),
                                    )
                                    Text(
                                        stringResource(
                                            if (videoMode) R.string.switch_to_audio
                                            else R.string.switch_to_video,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        // Title and artist on their own capsule, with the two
                        // per-track actions on the right.
                        GlassSurface(
                            backdrop = glassBackdrop,
                            surfaceColor = GlassFilm,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .sharedPill(sharedScope, animatedScope),
                        ) {
                            Row(
                                Modifier.padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TitleBlock(
                                    state,
                                    Modifier.weight(1f),
                                    onOpenArtist = { uri, name ->
                                        onCollapse()
                                        onOpenUri(uri, name)
                                    },
                                )
                                // A station built from this track, the way the
                                // official client's own radio button does it.
                                if (onRadio != null) {
                                    RoundGlassButton(
                                        backdrop = glassBackdrop,
                                        size = 40.dp,
                                        onClick = onRadio,
                                    ) {
                                        Icon(
                                            PhosphorIcons.Regular.Broadcast,
                                            contentDescription = stringResource(R.string.radio),
                                            tint = panelTint(false),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Spacer(Modifier.size(8.dp))
                                }
                                // The queue's one control. It is a sheet that
                                // opens over the player rather than a view of
                                // it, which is why it sits here and not in the
                                // segmented switch below.
                                RoundGlassButton(
                                    backdrop = glassBackdrop,
                                    size = 40.dp,
                                    onClick = {
                                        panel = if (panel == PlayerPanel.QUEUE) {
                                            PlayerPanel.NONE
                                        } else {
                                            PlayerPanel.QUEUE
                                        }
                                    },
                                ) {
                                    Icon(
                                        PhosphorIcons.Regular.Queue,
                                        contentDescription = stringResource(R.string.queue),
                                        // Coloured while its panel is the one
                                        // open: these buttons stay on screen
                                        // with the panel showing, and nothing
                                        // else said which of them put it there.
                                        tint = panelTint(panel == PlayerPanel.QUEUE),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                if (playlistEditAvailable) {
                                Spacer(Modifier.size(8.dp))
                                RoundGlassButton(
                                    backdrop = glassBackdrop,
                                    size = 40.dp,
                                    onClick = {
                                        panel = if (panel == PlayerPanel.ADD_TO_PLAYLIST) {
                                            PlayerPanel.NONE
                                        } else {
                                            onAddToPlaylist()
                                            PlayerPanel.ADD_TO_PLAYLIST
                                        }
                                    },
                                ) {
                                    Icon(
                                        when {
                                            inLikedSongs -> PhosphorIcons.Fill.Heart
                                            alreadySaved -> PhosphorIcons.Regular.Check
                                            else -> PhosphorIcons.Regular.Plus
                                        },
                                        contentDescription = stringResource(
                                            when {
                                                inLikedSongs -> R.string.liked_songs
                                                alreadySaved -> R.string.in_a_playlist
                                                else -> R.string.add_to_playlist
                                            },
                                        ),
                                        tint = when {
                                            panel == PlayerPanel.ADD_TO_PLAYLIST ->
                                                panelTint(true)
                                            inLikedSongs || alreadySaved -> SavedInk
                                            else -> panelTint(false)
                                        },
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        GlassProgressBar(
                            positionMs = positionMs,
                            durationMs = state.durationMs,
                            onSeek = onSeek,
                            accentColor = GlassInk,
                            trackColor = GlassInk.copy(alpha = 0.28f),
                        )
                        TimeRow(positionMs, state.durationMs)

                        Spacer(Modifier.height(14.dp))

                        Controls(
                            state = state,
                            backdrop = glassBackdrop,
                            onTogglePlay = onTogglePlay,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                        )

                        Spacer(Modifier.height(14.dp))

                        PlayerPanelSection(
                            panel = panel,
                            onSelect = { panel = it },
                            queue = queue,
                            lyrics = lyrics,
                            lyricsLoading = lyricsLoading,
                            positionMs = positionMs,
                            onPlayQueueItem = onPlayQueueItem,
                            onSeek = onSeek,
                            speed = state.speed,
                            pitch = state.pitch,
                            reverb = reverb,
                            onSpeed = onSpeed,
                            onPitch = onPitch,
                            onReverb = onReverb,
                            presets = presets,
                            onApplyPreset = onApplyPreset,
                            onSavePreset = onSavePreset,
                            onDeletePreset = onDeletePreset,
                            backdrop = glassBackdrop,
                        )

                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }

    }
}

/**
 * The video, on a surface the player draws straight into.
 *
 * A `SurfaceView` rather than a `TextureView`: the player writes to it without
 * the frame ever passing through the view hierarchy, which is what keeps a
 * 720p stream from costing anything on the UI thread. The surface is handed
 * back on the way out, or the player would go on rendering into a dead one.
 */
@Composable
private fun VideoStage(
    player: Player,
    attachKey: Any?,
    /**
     * True for content the device decrypts in a protected buffer.
     *
     * Which decides the surface, and takes the ambient glow away with it: a
     * texture is the only surface whose pixels can be read back, and pixels
     * that can be read back are exactly what protected playback does not
     * allow. Drawn into one, a Widevine video shows nothing at all — the
     * decoder refuses the surface rather than the licence. So this gets the
     * plain surface the floating window uses, and the glow falls back to the
     * cover's colours, which is the honest best available.
     */
    protectedContent: Boolean,
    onAmbient: (AmbientEdges) -> Unit,
) {
    if (protectedContent) {
        VideoSurface(
            player,
            attachKey,
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp)),
        )
        PanelAmbient(player, onAmbient)
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    // A TextureView rather than the SurfaceView the floating window uses: only
    // a texture can be read back, and reading the picture back is the whole
    // ambient effect — the glow is the video's own colour, not a guess made
    // from the cover.
    val texture = remember(context) { android.view.TextureView(context) }
    // `attachKey` as well as the player: the session can change what it is
    // playing on without changing the handle this screen holds, and the
    // picture goes to whoever the surface was last given to.
    DisposableEffect(player, texture, attachKey) {
        player.setVideoTextureView(texture)
        onDispose { player.clearVideoTextureView(texture) }
    }

    val report by rememberUpdatedState(onAmbient)
    LaunchedEffect(texture, player) {
        while (true) {
            // Slowly, off a thumbnail. Reading the whole picture back at frame
            // rate would cost far more than it shows, and a glow that tracked
            // every cut would strobe: this is meant to be light in the room, and
            // light in a room does not flicker.
            kotlinx.coroutines.delay(AMBIENT_INTERVAL_MS)
            if (!texture.isAvailable) continue
            val frame = runCatching {
                texture.getBitmap(AMBIENT_SAMPLE, AMBIENT_SAMPLE)
            }.getOrNull() ?: continue
            report(frame.edges())
            frame.recycle()
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { texture },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp)),
    )
}

/**
 * The sampled colour, over the whole screen.
 *
 * Each corner eased into its new value over seconds rather than frames: the
 * point is the colour of the scene, and a scene lasts. Drawn under everything,
 * so the glass above it refracts the light the same way it refracts artwork.
 */
@Composable
private fun AmbientLight(edges: AmbientEdges?) {
    val topLeft by animateColorAsState(
        edges?.topLeft ?: Color.Transparent, tween(AMBIENT_FADE_MS), label = "tl",
    )
    val topRight by animateColorAsState(
        edges?.topRight ?: Color.Transparent, tween(AMBIENT_FADE_MS), label = "tr",
    )
    val bottomLeft by animateColorAsState(
        edges?.bottomLeft ?: Color.Transparent, tween(AMBIENT_FADE_MS), label = "bl",
    )
    val bottomRight by animateColorAsState(
        edges?.bottomRight ?: Color.Transparent, tween(AMBIENT_FADE_MS), label = "br",
    )

    Canvas(Modifier.fillMaxSize()) {
        // One soft glow per corner rather than two bands across the screen.
        //
        // Bands meet along a line, and a line is exactly what light does not
        // have: the first version left a visible seam across the middle of the
        // screen. Radial gradients reaching past each other have no edge to
        // show, which is also what the lamps behind an ambient television
        // actually do.
        val radius = size.maxDimension * 0.9f
        listOf(
            topLeft to androidx.compose.ui.geometry.Offset(0f, 0f),
            topRight to androidx.compose.ui.geometry.Offset(size.width, 0f),
            bottomLeft to androidx.compose.ui.geometry.Offset(0f, size.height),
            bottomRight to androidx.compose.ui.geometry.Offset(size.width, size.height),
        ).forEach { (color, center) ->
            drawRect(
                Brush.radialGradient(
                    listOf(color, color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
            )
        }
    }
}

/** The colour of each corner of a frame, which is what the glow is made of. */
private data class AmbientEdges(
    val topLeft: Color,
    val topRight: Color,
    val bottomLeft: Color,
    val bottomRight: Color,
)

/**
 * Averages each quadrant of the thumbnail.
 *
 * Quadrants rather than single pixels: one pixel of a dark scene with a bright
 * speck in it would swing the whole glow, and the average is what a wall
 * actually reflects.
 */
private fun android.graphics.Bitmap.edges(): AmbientEdges {
    val half = AMBIENT_SAMPLE / 2
    fun quadrant(x0: Int, y0: Int): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        for (x in x0 until x0 + half) {
            for (y in y0 until y0 + half) {
                val pixel = getPixel(x, y)
                r += android.graphics.Color.red(pixel)
                g += android.graphics.Color.green(pixel)
                b += android.graphics.Color.blue(pixel)
            }
        }
        val count = (half * half).toFloat()
        return Color(r / count / 255f, g / count / 255f, b / count / 255f)
    }
    return AmbientEdges(
        topLeft = quadrant(0, 0),
        topRight = quadrant(half, 0),
        bottomLeft = quadrant(0, half),
        bottomRight = quadrant(half, half),
    )
}

/** How often the picture is read back, in milliseconds. */
private const val AMBIENT_INTERVAL_MS = 900L

/** How long a colour takes to become the next one. */
private const val AMBIENT_FADE_MS = 2500

/** Side of the thumbnail each sample is taken from. */
private const val AMBIENT_SAMPLE = 16

/**
 * The glow for a video whose own pixels cannot be read.
 *
 * Sampled from the thumbnail sheets the scrubber uses — ordinary JPEGs, one
 * frame a second — because protected playback decrypts into a buffer nothing
 * can read back. Same effect, same pictures, arrived at from the outside.
 *
 * One small fetch every couple of seconds, and the sheet holds thirty-six
 * frames, so most of those are already in the cache the loader keeps.
 */
@Composable
private fun PanelAmbient(player: Player, onAmbient: (AmbientEdges) -> Unit) {
    val manifest by dev.lelonio.square.backend.spotify.SpotifyVideoMode.manifest
        .collectAsStateWithLifecycle()
    val report by rememberUpdatedState(onAmbient)
    val http = remember { okhttp3.OkHttpClient() }

    LaunchedEffect(manifest) {
        val current = manifest ?: return@LaunchedEffect
        var lastUrl: String? = null
        var sheet: android.graphics.Bitmap? = null
        while (true) {
            val at = withContext(kotlinx.coroutines.Dispatchers.Main) { player.currentPosition }
            val panel = current.panelAt(at)
            if (panel == null) {
                kotlinx.coroutines.delay(AMBIENT_INTERVAL_MS)
                continue
            }
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    if (panel.url != lastUrl) {
                        val request = okhttp3.Request.Builder().url(panel.url).build()
                        val bytes = http.newCall(request).execute().use { it.body?.bytes() }
                        sheet?.recycle()
                        sheet = bytes?.let {
                            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                        }
                        lastUrl = panel.url
                    }
                    val whole = sheet ?: return@runCatching
                    // The one frame out of the sheet, small: the glow is four
                    // averaged edges, and averaging is cheaper on few pixels.
                    val frame = android.graphics.Bitmap.createBitmap(
                        whole,
                        panel.left.coerceIn(0, (whole.width - 1).coerceAtLeast(0)),
                        panel.top.coerceIn(0, (whole.height - 1).coerceAtLeast(0)),
                        panel.width.coerceAtMost(whole.width - panel.left),
                        panel.height.coerceAtMost(whole.height - panel.top),
                    )
                    val small = frame.scale(AMBIENT_SAMPLE, AMBIENT_SAMPLE)
                    frame.recycle()
                    val edges = small.edges()
                    small.recycle()
                    withContext(kotlinx.coroutines.Dispatchers.Main) { report(edges) }
                }
            }
            kotlinx.coroutines.delay(AMBIENT_INTERVAL_MS)
        }
    }
}

/** The surface itself, shared with the floating window; see [VideoStage]. */
@Composable
fun VideoSurface(player: Player, attachKey: Any? = null, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val surface = remember(context) { android.view.SurfaceView(context) }
    DisposableEffect(player, surface, attachKey) {
        player.setVideoSurfaceView(surface)
        onDispose { player.clearVideoSurfaceView(surface) }
    }
    androidx.compose.ui.viewinterop.AndroidView(factory = { surface }, modifier = modifier)
}

@Composable
private fun TopBar(
    backdrop: Backdrop,
    panel: PlayerPanel,
    /** Where the queue came from: "Playlist · Estate 2025", "Ricerca". */
    source: String,
    /** Opens that place, when it is one; null leaves the line as a caption. */
    onOpenSource: (() -> Unit)?,
    onCollapse: () -> Unit,
    onOpenDevices: () -> Unit,
    connectAvailable: Boolean,
    onAnotherDevice: Boolean,
    /** Null when the track has no video to watch. */
    onWatchVideo: (() -> Unit)?,
    videoOn: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(backdrop, onClick = onCollapse) {
            Icon(PhosphorIcons.Regular.CaretDown, contentDescription = stringResource(R.string.close))
        }
        // Names whatever the middle of the screen is currently showing, so the
        // switch below has a label without carrying one.
        Crossfade(
            targetState = when (panel) {
                PlayerPanel.LYRICS -> stringResource(R.string.lyrics)
                PlayerPanel.EFFECTS -> stringResource(R.string.effects)
                PlayerPanel.INFO -> stringResource(R.string.credits)
                PlayerPanel.QUEUE -> stringResource(R.string.queued)
                PlayerPanel.DEVICES -> stringResource(R.string.play_on)
                PlayerPanel.ADD_TO_PLAYLIST -> stringResource(R.string.add_to_playlist)
                // The source in place of the words "in riproduzione", which
                // said nothing the screen was not already saying. What is worth
                // knowing here is where the track came from — the playlist you
                // opened, the album, the search — and this is the one line of
                // the player not already spoken for.
                PlayerPanel.NONE -> source.ifBlank { stringResource(R.string.now_playing) }
            },
            animationSpec = tween(220),
            label = "topBarTitle",
            modifier = Modifier.weight(1f),
        ) { title ->
            // Only the source line is a way back, and only while it is the one
            // being shown: the panel names above it are labels for what is on
            // screen already.
            val open = onOpenSource.takeIf { panel == PlayerPanel.NONE && source.isNotBlank() }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (open != null) Modifier.plainClickable(open) else Modifier,
                    ),
            )
        }
        // Connect gets the permanent slot rather than hiding behind "more":
        // moving playback to another speaker is the thing you reach for while
        // the player is open, and the queue already has its own tab below.
        // Every YouTube Music track is a video underneath, and switching to it
        // is the thing this app cannot do itself — so it hands the track over
        // at the position it had reached rather than pretending otherwise.
        if (onWatchVideo != null) {
            GlassButton(backdrop, onClick = onWatchVideo) {
                Icon(
                    PhosphorIcons.Regular.YoutubeLogo,
                    contentDescription = stringResource(R.string.watch_video),
                    // Lit while the video is on, like every other button here
                    // that opens something: it is a switch, not a one-way trip.
                    tint = panelTint(videoOn),
                )
            }
        }
        if (connectAvailable) {
            GlassButton(backdrop, onClick = onOpenDevices) {
                Icon(
                    PhosphorIcons.Regular.Devices,
                    contentDescription = stringResource(R.string.devices),
                    // Lit for as long as the music is coming out of another
                    // device, not only while its list is open: on this screen it
                    // is the one thing that says the buttons are reaching
                    // somewhere else, and it has to still say it once the panel
                    // is closed.
                    tint = when {
                        onAnotherDevice -> ConnectedInk
                        else -> panelTint(panel == PlayerPanel.DEVICES)
                    },
                )
            }
        }

    }
}

/**
 * The cover, shown only when the track has no Canvas.
 *
 * Pausing pulls it back and lets the shadow settle toward the page; playing
 * pushes it out again. A spring rather than a tween on the scale, because this
 * answers a button press directly and an eased ramp reads as lag — while the
 * shadow follows a plain tween, since a bouncing shadow makes the cover look
 * like it is trembling.
 */
@Composable
private fun Cover(
    state: PlaybackState,
    panel: PlayerPanel,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    sharedScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val coverFraction by animateFloatAsState(
        targetValue = if (panel == PlayerPanel.NONE) 0.82f else 0.44f,
        label = "cover",
    )
    val playingScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "coverScale",
    )
    val playingLift by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.35f,
        animationSpec = tween(420),
        label = "coverLift",
    )

    CoverGestures(
        onNext = onNext,
        onPrevious = onPrevious,
        canGoNext = state.hasNext,
        canGoPrevious = state.hasPrevious,
        modifier = Modifier
            .fillMaxWidth(coverFraction)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = playingScale
                scaleY = playingScale
            },
    ) {
        Crossfade(
            targetState = state.artworkUrl to state.title,
            animationSpec = tween(320),
            label = "art",
        ) { (url, title) ->
            Artwork(
                url = url,
                title = title,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedArtwork(sharedScope, animatedScope)
                    .softShadow(
                        RoundedCornerShape(26.dp),
                        elevation = (10 + 30 * playingLift).dp,
                        ambient = 0.10f + 0.10f * playingLift,
                        spot = 0.20f + 0.25f * playingLift,
                    ),
                corner = 26.dp,
            )
        }
    }
}

@Composable
private fun TitleBlock(
    state: PlaybackState,
    modifier: Modifier = Modifier,
    onOpenArtist: (uri: String, name: String) -> Unit = { _, _ -> },
) {
    // Slid rather than swapped on a track change: this capsule is the only place
    // the track is named now that the big cover is gone, so the change has to be
    // visible without watching for it.
    AnimatedContent(
        targetState = state.title to state.artist,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn(tween(220)))
                .togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(170)))
        },
        label = "title",
        modifier = modifier,
    ) { (title, artist) ->
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ArtistLine(state, artist, onOpenArtist)
        }
    }
}

/**
 * The line under the title: who made this, and a way to each of them.
 *
 * "Pyrex, Sfera Ebbasta" is two artists and two pages, so the whole line
 * cannot lead to one of them. Where the finger landed is turned into a
 * character offset and matched against the span each name occupies, which is
 * what makes the second name reach the second artist.
 *
 * Plain text when there is nowhere to go: a queue built from search results, or
 * a track another device is playing, names an artist this app has no page for,
 * and a line that looks like a link and does nothing is worse than a caption.
 */
@Composable
private fun ArtistLine(
    state: PlaybackState,
    artist: String,
    onOpenArtist: (uri: String, name: String) -> Unit,
) {
    val credits = state.artists
    // The names as written, so the spans below line up with what is drawn.
    val label = if (credits.isEmpty()) artist else credits.joinToString(", ") { it.name }

    var layout by remember(label) { mutableStateOf<TextLayoutResult?>(null) }
    val openable = credits.any { it.uri != null } || state.artistUri != null

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = GlassInkDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = if (!openable) {
            Modifier
        } else {
            Modifier.pointerInput(credits, label) {
                detectTapGestures { position ->
                    val offset = layout?.getOffsetForPosition(position)
                    val hit = offset?.let { at ->
                        var start = 0
                        credits.firstOrNull { credit ->
                            val end = start + credit.name.length
                            val contains = at in start..end
                            start = end + 2
                            contains
                        }
                    }
                    // Anywhere else on the line, and any track that named only
                    // one artist, goes to the first.
                    val uri = hit?.uri ?: state.artistUri ?: return@detectTapGestures
                    onOpenArtist(uri, hit?.name ?: artist)
                }
            }
        },
    )
}

/**
 * A tap with no ripple behind it.
 *
 * These two are lines of text rather than controls, and the ripple draws a
 * rectangle around a word: the highlight ends up wider and squarer than the
 * thing it is meant to be acknowledging.
 */
@Composable
private fun Modifier.plainClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Isolated so the ticking position recomposes only these two labels. */
@Composable
internal fun TimeRow(positionMs: State<Long>, durationMs: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            formatDuration(positionMs.value),
            style = MaterialTheme.typography.bodySmall,
            color = GlassInkDim,
        )
        Text(
            formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = GlassInkDim,
        )
    }
}

@Composable
internal fun Controls(
    state: PlaybackState,
    backdrop: Backdrop,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToggleIcon(
            icon = PhosphorIcons.Regular.Shuffle,
            description = stringResource(R.string.shuffle_play),
            active = state.shuffleEnabled,
            onClick = onToggleShuffle,
        )

        // Three discs of the same material, the middle one larger. The reference
        // gives the transport its own row of circles rather than icons on a bar,
        // and the size difference is the only thing marking the primary action —
        // no fill, no accent.
        RoundGlassButton(
            backdrop = backdrop,
            size = 62.dp,
            enabled = state.hasPrevious,
            onClick = onPrevious,
        ) {
            Icon(
                PhosphorIcons.Fill.SkipBack,
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier.size(30.dp),
            )
        }

        RoundGlassButton(backdrop = backdrop, size = 76.dp, onClick = onTogglePlay) {
            // A ring around the icon while the track is still being fetched.
            //
            // Worth its place here and nowhere else: a YouTube track has to have
            // its stream resolved before a single byte can be read, which takes
            // long enough that a tap on play looks like it did nothing at all.
            // Around the button rather than in place of it, so the control stays
            // where it is and stays pressable.
            // A Box, because the button lays its content out in a row and the
            // ring belongs *around* the icon rather than beside it.
            Box(contentAlignment = Alignment.Center) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = LocalContentColor.current.copy(alpha = 0.5f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Crossfade(
                    state.wantsPlay,
                    animationSpec = tween(180),
                    label = "playPause",
                ) { playing ->
                    Icon(
                        imageVector = if (playing) PhosphorIcons.Fill.Pause else PhosphorIcons.Fill.Play,
                        contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }

        RoundGlassButton(
            backdrop = backdrop,
            size = 62.dp,
            enabled = state.hasNext,
            onClick = onNext,
        ) {
            Icon(
                PhosphorIcons.Fill.SkipForward,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(30.dp),
            )
        }

        ToggleIcon(
            icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                PhosphorIcons.Regular.RepeatOnce
            } else {
                PhosphorIcons.Regular.Repeat
            },
            description = stringResource(R.string.repeat),
            active = state.repeatMode != Player.REPEAT_MODE_OFF,
            onClick = onCycleRepeat,
        )
    }
}

/**
 * A disc of glass that answers a press.
 *
 * Built on the catalog's `LiquidButton` rather than on [GlassSurface]: the
 * squash-and-settle when you push it is the same animation the tab indicator
 * uses, and hand-rolling a second version of it would drift from the one the
 * bar has. A capsule with equal sides is a circle, so no separate shape is
 * needed.
 */
/**
 * A word for the setting that is quietly changing the record.
 *
 * Small, unclickable and out of the way: it is a note to self, not a control.
 * The control is the microphone at the other end of the panel.
 */
@Composable
private fun KaraokeBadge(amount: Float, backdrop: Backdrop, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 4.dp)
            .clip(dev.lelonio.square.ui.glass.shapes.ContinuousCapsule())
            // A way to the thing it is talking about: the lyrics, with the
            // control open. A label about a setting that cannot be reached from
            // where it is read is half a message.
            .pressable(onClick, pressedScale = 0.94f)
            // The app's material, not a grey plate: it sits among glass and a
            // painted rectangle is the one thing that reads as pasted on.
            .liquidGlass(
                config = LocalGlassEffectConfig.current,
                shape = dev.lelonio.square.ui.glass.shapes.ContinuousCapsule(),
                ownBackdrop = backdrop,
                highlightAlpha = BarHighlightAlpha,
                backdropScale = 0.4f,
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            PhosphorIcons.Fill.MicrophoneStage,
            contentDescription = null,
            // White, like the control it refers to: an accent pulled from the
            // cover lands anywhere, including on the cover itself.
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
        Text(
            stringResource(R.string.karaoke_on, (amount * 100).toInt()),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun RoundGlassButton(
    backdrop: Backdrop,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = modifier
            .size(size)
            // Dimmed rather than removed when there is nowhere to go: a control
            // that disappears makes the whole row jump.
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        contentHeight = size,
        contentPadding = 0.dp,
        // Matched to the bottom bar and the mini player. At the upstream 2dp
        // these were the one place in the app where the glass barely frosted
        // what was behind it.
    ) {
        content()
    }
}

@Composable
private fun GlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    RoundGlassButton(backdrop = backdrop, size = 44.dp, onClick = onClick) { content() }
}

/** A control that opened the panel currently showing is coloured, not just lit. */
@Composable
private fun panelTint(active: Boolean) =
    if (active) MaterialTheme.colorScheme.primary else GlassInk

/** Spotify's own green, which already means "playing over there". */
private val ConnectedInk = androidx.compose.ui.graphics.Color(0xFF1ED760)

/** The same green for "this one is already in a playlist of yours". */
private val SavedInk = ConnectedInk

@Composable
private fun ToggleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) GlassInk else GlassInkDim,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Fixed light, not themed.
 *
 * What sits behind every control here is album art under a dark wash, not the
 * app's page colour, so the light/dark scheme says nothing about what is
 * readable.
 */
internal val GlassInk = Color.White
internal val GlassInkDim = Color.White.copy(alpha = 0.68f)

/**
 * The film every glass surface is tinted with.
 *
 * One value, shared, because the surfaces looked like different materials when
 * each picked its own: the tab bar came out noticeably paler than the mini
 * player and the search button beside it.
 */
internal val GlassFilm = Color.White.copy(alpha = 0.12f)

/**
 * The lyrics, centre stage.
 *
 * Its own composable only so the empty and loading cases stay out of the layout
 * above: this sits where the cover would, so "no lyrics" has to occupy the same
 * space rather than collapsing the screen around it.
 */
@Composable
private fun LyricsStage(
    lyrics: dev.lelonio.square.data.Lyrics?,
    loading: Boolean,
    positionMs: State<Long>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    backdrop: Backdrop,
    /** Bumped when somebody arrives here asking for the karaoke control. */
    expandSignal: Int,
) {
    // Off when a song starts: turning it on is asking to read this one.
    var translated by androidx.compose.runtime.saveable.rememberSaveable(lyrics) {
        androidx.compose.runtime.mutableStateOf(false)
    }

    // The language the app itself is read in, which is the one to translate
    // into — not the phone's, when the two have been made to differ on purpose.
    val context = androidx.compose.ui.platform.LocalContext.current
    val target = remember(context) {
        (context.applicationContext as dev.lelonio.square.SquareApplication)
            .language
            .language()
    }

    // What the translator answered for this song, once it has been asked.
    // Kept per song, so turning the toggle off and on again costs nothing.
    var machine by remember(lyrics) {
        androidx.compose.runtime.mutableStateOf<List<String>?>(null)
    }
    var translating by remember(lyrics) { androidx.compose.runtime.mutableStateOf(false) }

    // A document that ships its own translation is already answered; anything
    // else is put through the translator the first time it is asked for.
    val hasOwn = lyrics?.lines.orEmpty().any { !it.translation.isNullOrBlank() }

    androidx.compose.runtime.LaunchedEffect(lyrics, translated) {
        if (!translated || lyrics == null || hasOwn || machine != null) return@LaunchedEffect
        translating = true
        machine = runCatching {
            dev.lelonio.square.backend.lyrics.Translate.lines(
                lyrics.lines.map { it.text },
                target,
            )
        }.getOrNull()
        translating = false
    }

    val shown = remember(lyrics, machine) {
        val answers = machine
        if (lyrics == null || answers == null) {
            lyrics
        } else {
            lyrics.copy(
                lines = lyrics.lines.mapIndexed { index, line ->
                    line.copy(translation = answers.getOrNull(index)?.takeIf { it.isNotBlank() })
                },
            )
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loading -> androidx.compose.material3.CircularProgressIndicator(
                color = GlassInkDim,
                strokeWidth = 2.dp,
            )

            lyrics == null -> Text(
                stringResource(R.string.no_lyrics),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassInkDim,
            )

            else -> LyricsView(
                lyrics = shown ?: lyrics,
                positionMs = positionMs,
                isPlaying = isPlaying,
                showTranslation = translated,
                onSeek = onSeek,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Beside the words rather than under them: a band across the panel took
        // the room the lyrics need, for a control touched once a song.
        val karaoke by dev.lelonio.square.playback.AudioEffects.karaoke
            .collectAsStateWithLifecycle()
        // Offered on every song with words. Where the document carries no
        // translation of its own the lines are put through a translator, so
        // there is always something behind the switch.
        if (lyrics != null) {
            TranslationToggle(
                on = translated,
                busy = translating,
                onChange = { translated = it },
                backdrop = backdrop,
                modifier = Modifier
                    // Above the karaoke dial, in the same column: the two are
                    // the panel's controls, and a second one loose on the other
                    // side would read as belonging to something else.
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 56.dp),
            )
        }

        KaraokeDial(
            amount = karaoke,
            onChange = dev.lelonio.square.playback.AudioEffects::setKaraoke,
            backdrop = backdrop,
            expandSignal = expandSignal,
            modifier = Modifier
                // Low on the right, just above the transport — where Apple puts
                // it, and out of the way of the line being sung, which sits in
                // the middle of the panel.
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 6.dp),
        )
    }
}
