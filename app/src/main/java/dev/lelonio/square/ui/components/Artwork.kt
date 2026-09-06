package dev.lelonio.square.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowCircleDown
import com.adamglin.phosphoricons.regular.FolderSimple
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Square artwork, falling back to a generated cover.
 *
 * The fallback is derived from the title rather than one grey placeholder: a
 * wall of identical squares makes a list unreadable, while a stable per-title
 * colour gives each row something to recognise.
 */
@Composable
fun Artwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    /**
     * Decode size. Covers arrive at up to 640px; decoding one of those into a
     * 48dp row costs both memory and time on the scrolling frame, and a hundred
     * rows of it is what makes a list stutter.
     */
    decodeSize: Dp = 0.dp,
    /**
     * How long a new picture takes to replace the one before it.
     *
     * Zero in lists, and deliberately: a crossfade animates every row that
     * scrolls into view, which is exactly when frames are scarcest. Somewhere
     * that shows one picture the size of the screen, and sometimes replaces it
     * with a better copy of the same thing, wants the opposite.
     */
    crossfadeMs: Int = 0,
    /**
     * Whether a missing picture is worth drawing a stand-in for.
     *
     * A row with no cover needs one: the alternative is a hole in a list. A
     * screen that is *about* to have a picture does not — the generated tile is
     * a large letter in the middle of the screen, and a letter that appears for
     * half a second on every track change and is then replaced by the artwork
     * reads as a fault. Where the wait is known to be short, the ground under
     * it is the better placeholder.
     */
    fallback: Boolean = true,
) {
    val shape = remember(corner) { RoundedCornerShape(corner) }
    val context = LocalContext.current
    val density = LocalDensity.current

    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (url == DOWNLOADS_COVER) {
            DownloadsCover()
        } else if (url == dev.lelonio.square.data.LocalLibrary.COVER) {
            LocalFilesCover()
        } else if (url != null && (!offlineOnly() || isOnThisPhone(url))) {
            // Offline the url is not something that can be fetched, so what is
            // handed to the loader is the copy on the disk. Online the url
            // stays: covers are kept keyed on the picture rather than its size,
            // so the file behind a url may be a smaller print of it, and a page
            // that can fetch the large one should.
            val source = remember(url) { artSource(url) }
            val request = remember(source, decodeSize, crossfadeMs) {
                ImageRequest.Builder(context)
                    .data(source)
                    .scale(Scale.FILL)
                    .apply {
                        if (decodeSize > 0.dp) {
                            val px = with(density) { decodeSize.toPx() }.roundToInt()
                            size(px, px)
                        }
                    }
                    // The fade is done here rather than by the loader; see
                    // below.
                    .crossfade(false)
                    .build()
            }

            // Faded in by hand, and deliberately.
            //
            // The loader's own crossfade is skipped whenever the picture comes
            // out of memory — which, since the next song's cover is fetched
            // while the current one plays, is exactly the case that matters:
            // the artwork appeared with a cut while everything around it was
            // still animating, and arrived before the colour it is supposed to
            // bring with it. An animation of our own does not care where the
            // bytes came from.
            var arrived by remember(source) { mutableStateOf(false) }
            val appear by animateFloatAsState(
                targetValue = if (arrived || crossfadeMs <= 0) 1f else 0f,
                animationSpec = tween(crossfadeMs),
                label = "artwork",
            )

            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state ->
                    if (state is coil.compose.AsyncImagePainter.State.Success) arrived = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (crossfadeMs > 0) {
                            Modifier.graphicsLayer { alpha = appear }
                        } else {
                            Modifier
                        }
                    ),
            )
        } else if (fallback) {
            GeneratedCover(title, corner)
        }
    }
}

/**
 * The cover for something that has none.
 *
 * The previous version picked a saturated hue per title. It did make rows
 * distinguishable, but it put a random colour next to real album art in a
 * palette that is otherwise neutral, so the placeholders were the loudest thing
 * on a screen despite being the least important. This keeps the same idea —
 * stable and different per title — and spends it on tone and geometry instead:
 * a dark neutral gradient whose angle and lightness come from the title, one
 * soft off-centre highlight, and the initial set large and dimmed so it reads as
 * texture rather than as a label.
 */
/**
 * The tile for the phone's own music.
 *
 * Drawn rather than shipped as a picture: this is the one shelf in the library
 * with no artwork behind it, and it is asked for at every size from a row's
 * thumbnail to the top of a page, where a bitmap would either be soft or be
 * three bitmaps. A folder because that is what it is.
 */
@Composable
private fun LocalFilesCover() {
    Box(
        Modifier
            .fillMaxSize()
            .background(LocalFilesTile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PhosphorIcons.Regular.FolderSimple,
            contentDescription = null,
            tint = LocalFilesGlyph,
            modifier = Modifier.fillMaxSize(0.44f),
        )
    }
}

/** The two colours of that tile, deep enough to sit in a grid of covers. */
/**
 * What the image loader should actually be handed for a picture.
 *
 * Online, the url: covers are kept keyed on the picture rather than on its
 * size, so the file behind a url may be a smaller print of the same artwork,
 * and a screen that can fetch the large one should. Offline the url is nothing
 * that can be fetched, and the copy on the disk is the only picture there is.
 */
fun artSource(url: String): Any =
    if (!offlineOnly()) url
    else dev.lelonio.square.download.DownloadExtras.fileOf(url, "art") ?: url

/**
 * Whether only what is already here may be drawn.
 *
 * Offline a url handed to the loader is a request going out on a connection the
 * app has been told it does not have — and on a metered one, somebody's data.
 */
private fun offlineOnly(): Boolean =
    dev.lelonio.square.playback.OfflineMode.active.value

/**
 * Whether this picture is on the phone already.
 *
 * A file path counts as much as a saved copy does, and missing that was a bug
 * of its own: the notification and the player are handed the local file
 * directly — that is how a downloaded song shows its sleeve on the lock screen
 * — and a check that only knew how to look up remote urls decided those were
 * unavailable and drew a generated tile over a picture sitting on the disk.
 */
private fun isOnThisPhone(url: String): Boolean =
    url.startsWith("file:") ||
        url.startsWith("/") ||
        url.startsWith("content:") ||
        dev.lelonio.square.download.DownloadExtras.fileOf(url, "art") != null

/** The shelf of songs downloaded on their own; see DownloadStore.SINGLES. */
const val DOWNLOADS_COVER = "square:downloads-cover"

@Composable
private fun DownloadsCover() {
    Box(
        Modifier
            .fillMaxSize()
            .background(LocalFilesTile),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn like the local files shelf beside it: the same tile, the same
        // size, an outlined glyph rather than a filled disc. The two are the
        // same kind of thing — music that is already on the phone — and they
        // should read as a pair rather than as one shelf and one badge.
        Icon(
            PhosphorIcons.Regular.ArrowCircleDown,
            contentDescription = null,
            tint = DownloadsGlyph,
            modifier = Modifier.fillMaxSize(0.44f),
        )
    }
}

private val LocalFilesTile = Color(0xFF20306E)
private val LocalFilesGlyph = Color(0xFF2ECC57)

/** The downloads shelf's own glyph colour; see [LocalFilesGlyph] beside it. */
private val DownloadsGlyph = Color(0xFF35B7E8)

@Composable
private fun GeneratedCover(title: String, corner: Dp) {
    val seed = remember(title) { title.hashCode().absoluteValue }
    val initial = remember(title) {
        title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪"
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Four fixed diagonals rather than a free angle: a gradient that can
            // land at any rotation makes a grid of these look accidental.
            val direction = seed % 4
            val start = when (direction) {
                0 -> Offset(0f, 0f)
                1 -> Offset(size.width, 0f)
                2 -> Offset(0f, size.height)
                else -> Offset(size.width, size.height)
            }
            val end = Offset(size.width - start.x, size.height - start.y)

            // Narrow range: these have to sit together in a row without one
            // looking like a hole and the next like a light box.
            val lightness = 0.16f + (seed / 4 % 5) * 0.035f

            drawRect(
                Brush.linearGradient(
                    listOf(
                        Color.hsl(220f, 0.06f, lightness + 0.07f),
                        Color.hsl(230f, 0.08f, lightness),
                    ),
                    start = start,
                    end = end,
                ),
            )

            // A single diffuse highlight, placed off centre so the square does
            // not read as flat fill.
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(
                        size.width * (0.25f + (seed / 20 % 4) * 0.17f),
                        size.height * 0.22f,
                    ),
                    radius = size.minDimension * 0.85f,
                ),
            )
        }

        Text(
            text = initial,
            style = MaterialTheme.typography.displayLarge,
            // Sized to the tile: the same absolute type looks like a label on a
            // large cover and fills a 46dp row edge to edge.
            fontSize = with(LocalDensity.current) { (sizeHint(corner)).toSp() },
            color = Color.White.copy(alpha = 0.20f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Type size for the initial, inferred from the corner radius.
 *
 * A hack, and a deliberate one: the alternative is threading a size through
 * every call site, and corner radius already scales with the tile everywhere
 * this is used.
 */
private fun sizeHint(corner: Dp): Dp = (corner * 2.6f).coerceIn(16.dp, 96.dp)
