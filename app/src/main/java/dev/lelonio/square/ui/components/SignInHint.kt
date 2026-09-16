package dev.lelonio.square.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.YoutubeLogo
import dev.lelonio.square.R
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.theme.Ink
import dev.lelonio.square.ui.theme.lightPage
import dev.lelonio.square.ui.theme.pageGround

/**
 * What signing in to YouTube Music would add, where its absence shows.
 *
 * The source works without an account, and nothing here says otherwise: this
 * is not a wall in front of the page but a card at the top of it. It sits on
 * the pages that are thinner for the lack of one, the home page and the
 * library, and says what arrives: the listener's own lists and mixes, and a
 * home made for them rather than for the country.
 *
 * Drawn like an unlit filter chip, a shade off the ground with a hairline, and
 * its one action in ink, so it reads as part of the page in either theme.
 */
@Composable
fun SignInHint(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (lightPage()) Color.White else Ink.copy(alpha = 0.07f))
            .border(1.dp, Ink.copy(alpha = if (lightPage()) 0.07f else 0.09f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.Fill.YoutubeLogo,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                stringResource(R.string.youtube_sign_in_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.8f),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 1.dp),
            )
        }
        Box(
            Modifier
                .padding(top = 14.dp)
                .align(Alignment.End)
                .pressable(onSignIn, pressedScale = 0.96f)
                .height(38.dp)
                .clip(CircleShape)
                .background(Ink)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.youtube_sign_in),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = pageGround(),
                maxLines = 1,
            )
        }
    }
}
