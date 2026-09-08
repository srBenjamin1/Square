package dev.lelonio.square.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.adamglin.phosphoricons.regular.X
import dev.lelonio.square.R
import dev.lelonio.square.ui.glass.LocalGlassEffectConfig
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import dev.lelonio.square.ui.glass.liquidGlass
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.theme.Ink
import dev.lelonio.square.ui.theme.InkDim
import dev.lelonio.square.ui.theme.lightPage

/**
 * The field that filters a list, drawn the way the reference draws it.
 *
 * One shape for both places it appears — the library's own lists, and the songs
 * inside a record — because they are the same act: narrowing what is already on
 * the screen. It is deliberately not the app's search: there is no result page
 * behind it, nothing is fetched, and it takes what it filters from the list it
 * sits at the top of.
 *
 * Two materials, one shape. In a page that scrolls, the field is a shade off
 * the paper, because that is what a row of a list is made of. Floating over a
 * cover it is a pane of the app's own glass — the same film and the same rim as
 * the capsule it takes the place of, since a flat grey box among the header's
 * glass buttons reads as a control from another app. Which one it is is decided
 * by whether the caller hands it something to refract.
 *
 * Built out of [BasicTextField] rather than Material's own. The filled and
 * outlined fields carry a label slot, an indicator line and a height this
 * shape has no use for, and every one of them had to be turned off before the
 * field looked like a search bar rather than a form.
 */
@Composable
fun ListSearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_in_tracks),
    /**
     * What the pane refracts. Null keeps the flat fill, which is what a field
     * sitting in the page itself wants.
     */
    backdrop: Backdrop? = null,
    /** Matched to the buttons beside it where it floats; a list row where it does not. */
    height: Dp = 38.dp,
    /** The glyphs' colour: the page's own ink here, the chrome's over a cover. */
    ink: Color = Ink,
    hint: Color = InkDim,
    /**
     * Focus on arrival.
     *
     * For the field that opens out of a button: whoever pressed the magnifier
     * means to type, and a field that arrives needing one more tap is the tap
     * they already made.
     */
    autoFocus: Boolean = false,
    /**
     * How present the contents are, read on every frame rather than in
     * composition — see the header's growing field, which fades them in behind
     * its own opening. A lambda so nothing here recomposes while it travels.
     */
    contentAlpha: () -> Float = FullyPresent,
) {
    val glass = backdrop != null
    val shape = if (glass) RoundedCornerShape(percent = 50) else RoundedCornerShape(11.dp)
    // A shade off the page: lifted out of the floor on the dark side, pressed
    // into the paper on the light one.
    val fill = if (lightPage()) Ink.copy(alpha = 0.06f) else Ink.copy(alpha = 0.10f)

    val focus = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }

    val surface = if (backdrop != null) {
        val config = LocalGlassEffectConfig.current
        Modifier
            .clip(shape)
            .liquidGlass(
                config = config,
                shape = shape,
                blurRadiusDp = config.blurRadius,
                ownBackdrop = backdrop,
            )
            // The drawn edge that separates the pane from the artwork behind
            // it, at the weight the header's capsule carries.
            .border(0.6.dp, Color.White.copy(alpha = 0.30f), shape)
    } else {
        Modifier
            .clip(shape)
            .background(fill)
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(surface)
            // After the material, so what fades is the writing and not the pane
            // it is written on.
            .graphicsLayer { alpha = contentAlpha() }
            .padding(horizontal = if (glass) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            PhosphorIcons.Bold.MagnifyingGlass,
            contentDescription = null,
            tint = hint,
            modifier = Modifier.size(16.dp),
        )

        Box(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyMedium,
                ).copy(color = ink),
                cursorBrush = SolidColor(
                    if (glass) ink else MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }

        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.14f))
                    .pressable({ onQuery("") }, shape = CircleShape, pressedScale = 0.88f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.Regular.X,
                    contentDescription = stringResource(R.string.clear),
                    tint = ink,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** The default for [ListSearchField]'s fade: a field that is simply there. */
private val FullyPresent: () -> Float = { 1f }
