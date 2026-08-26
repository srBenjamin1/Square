package dev.lelonio.square.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lelonio.square.data.Lyrics
import kotlin.math.abs

/**
 * Synced lyrics, animated in the manner of Apple Music's view — the approach
 * demonstrated by [amlv](https://github.com/dokar3/amlv) (Apache-2.0), written
 * here rather than adapted.
 *
 * Three things carry the effect, and they matter more together than separately:
 * the active line grows and brightens, lines fall away by *distance* from it
 * rather than being uniformly dim, and everything moves on a spring so a line
 * change reads as physical instead of as a cut.
 */
@Composable
fun LyricsView(
    lyrics: Lyrics,
    positionMs: State<Long>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    /** Whether to read each line in the listener's language underneath it. */
    showTranslation: Boolean = false,
    onSeek: (Long) -> Unit,
) {
    // Keyed on the lyrics, so a new track starts at its first line. Kept across
    // the change, the list stayed wherever the previous song was left.
    val listState = remember(lyrics) { androidx.compose.foundation.lazy.LazyListState() }
    val position by rememberSmoothPosition(positionMs, isPlaying)

    val activeLine = remember(lyrics, position) {
        if (!lyrics.synced) -1
        else lyrics.lines.indexOfLast { (it.startTimeMs ?: 0L) <= position }
    }

    // Centre the active line rather than pin it to the top: the lines around it
    // are the context that makes a lyric readable while it plays.
    androidx.compose.runtime.LaunchedEffect(activeLine) {
        if (activeLine < 0) return@LaunchedEffect
        val viewportCentre = listState.layoutInfo.viewportSize.height / 2
        listState.animateScrollToItem(activeLine, -viewportCentre + LineHeightPx)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            // Fades the ends instead of cutting lines off square. DstIn needs
            // its own layer, otherwise the mask would erase what is behind the
            // list as well.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.18f to Color.Black,
                        0.82f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 40.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = index == activeLine
            LyricRow(
                text = line.text,
                // Real timings where the source has them; see the lyrics package.
                words = line.words,
                translation = line.translation.takeIf { showTranslation },
                positionMs = position,
                distance = if (activeLine < 0) 0 else abs(index - activeLine),
                isActive = isActive,
                unsynced = !lyrics.synced,
                // Only the line being sung pays for this; the rest are drawn
                // plain.
                onClick = { line.startTimeMs?.let(onSeek) },
            )
        }
    }
}

/**
 * The playback position, advanced every frame between reports.
 *
 * The player is polled four times a second, which is plenty for a seek bar and
 * far too coarse for a highlight that sweeps across words: at 250ms steps the
 * words light up in visible jumps, which is exactly what this was. Between
 * reports the clock simply runs — playback does too — and every report snaps it
 * back to the truth, so it can never drift further than one poll.
 */
@Composable
private fun rememberSmoothPosition(source: State<Long>, isPlaying: Boolean): State<Long> {
    val smoothed = remember { androidx.compose.runtime.mutableLongStateOf(source.value) }
    val reported = source.value

    androidx.compose.runtime.LaunchedEffect(reported, isPlaying) {
        smoothed.longValue = reported
        if (!isPlaying) return@LaunchedEffect

        val startedAt = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                smoothed.longValue = reported + (frame - startedAt)
            }
        }
    }

    return smoothed
}

/**
 * How far through the current line playback is, 0 to 1.
 *
 * The line's end is the next line's start; the last line has no such marker, so
 * it gets a fixed span rather than staying at zero for however long the outro
 * runs.
 */
private fun lineProgress(lyrics: Lyrics, index: Int, positionMs: Long): Float {
    val start = lyrics.lines[index].startTimeMs ?: return 0f
    val end = lyrics.lines.getOrNull(index + 1)?.startTimeMs ?: (start + TAIL_LINE_MS)
    val span = (end - start).coerceAtLeast(1L)
    return ((positionMs - start).toFloat() / span).coerceIn(0f, 1f)
}


@Composable
private fun LyricRow(
    text: String,
    /** When each word is sung, when that is known. */
    words: List<dev.lelonio.square.data.LyricWord>,
    /** The line in the listener's language, if there is one and it is wanted. */
    translation: String?,
    positionMs: Long,
    distance: Int,
    isActive: Boolean,
    unsynced: Boolean,
    onClick: () -> Unit,
) {
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
    )

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.90f,
        animationSpec = springSpec,
        label = "lyricScale",
    )

    // Opacity falls off with distance, so the eye is pulled to the current line
    // without the rest disappearing.
    val alpha by animateFloatAsState(
        targetValue = when {
            unsynced -> 1f
            isActive -> 1f
            else -> (0.62f - distance * 0.12f).coerceAtLeast(0.22f)
        },
        animationSpec = springSpec,
        label = "lyricAlpha",
    )

    // Blur only kicks in a few lines out, and only where the platform supports
    // it (API 31+); below that it is a no-op and the alpha ramp carries it.
    val blurRadius = when {
        unsynced || distance <= 1 -> 0.dp
        else -> ((distance - 1) * 1.1f).coerceAtMost(4.5f).dp
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !unsynced, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 27.sp,
            lineHeight = 33.sp,
        )

        androidx.compose.foundation.layout.Column {

            // One composable per word, so the one being sung can move on its own.
            //
            // Taken from Convx, whose lyrics lift each word as it is reached: it
            // rises a little, grows a little and carries a soft light, then settles
            // as the next takes over. Doing it to the whole line — which is what
            // this did first — makes the whole sentence breathe, which is not the
            // same thing and reads as a wobble.
            //
            // Wrapped by the layout rather than by the text engine, since a lifted
            // word has to be its own thing to lift. The trailing space is part of
            // each word, so the spacing is the font's own rather than a guess.
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                    .graphicsLayer {
                        // Scale from the left edge so the text grows into the line
                        // instead of drifting sideways.
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
            ) {
                // Word by word only where the words really are timed.
                //
                // A line-timed source knows when the line begins and nothing else,
                // and dividing it by how much each word has to say is a guess: on a
                // held note it lights three words while one is still being sung.
                // Better to say what is known — this line, now — and light the
                // whole of it. The words move only when a source has actually timed
                // them; see the lyrics package.
                if (words.isEmpty()) {
                    // The whole line does what a word does when a word is timed:
                    // rises, grows and carries a light while it is the one being
                    // sung. What is not known is where inside the line the singing
                    // has reached, so the line is treated as the unit it is.
                    val lineBump by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        label = "lineBump",
                    )

                    Text(
                        text = text,
                        style = style.copy(
                            shadow = if (lineBump > 0.05f) {
                                androidx.compose.ui.graphics.Shadow(
                                    color = Color.White.copy(alpha = 0.4f * lineBump),
                                    offset = androidx.compose.ui.geometry.Offset.Zero,
                                    blurRadius = 16f * lineBump,
                                )
                            } else {
                                null
                            },
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.graphicsLayer {
                            translationY = -4.dp.toPx() * lineBump
                            scaleX = 1f + 0.02f * lineBump
                            scaleY = 1f + 0.02f * lineBump
                        },
                    )
                } else {
                    // Each word carries the space that follows it in the line.
                    //
                    // Taken out, the words sat against each other: what is drawn
                    // here is a row of separate pieces of text, and a row has no
                    // idea that written language puts gaps between words.
                    val pieces = remember(text, words) {
                        var at = 0
                        words.map { word ->
                            val start = text.indexOf(word.text, at).takeIf { it >= 0 } ?: at
                            var stop = start + word.text.length
                            while (stop < text.length && text[stop].isWhitespace()) stop++
                            at = stop
                            text.substring(start, stop)
                        }
                    }

                    pieces.forEachIndexed { index, piece ->
                        val word = words[index]
                        val span = (word.endMs - word.startMs).coerceAtLeast(1L)
                        val lit = if (isActive) {
                            ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        // Eased at both ends, so a word neither snaps on nor
                        // finishes brightening a beat before the next begins.
                        val eased = lit * lit * (3f - 2f * lit)
                        // And a half turn of a sine across the word: strongest in
                        // the middle of it, gone by the time the next one starts.
                        val bump = if (isActive && lit > 0f && lit < 1f) {
                            kotlin.math.sin(lit * kotlin.math.PI.toFloat())
                        } else {
                            0f
                        }

                        Text(
                            text = piece,
                            style = style.copy(
                                shadow = if (bump > 0.05f) {
                                    androidx.compose.ui.graphics.Shadow(
                                        color = Color.White.copy(alpha = 0.4f * bump),
                                        offset = androidx.compose.ui.geometry.Offset.Zero,
                                        blurRadius = 16f * bump,
                                    )
                                } else {
                                    null
                                },
                            ),
                            fontWeight = FontWeight.Bold,
                            // White rather than the artwork accent. These sit over
                            // a Canvas now, and an accent pulled from the cover can
                            // land anywhere — including on the clip's own colours.
                            color = Color.White.copy(
                                alpha = if (isActive) {
                                    DIM_WORD_ALPHA + (1f - DIM_WORD_ALPHA) * eased
                                } else {
                                    1f
                                },
                            ),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.graphicsLayer {
                                translationY = -4.dp.toPx() * bump
                                scaleX = 1f + 0.02f * bump
                                scaleY = 1f + 0.02f * bump
                            },
                        )
                    }
                }
            }

            // Under the line, quieter and smaller: a second voice reading
            // along, not a second line of the song. It fades, blurs and grows
            // with the line above it, being part of the same line.
            if (!translation.isNullOrBlank()) {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                    ),
                    color = Color.White.copy(alpha = 0.62f),
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                        .padding(top = 3.dp)
                        .graphicsLayer {
                            // Anchored left with the words above, so it does
                            // not drift out from under its own line as that
                            // one grows.
                            transformOrigin =
                                androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                )
            }
        }
    }
}

/** Rough line height in px, used to bias the auto-scroll target. */
private const val LineHeightPx = 60

/** How dim a word is before it has been reached. */
private const val DIM_WORD_ALPHA = 0.42f

/** How long the last line is assumed to last, having no next line to end it. */
private const val TAIL_LINE_MS = 4_000L
