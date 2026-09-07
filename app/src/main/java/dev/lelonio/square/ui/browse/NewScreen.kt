package dev.lelonio.square.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lelonio.square.R
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.data.SearchItem
import dev.lelonio.square.ui.MainViewModel
import dev.lelonio.square.ui.components.Artwork
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.theme.InkDim

/**
 * What has just come out, as a page rather than as one shelf on the home page.
 *
 * Laid out the way the reference lays this page out: a few records at the top
 * drawn large enough to be an editorial choice rather than a result, then the
 * songs out of them, then the week, then everything else. The order is how
 * specific each part is — the picks are for this listener, the week is for
 * everyone, and by the bottom it is a catalogue.
 *
 * The reference fills the rest of its page with charts, city charts and radio
 * shows, none of which Spotify hands out to an application like this one. What
 * is here is what can be answered honestly.
 */
@Composable
fun NewScreen(
    page: MainViewModel.NewPage,
    /**
     * Spotify's own rows, as it lays them out for this account.
     *
     * The rest of this page is built out of the catalogue-wide list of what
     * came out, which is the same for everyone and moves once a week. These are
     * assembled per listener and change daily, and they are the reason the tab
     * has something to say on a Tuesday.
     */
    shelves: List<dev.lelonio.square.data.HomeShelf>,
    /** Whether those rows are still coming; see SkeletonRow. */
    shelvesLoading: Boolean,
    contentPadding: PaddingValues,
    onOpen: (SearchItem) -> Unit,
    onPlaySong: (List<CatalogTrack>, Int) -> Unit,
) {
    val empty = page.hero.isEmpty() && page.thisWeek.isEmpty() &&
        page.recent.isEmpty() && shelves.isEmpty()
    if (empty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (page.loading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(R.string.nothing_here),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkDim,
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        item(contentType = "title") {
            Text(
                stringResource(R.string.tab_new),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp),
            )
        }

        if (page.hero.isNotEmpty()) {
            item(contentType = "hero") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(page.hero, key = { it.uri }) { item ->
                        HeroCard(item) { onOpen(item) }
                    }
                }
            }
        }

        if (page.songs.isNotEmpty()) {
            item(contentType = "songsHeading") { Heading(stringResource(R.string.new_songs)) }
            songRows(page.songs, onPlaySong)
        }

        // The outline of what is coming, while it is coming.
        if (shelvesLoading && shelves.isEmpty()) {
            items(
                count = SKELETON_ROWS,
                key = { "skeleton $it" },
                contentType = { "skeleton" },
            ) {
                SkeletonRow()
            }
        }

        // Spotify's own rows, under its own headings: they are titled for this
        // listener and renaming them would be this app pretending to have
        // assembled them.
        shelves.forEach { shelf ->
            item(key = "shelf ${shelf.title}", contentType = "shelfHeading") {
                Heading(shelf.title)
            }
            item(key = "shelfRow ${shelf.title}", contentType = "shelfRow") {
                Shelf(
                    shelf.items.map { entry ->
                        SearchItem(
                            uri = entry.uri,
                            title = entry.name,
                            subtitle = "",
                            artworkUrl = entry.artworkUrl,
                        )
                    },
                    onOpen,
                )
            }
        }

        if (page.thisWeek.isNotEmpty()) {
            item(contentType = "weekHeading") { Heading(stringResource(R.string.new_this_week)) }
            item(contentType = "week") { Shelf(page.thisWeek, onOpen) }
        }

        // The catalogue-wide list of what came out, and only when Spotify's
        // own picked one is not here.
        //
        // The two are the same shelf twice: theirs is chosen for this listener
        // and ours is what the market got, and on a page that shows both, the
        // reader sees "new releases" and "new releases for you" one under the
        // other with half the same covers. Ours is the fallback for an account
        // the gateway will not answer for.
        if (page.recent.isNotEmpty() && shelves.isEmpty()) {
            item(contentType = "recentHeading") {
                Heading(stringResource(R.string.new_releases_title))
            }
            item(contentType = "recent") { Shelf(page.recent, onOpen) }
        }
    }
}

/** How many outlines stand in for the rows that are coming. */
private const val SKELETON_ROWS = 3

/** The songs, one row each, played as a list rather than one at a time. */
private fun LazyListScope.songRows(
    songs: List<CatalogTrack>,
    onPlay: (List<CatalogTrack>, Int) -> Unit,
) {
    itemsIndexed(
        items = songs,
        key = { _, track -> "song ${track.uri}" },
        contentType = { _, _ -> "song" },
    ) { index, track ->
        SongRow(track) { onPlay(songs, index) }
    }
}

/**
 * A record the page is leading with.
 *
 * Wide rather than square: the reference's picks carry a line of editorial
 * under them and read as a card, and a square cover in a row of square covers
 * is a result no matter how large it is drawn.
 */
@Composable
private fun HeroCard(item: SearchItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(268.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .pressable(onClick, shape = shape, pressedScale = 0.98f)
            .padding(bottom = 14.dp),
    ) {
        Artwork(
            url = item.artworkUrl,
            title = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 0.dp,
        )
        Text(
            stringResource(R.string.new_release_tag).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
        )
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

/** A row of records, scrolled sideways. */
@Composable
private fun Shelf(items: List<SearchItem>, onOpen: (SearchItem) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = { it.uri }) { item ->
            Column(
                Modifier
                    .width(146.dp)
                    .pressable({ onOpen(item) }, pressedScale = 0.97f),
            ) {
                Artwork(
                    url = item.artworkUrl,
                    title = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    corner = 10.dp,
                    decodeSize = 146.dp,
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One new song, in the same shape the track lists everywhere else use. */
@Composable
private fun SongRow(track: CatalogTrack, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 2.dp)
            .clip(shape)
            .pressable(onClick, shape = shape, pressedScale = 0.985f)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = track.artworkUrl,
            title = track.name,
            modifier = Modifier.size(46.dp),
            corner = 8.dp,
            decodeSize = 46.dp,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                track.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp),
    )
}
