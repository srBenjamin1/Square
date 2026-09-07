package dev.lelonio.square.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.lelonio.square.R
import dev.lelonio.square.ui.glass.LocalGlassEffectConfig
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import dev.lelonio.square.ui.glass.liquidGlass
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.glass.shapes.ContinuousCapsule
import dev.lelonio.square.ui.glass.shapes.ContinuousRoundedRectangle
import dev.lelonio.square.ui.player.GlassInk
import dev.lelonio.square.ui.player.GlassInkDim

/**
 * What a new version is, and what it changes, before anything is downloaded.
 *
 * Drawn inside the app's own window rather than as a system dialog, which is
 * the difference between glass and a grey card: a dialog is its own window and
 * cannot sample what is behind it, so it can only imitate the material. This
 * sits over the page it dims, refracts it, and is the same pane as the rest of
 * the app.
 *
 * The shape of it is the library's own dialog — the wide corner, the dim, the
 * two capsule actions of equal weight — but the material is the app's, read
 * from the same setting the bottom bar reads. A dialog with glass of its own
 * would be the one surface here that ignores what the user asked for.
 *
 * It shows what the release says about itself. An update is a thing to agree
 * to, and "there is a new version" is not enough to agree to: what changed is
 * the whole of the argument for pressing the button.
 */
@Composable
fun UpdateSheet(
    version: String,
    /** Download size, already formatted, or null when the release did not say. */
    size: String?,
    /** What the release says about itself, as written on the releases page. */
    notes: String,
    /** 0f..1f while fetching, null when the length is unknown; absent at rest. */
    progress: Float?,
    downloading: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // The page goes back rather than away: turned down, and left
                // where it was. Blurring it too was tried and read as the app
                // going out of focus rather than as one panel coming forward —
                // the sharp panel over the dark page says it on its own.
                // Behind what this holds, not over it: drawn after the
                // content it would dim the panel too, which is the one thing on
                // the screen that has to stay lit.
                .background(DIM)
                // Swallows what lands outside the panel. Dismissing by tapping
                // there is deliberately not offered while a download is
                // running, since there would be nothing to come back to.
                .pressable(onClick = { if (!downloading) onDismiss() }),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(tween(240), initialScale = 0.92f) + fadeIn(tween(240)),
                exit = scaleOut(tween(160), targetScale = 0.94f) + fadeOut(tween(160)),
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 28.dp)
                        .fillMaxWidth()
                        .liquidGlass(
                            // Every parameter of the material comes from here:
                            // the frost, the edge, the film, the colour. A
                            // dialog is not a special surface, and one with
                            // glass of its own would be the only thing in the
                            // app that ignores what the user set.
                            config = LocalGlassEffectConfig.current,
                            shape = PANEL,
                            ownBackdrop = backdrop,
                            // Not the material: what makes the panel the lit
                            // thing on a screen that has gone dark. It samples
                            // the page as it was — sharp, and never dimmed —
                            // so it already comes through brighter than its
                            // own surroundings, and this lifts it the rest of
                            // the way.
                            onDrawTint = { drawRect(LIFT) },
                        )
                        // Nothing behind the panel takes a tap through it.
                        .pressable(onClick = {}),
                ) {
                    Text(
                        stringResource(R.string.update_available, version),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = GlassInk,
                        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp),
                    )
                    size?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassInkDim,
                            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp),
                        )
                    }

                    if (notes.isNotBlank()) {
                        // The emphasis colour is handed in rather than read
                        // inside: this builds a string, not a composition, and
                        // the ink now depends on the theme.
                        val emphasis = GlassInk
                        val text = remember(notes, emphasis) { releaseNotes(notes, emphasis) }
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassInkDim,
                            modifier = Modifier
                                .padding(start = 24.dp, end = 24.dp, top = 14.dp)
                                // Capped and scrolled: a release with a long
                                // list of changes should not push its own
                                // buttons off the screen.
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }

                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = GlassInk.copy(alpha = 0.18f),
                            modifier = Modifier
                                .padding(start = 24.dp, end = 24.dp, top = 24.dp)
                                .fillMaxWidth(),
                        )
                        Text(
                            progress
                                ?.let { "${(it * 100).toInt()}%" }
                                ?: stringResource(R.string.update_downloading),
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassInkDim,
                            modifier = Modifier.padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = 10.dp,
                                bottom = 24.dp,
                            ),
                        )
                    } else {
                        Row(
                            Modifier
                                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DialogAction(
                                label = stringResource(R.string.later),
                                container = FILM.copy(alpha = 0.2f),
                                content = GlassInk,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            DialogAction(
                                label = stringResource(R.string.update_now),
                                container = MaterialTheme.colorScheme.primary,
                                content = Color.White,
                                onClick = onInstall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogAction(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(ContinuousCapsule)
            .background(container)
            .pressable(onClick, pressedScale = 0.96f)
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}

/**
 * The release notes as prose rather than as their source.
 *
 * They are written as Markdown, because that is what the releases page renders,
 * and read here as `## New` and `- **Lyrics**` — the punctuation of a format
 * nobody is reading in. This is not a Markdown renderer: it handles the four
 * things release notes are actually written with, and leaves anything else as
 * the text it already is.
 */
private fun releaseNotes(source: String, emphasis: Color): AnnotatedString = buildAnnotatedString {
    val lines = source.trim().lines()
    lines.forEachIndexed { index, raw ->
        val line = raw.trim()
        if (line.isEmpty()) {
            // One blank line between paragraphs, never a run of them.
            if (index > 0 && lines[index - 1].isNotBlank() && index < lines.lastIndex) {
                append('\n')
            }
            return@forEachIndexed
        }
        if (index > 0) append('\n')

        val heading = line.startsWith("#")
        val bullet = line.startsWith("- ") || line.startsWith("* ")
        val body = line
            .removePrefix("#").removePrefix("#").removePrefix("#").trimStart()
            .let { if (bullet) it.drop(2) else it }

        if (bullet) append("•  ")
        if (heading) {
            withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.SemiBold)) {
                appendInline(body, emphasis)
            }
        } else {
            appendInline(body, emphasis)
        }
    }
}

/** Bold spans and code ticks, which is all the emphasis the notes ever use. */
private fun AnnotatedString.Builder.appendInline(text: String, emphasis: Color) {
    var rest = text
    while (true) {
        val open = rest.indexOf("**")
        if (open < 0) break
        val close = rest.indexOf("**", open + 2)
        if (close < 0) break
        append(rest.substring(0, open).replace("`", ""))
        withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.SemiBold)) {
            append(rest.substring(open + 2, close).replace("`", ""))
        }
        rest = rest.substring(close + 2)
    }
    append(rest.replace("`", ""))
}

/** What is left of the page behind. */
private val DIM = Color(0xFF121212).copy(alpha = 0.62f)

/** The panel's own light, over the glass. */
private val LIFT = Color.White.copy(alpha = 0.08f)

/** What the resting action is filled with, over the panel's own glass. */
private val FILM = Color(0xFF121212).copy(alpha = 0.4f)

private val PANEL = ContinuousRoundedRectangle(48.dp)
