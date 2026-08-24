package dev.lelonio.square.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.lelonio.square.ui.theme.Ink

/**
 * What the glass in this app holds: the colour of what is playing.
 *
 * The accent comes from the artwork, which means it can be anything — a
 * saturated pink one song and a muddy brown the next. On the app's own mark,
 * one round pane on the page, that is the point. On a row of chips it is not:
 * five saturated capsules read as five buttons demanding a tap, and the labels
 * on them stop being the loudest thing in the row.
 *
 * So a chip wears the same colour pulled most of the way towards the page's
 * ink and at a fraction of the alpha. What survives is the *direction* of the
 * light and a hint of the hue, which is what makes the glass look like glass.
 *
 * @param selected the chosen chip, which carries a little more of it: it is
 *   already filled harder, and the wash should not undo that difference.
 */
@Composable
fun chipWash(selected: Boolean): Color {
    val accent = MaterialTheme.colorScheme.primary
    // Most of the way to the ink: the hue is still there, the saturation
    // mostly is not.
    val quiet = lerp(accent, Ink, 0.55f)
    return quiet.copy(alpha = if (selected) 0.32f else 0.20f)
}
