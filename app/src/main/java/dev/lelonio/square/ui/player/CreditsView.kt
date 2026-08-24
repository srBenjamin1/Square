package dev.lelonio.square.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.lelonio.square.R
import dev.lelonio.square.backend.spotify.SpotifyCredits
import dev.lelonio.square.ui.theme.Ink
import dev.lelonio.square.ui.theme.InkDim

/**
 * Who made the song, in the player's own slot.
 *
 * The same three groups Spotify lists — who played it, who wrote it, who
 * produced it — and the label underneath. Names only: this is a page to read
 * once, not a place to navigate from, and every name that has an artist page
 * already has a row in the queue or a line under the title that opens it.
 */
@Composable
fun CreditsView(
    credits: SpotifyCredits.Credits?,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loading && credits == null -> CircularProgressIndicator(
                color = Ink,
                strokeWidth = 2.dp,
            )

            credits == null || credits.isEmpty -> Text(
                stringResource(R.string.credits_none),
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                credits.roles.forEach { role ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            role.title.localised(),
                            style = MaterialTheme.typography.labelLarge,
                            color = InkDim,
                        )
                        role.people.forEach { person ->
                            Text(
                                person.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            // "main artist", "composer": what they did on this
                            // track, when the credit says so.
                            person.subroles
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString(", ")
                                ?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkDim,
                                    )
                                }
                        }
                    }
                }

                credits.sources.takeIf { it.isNotEmpty() }?.let { sources ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.credits_source),
                            style = MaterialTheme.typography.labelLarge,
                            color = InkDim,
                        )
                        Text(
                            sources.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The three groups in the app's language.
 *
 * Spotify names them in English whatever the account's language is, and these
 * three cover every track that has credits at all; anything else it invents is
 * shown as it arrives rather than dropped.
 */
@Composable
private fun String.localised(): String = when (lowercase()) {
    "performers" -> stringResource(R.string.credits_performers)
    "writers" -> stringResource(R.string.credits_writers)
    "producers" -> stringResource(R.string.credits_producers)
    else -> this
}
