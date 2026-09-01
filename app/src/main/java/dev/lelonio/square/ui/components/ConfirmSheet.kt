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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lelonio.square.ui.glass.LocalGlassEffectConfig
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import dev.lelonio.square.ui.glass.liquidGlass
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.glass.shapes.ContinuousCapsule
import dev.lelonio.square.ui.glass.shapes.ContinuousRoundedRectangle
import dev.lelonio.square.ui.player.GlassInk
import dev.lelonio.square.ui.player.GlassInkDim

/**
 * One question with two answers, in the app's own glass.
 *
 * The same panel the update alert is built from, without the parts that made
 * that one specific: a title, a line saying what is about to happen, and two
 * capsules. Deleting a library's worth of downloaded music is exactly the kind
 * of act that deserves a moment's pause, and a system dialog in the middle of a
 * page made of glass would look like it came from another app.
 */
@Composable
fun ConfirmSheet(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    backdrop: Backdrop,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Red rather than accent, for an answer that takes something away. */
    destructive: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(DIM)
                .pressable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(tween(220), initialScale = 0.93f) + fadeIn(tween(220)),
                exit = scaleOut(tween(150), targetScale = 0.95f) + fadeOut(tween(150)),
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .liquidGlass(
                            config = LocalGlassEffectConfig.current,
                            shape = PANEL,
                            ownBackdrop = backdrop,
                            onDrawTint = { drawRect(LIFT) },
                        )
                        // Nothing behind the panel takes a tap through it.
                        .pressable(onClick = {}),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = GlassInk,
                        modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 24.dp),
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassInkDim,
                        modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 8.dp),
                    )
                    Row(
                        Modifier
                            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 22.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Action(
                            label = cancelLabel,
                            container = FILM,
                            content = GlassInk,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        Action(
                            label = confirmLabel,
                            container = if (destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            content = Color.White,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(
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

private val DIM = Color(0xFF121212).copy(alpha = 0.62f)
private val FILM = Color(0xFF121212).copy(alpha = 0.4f)
private val LIFT = Color.White.copy(alpha = 0.08f)
private val PANEL = ContinuousRoundedRectangle(40.dp)
