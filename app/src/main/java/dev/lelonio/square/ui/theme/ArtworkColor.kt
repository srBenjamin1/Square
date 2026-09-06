package dev.lelonio.square.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a page ends up once the artwork's colour has faded out of it.
 *
 * Not pure black: a page that ends on #000 against a phone's own black bezel
 * has no bottom edge, and the last shelf on it looks like it is falling off.
 */
val PageFloor = Color(0xFF0A0A0C)

/**
 * The page tone for a cover.
 *
 * The dominant colour arrives at full saturation — it has to, it is picked to
 * identify the artwork — and using it as a background would put text on a
 * fluorescent field. Most of the way to the floor keeps the hue recognisable
 * and nothing else. A null cover falls back to the floor rather than to grey.
 */
fun pageColorFor(accent: Color?): Color =
    accent?.let { androidx.compose.ui.graphics.lerp(it, PageFloor, 0.7f) } ?: PageFloor

/**
 * The page tone the other catalogue chose for a record, as six hex digits.
 *
 * Kept apart from [pageColorFor] and mixed much lighter, because the two
 * sources are not the same kind of colour. A dominant colour is picked out of a
 * photograph at full strength and has to be taken most of the way down before
 * text can sit on it; this one was chosen by somebody as the ground for a page,
 * so taking it that far down would throw away the choice — and it is what makes
 * a record's page read as coloured rather than as dark grey.
 *
 * Null, or anything unparseable, ends on the floor like everything else.
 */
fun pageColorForHex(hex: String?): Color = hex
    ?.let { runCatching { Color(android.graphics.Color.parseColor("#$it")) }.getOrNull() }
    ?.let { androidx.compose.ui.graphics.lerp(it, PageFloor, 0.42f) }
    ?: PageFloor

/**
 * The dominant colour of an artwork URL, for seeding the theme.
 *
 * Results are memoised per URL: the same cover is asked for by the mini player,
 * the full player and the theme at once, and decoding a bitmap three times per
 * track change is wasteful.
 */
@Composable
fun rememberArtworkColor(artworkUrl: String?): State<Color?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) {
            state.value = null
            return@LaunchedEffect
        }
        cached[artworkUrl]?.let {
            state.value = it
            return@LaunchedEffect
        }
        state.value = extractDominant(context, artworkUrl)?.also { cached[artworkUrl] = it }
    }
    return state
}

private val cached = object : LinkedHashMap<String, Color>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, Color>?) = size > 64
}

/**
 * Works out a cover's colour before anything asks for it.
 *
 * The picture and the colour it tints the page with are two different reads of
 * the same file, and only the first of them is prefetched with the song that is
 * coming. Left alone, the artwork arrived out of memory in a single frame while
 * its colour was still being extracted — the cover changed, and the page caught
 * up with it a moment later. This puts the answer in the same place the screen
 * will look for it, at the same time as the picture.
 */
suspend fun warmArtworkColor(context: Context, url: String) {
    if (cached.containsKey(url)) return
    extractDominant(context, url)?.let { cached[url] = it }
}

/**
 * A handful of colours from an artwork, for something that has to move.
 *
 * [rememberArtworkColor] answers with one colour because a theme needs one.
 * An animation needs several or it has nothing to travel between, and they
 * have to come from the same picture or the movement stops being the cover's.
 * Empty until the cover has been read, and empty for good if it cannot be.
 */
@Composable
fun rememberArtworkPalette(artworkUrl: String?): State<List<Color>> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) {
            state.value = emptyList()
            return@LaunchedEffect
        }
        cachedPalettes[artworkUrl]?.let {
            state.value = it
            return@LaunchedEffect
        }
        val swatches = extractPalette(context, artworkUrl)
        if (swatches.isNotEmpty()) cachedPalettes[artworkUrl] = swatches
        state.value = swatches
    }
    return state
}

private val cachedPalettes = object : LinkedHashMap<String, List<Color>>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, List<Color>>?) = size > 32
}

private suspend fun extractPalette(context: Context, url: String): List<Color> =
    withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(96)
                .allowHardware(false)
                .build()

            val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
                ?.drawable
                ?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
                ?: return@runCatching emptyList()

            val palette = Palette.from(bitmap).clearFilters().generate()
            // In this order on purpose: the vivid ones lead, the muted ones fill
            // in behind them, and a cover that is all one colour still answers
            // with something rather than nothing.
            listOfNotNull(
                palette.vibrantSwatch,
                palette.lightVibrantSwatch,
                palette.darkVibrantSwatch,
                palette.mutedSwatch,
                palette.darkMutedSwatch,
                palette.dominantSwatch,
            )
                .map { Color(it.rgb) }
                .distinct()
                .take(4)
        }.getOrDefault(emptyList())
    }

private suspend fun extractDominant(context: Context, url: String): Color? =
    withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                // Palette only needs colour proportions, so decode small: a
                // full-size cover costs far more memory for the same answer.
                .size(96)
                .allowHardware(false)
                .build()

            val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
                ?.drawable
                ?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
                ?: return@runCatching null

            val palette = Palette.from(bitmap).clearFilters().generate()
            val rgb = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: return@runCatching null

            Color(rgb)
        }.getOrNull()
    }
