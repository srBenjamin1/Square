package dev.lelonio.square.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.theme.Ink
import dev.lelonio.square.ui.theme.lightPage
import dev.lelonio.square.ui.theme.pageGround

/**
 * One of a row of filters, drawn the way the reference draws them.
 *
 * Flat, not glass. Every chip in this app used to be a pane of the same liquid
 * material as the bars, with the lit one filled a little harder and washed with
 * the artwork's colour. Over a tinted page that read as one family; over the
 * neutral ground these pages have now it reads as a row of grey lozenges with a
 * colour in them that belongs to something else on the screen.
 *
 * The reference is much plainer and says more with less: an unlit chip is a
 * whisper of the ink it is written in, and the lit one is the ink itself with
 * the page's own colour for a label. There is no third state and no accent —
 * being *on* is carried entirely by the inversion, which is legible at a glance
 * and in both themes without a single value changing side.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // An unlit chip is a card, not a film: on the reference it is white with a
    // hairline round it, sitting on a grey page. Taken to this app's two sides
    // that is "a shade away from the ground, outlined" — white on the light
    // side, and a lift out of the floor on the dark one.
    val resting = if (lightPage()) Color.White else Ink.copy(alpha = 0.09f)

    // Eased, because a row of these is usually tapped along and a cut between
    // two solid fills reads as the row redrawing itself.
    val fill by animateColorAsState(
        targetValue = if (selected) Ink else resting,
        animationSpec = tween(durationMillis = 180),
        label = "chipFill",
    )
    val labelInk by animateColorAsState(
        targetValue = if (selected) pageGround() else Ink,
        animationSpec = tween(durationMillis = 180),
        label = "chipInk",
    )
    val edge by animateColorAsState(
        targetValue = if (selected) Color.Transparent else Ink.copy(alpha = 0.14f),
        animationSpec = tween(durationMillis = 180),
        label = "chipEdge",
    )

    Box(
        modifier
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, edge, CircleShape)
            .pressable(onClick, shape = CircleShape, pressedScale = 0.94f)
            .defaultMinSize(minHeight = CHIP_HEIGHT)
            .padding(horizontal = CHIP_PADDING, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = labelInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Measured off the reference: shorter and tighter than the glass ones were. */
private val CHIP_HEIGHT = 34.dp
private val CHIP_PADDING = 16.dp
