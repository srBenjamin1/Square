package dev.lelonio.square.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * A picture that stops being a picture on its way down the screen.
 *
 * Two copies of the same image: the sharp one, and over its lower half a
 * blurred one that fades in, and then the page's own colour arriving under
 * that. It is what makes a photograph dissolve into a page instead of ending —
 * a straight fade to a flat colour leaves the image crisp right up to the point
 * where it vanishes, which reads as a photo behind a curtain.
 *
 * Shared by the pages that are made of one picture — an artist, a record, a
 * list — and by the player when the track has nothing moving to show. One
 * treatment, so the cover looks the same wherever the app puts it up large.
 *
 * Blurred at decode time rather than with `Modifier.blur`: that modifier is a
 * render effect over the whole layer, re-run on every frame the layer changes,
 * and this sits under everything.
 */
@Composable
fun HeroBackdrop(
    artworkUrl: String?,
    /** Named for the description a reader hears; the picture is decorative. */
    title: String,
    /** The tone the picture ends on, which is the page's own. */
    pageColor: Color,
    modifier: Modifier = Modifier,
    /** The cover as a moving picture, where there is one; see [MotionCover]. */
    motionUrl: String? = null,
    /**
     * Whether the picture is still being looked up.
     *
     * While it is, nothing stands in for it: see [Artwork]'s `fallback`. The
     * page colour is already drawn under this, so the wait reads as a header
     * that has not finished arriving rather than as a song with no cover.
     */
    pending: Boolean = false,
    /** Where the blurred copy starts and where it is complete, top to bottom. */
    softenFrom: Float = 0.42f,
    softenTo: Float = 0.72f,
    /**
     * The picture's own proportions, when it is to be shown at them.
     *
     * Null crops it to fill whatever it is given, which is right for a header
     * measured in advance. A number lays it across the top at that ratio
     * instead, untouched: a picture drawn for a record is a composition, and
     * cropping it to a phone's shape enlarges it and cuts the sides off the
     * thing somebody framed.
     */
    imageAspect: Float? = null,
) {
    Box(modifier) {
        if (imageAspect != null) {
            // The colour first, because the picture no longer covers the slot.
            Box(Modifier.fillMaxSize().background(pageColor))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (imageAspect != null) {
                        Modifier.aspectRatio(imageAspect)
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
                .align(Alignment.TopCenter),
        ) {
        Artwork(
            url = artworkUrl,
            title = title,
            modifier = Modifier.fillMaxSize(),
            corner = 0.dp,
            // One picture, sometimes replaced by a better copy of itself — the
            // catalogue's scan arriving after the one the queue carried. A cut
            // between two versions of the same artwork is the most visible
            // change this screen ever makes; a fade makes it a refinement.
            crossfadeMs = SWAP_MS,
            fallback = !pending,
        )

        // The moving cover over the still one, which stays underneath as what is
        // shown until the first frame arrives.
        if (motionUrl != null) {
            MotionCover(url = motionUrl, modifier = Modifier.fillMaxSize())
        }

        if (artworkUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    // The same picture the sharp copy above draws, which
                    // offline is the file on the disk rather than the url.
                    .data(artSource(artworkUrl))
                    .size(HERO_BLUR_PX)
                    .transformations(HeroBlur)
                    // A hardware bitmap cannot be read back, and the blur reads
                    // every pixel of it.
                    .allowHardware(false)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // Its own layer, so the mask below erases this copy alone
                    // and not the sharp one underneath it.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                softenFrom to Color.Transparent,
                                softenTo to Color.Black,
                                1f to Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }

        // And the colour, arriving under the blur. It ends on the page's own
        // tone rather than on black: the last row of the picture and the first
        // row of the background are then the same colour, which is what leaves
        // no seam to find.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        // Tied to where the picture is being softened rather
                        // than to fixed fractions. With them, a picture asked to
                        // stay sharp most of the way down was still being washed
                        // out from halfway — the colour arrived on its own
                        // schedule and paled the very part that was meant to be
                        // legible.
                        0f to Color.Black.copy(alpha = 0.22f),
                        (softenFrom * 0.45f) to Color.Transparent,
                        softenFrom to pageColor.copy(alpha = 0.12f),
                        ((softenFrom + softenTo) / 2f) to pageColor.copy(alpha = 0.55f),
                        // Opaque at the end of the softening, not nearly
                        // opaque: the last few percent of the picture were
                        // showing through, and a picture that is still faintly
                        // there has an edge — which is the seam this whole
                        // gradient exists to remove.
                        softenTo to pageColor,
                        1f to pageColor,
                    ),
                ),
        )
        }
    }
}

/** How long a better copy of the same picture takes to arrive. */
private const val SWAP_MS = 450

/** Decode size for the soft copy, in pixels. */
private const val HERO_BLUR_PX = 240

/** Soft enough to be a wash, sharp enough to keep the picture's shapes. */
private val HeroBlur = BlurTransformation(radius = 10, passes = 2)
