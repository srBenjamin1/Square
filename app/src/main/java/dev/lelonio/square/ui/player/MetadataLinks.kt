package dev.lelonio.square.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lelonio.square.data.MetadataDestination
import dev.lelonio.square.ui.glass.pressable

/** Each identity has its own touch target, semantics and controller focus. */
@Composable
internal fun ArtistMetadataLinks(
    state: PlaybackState,
    onOpen: ((MetadataDestination) -> Unit)?,
    color: Color = GlassInkDim,
    centered: Boolean = false,
) {
    FlowRow(horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start) {
        PlayerMetadata.artists(state).forEachIndexed { index, artist ->
            if (index > 0) Text(", ", style = MaterialTheme.typography.bodyMedium, color = color)
            MetadataLink(artist.name, artist.destination?.let { destination -> onOpen?.let { { it(destination) } } }, color)
        }
    }
}

@Composable
internal fun MetadataLink(name: String, onOpen: (() -> Unit)?, color: Color = GlassInkDim, modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(
        text = name,
        style = style,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.then(if (onOpen != null) {
            Modifier.pressable(onOpen, pressedScale = 0.98f, focusShape = RoundedCornerShape(6.dp))
        } else Modifier).padding(horizontal = 2.dp, vertical = 4.dp),
    )
}
