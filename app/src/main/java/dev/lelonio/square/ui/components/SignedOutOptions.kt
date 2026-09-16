package dev.lelonio.square.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lelonio.square.R
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.theme.InkDim

/**
 * The ways out of a page that only asks to sign in to Spotify.
 *
 * Signed out, that page has no header and so no way into the settings, which
 * is where the source is chosen and the guide is shown again. Someone who
 * picked Spotify by mistake, or skipped the guide meaning to come back to it,
 * was left with one button leading to a service they may not have. These are
 * the other two answers, quieter than the sign-in so it stays the obvious one.
 */
@Composable
fun SignedOutOptions(onUseYouTube: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        QuietLink(stringResource(R.string.use_youtube_music), onUseYouTube)
        QuietLink(stringResource(R.string.settings), onOpenSettings)
    }
}

@Composable
private fun QuietLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = InkDim,
        modifier = Modifier
            .clip(CircleShape)
            .pressable(onClick, shape = CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
