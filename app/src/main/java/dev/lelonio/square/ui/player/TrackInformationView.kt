package dev.lelonio.square.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import dev.lelonio.square.thor.input.thorVerticalScroll as verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lelonio.square.R
import dev.lelonio.square.backend.spotify.SpotifyCredits
import dev.lelonio.square.data.MetadataDestination
import dev.lelonio.square.ui.theme.Ink
import dev.lelonio.square.ui.theme.InkDim

/** Metadata navigation and the original read-only credits occupy separate sections. */
@Composable
internal fun TrackInformationView(
    credits: SpotifyCredits.Credits?,
    loading: Boolean,
    modifier: Modifier = Modifier,
    state: PlaybackState,
    metadataResolving: Boolean = false,
    onOpenMetadata: ((MetadataDestination) -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().heightIn(max = 176.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.artist_and_album), style = MaterialTheme.typography.labelLarge, color = InkDim)
            if (state.artist.isNotBlank() || state.artists.isNotEmpty()) {
                Text(stringResource(R.string.artists), style = MaterialTheme.typography.labelSmall, color = InkDim)
                ArtistMetadataLinks(state, onOpenMetadata, color = Ink)
            }
            Text(stringResource(R.string.album), style = MaterialTheme.typography.labelSmall, color = InkDim)
            if (state.album.isNotBlank()) {
                MetadataLink(state.album, onOpenAlbum, color = Ink)
            } else {
                Text(stringResource(if (metadataResolving) R.string.metadata_resolving else R.string.metadata_unavailable),
                    style = MaterialTheme.typography.bodyMedium, color = InkDim)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = Ink.copy(alpha = 0.15f))
        Text(stringResource(R.string.credits), style = MaterialTheme.typography.labelLarge, color = InkDim,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        // No navigation callbacks enter this component. Roles, names and sources stay informational.
        CreditsView(credits, loading, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
