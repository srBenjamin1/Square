package dev.lelonio.square.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Broadcast
import dev.lelonio.square.R
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.ui.components.Artwork
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.theme.InkDim

/**
 * Radio: one station per artist the account actually listens to.
 *
 * Spotify has no "browse radio" of its own to read, so the page is built rather
 * than fetched. Every station is seeded from a song — `spotify:station:track:…`
 * is the one form the access point reliably answers — and named after the
 * artist, because two stations seeded from two songs by the same person are the
 * same station to a listener, and "Radio" alone names the feature rather than
 * the station.
 *
 * One card per artist for that reason: seeds are taken from the account's most
 * played songs, and those repeat the same names over and over.
 */
@Composable
fun RadioScreen(
    /** Seeds, already reduced to one per artist by the caller. */
    seeds: List<CatalogTrack>,
    loading: Boolean,
    contentPadding: PaddingValues,
    onOpen: (CatalogTrack) -> Unit,
) {
    if (seeds.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(R.string.radio_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.tab_radio),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.stations_for_you),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        items(seeds, key = { it.uri }) { seed ->
            StationTile(seed) { onOpen(seed) }
        }
    }
}

/**
 * A station, drawn as the thing it plays rather than as a list icon.
 *
 * The cover of the song it grew from, darkened, with the artist's name over it:
 * a grid of identical broadcast glyphs would be unreadable, and the cover is
 * what makes one station recognisable at a glance.
 */
@Composable
private fun StationTile(seed: CatalogTrack, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .pressable(onClick, shape = shape, pressedScale = 0.97f),
    ) {
        Artwork(
            url = seed.artworkUrl,
            title = seed.artist,
            modifier = Modifier.fillMaxSize(),
            corner = 0.dp,
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.8f),
                    ),
                ),
        )

        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Fill.Broadcast,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                stringResource(R.string.radio_of, seed.artist),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                seed.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
