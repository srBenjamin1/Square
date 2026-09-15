package dev.lelonio.square.ui.components

import dev.lelonio.square.R

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Queue
import com.adamglin.phosphoricons.regular.Trash

/**
 * Wraps a row so dragging it to the left queues the track.
 *
 * A hand-rolled drag rather than Material's SwipeToDismiss: that component is
 * built around the row *leaving* the list, and here the row stays exactly where
 * it was — the gesture is a command, not a deletion. It also has to share the
 * vertical axis with a scrolling list, so only horizontal drags are consumed and
 * a mostly-vertical movement is left for the list to handle.
 *
 * The row springs back either way; the action fires once, on release, past a
 * threshold. Committing partway through the drag would fire on every
 * accidental brush of the list.
 *
 * Dragging it to the right removes it, where the list allows that: the recent
 * searches are the one list whose rows are the listener's to throw away. That
 * direction is the deletion the note above is not, so the row goes with it,
 * sliding off the side it was pushed towards. Either action can be left out,
 * and the row then does not move that way at all.
 */
@Composable
fun SwipeToQueue(
    onQueue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    // Read at release rather than captured when the gesture was set up.
    val queue by rememberUpdatedState(onQueue)
    val remove by rememberUpdatedState(onRemove)

    val threshold = with(density) { THRESHOLD.toPx() }
    val maximum = with(density) { MAX_DRAG.toPx() }

    Box(modifier) {
        // Revealed behind the row, and only drawn once the drag is far enough
        // to mean something — a label flickering under every scroll would be
        // noise.
        val progress = (abs(offset.value) / threshold).coerceIn(0f, 1f)
        if (progress > 0f) {
            val removing = offset.value > 0f
            val tint = if (removing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Row(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = 30.dp)
                    .graphicsLayer { alpha = progress },
                horizontalArrangement = if (removing) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (removing) PhosphorIcons.Regular.Trash else PhosphorIcons.Regular.Queue,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(if (removing) R.string.delete else R.string.queued),
                    style = MaterialTheme.typography.labelLarge,
                    color = tint,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(onQueue != null, onRemove != null) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val queueNow = queue
                            val removeNow = remove
                            when {
                                queueNow != null && -offset.value >= threshold -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    queueNow()
                                    scope.launch { offset.animateTo(0f, tween(220)) }
                                }
                                removeNow != null && offset.value >= threshold -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Off the edge first, then out of the list:
                                    // gone at once, it read as a row that had
                                    // jumped rather than one that was thrown.
                                    scope.launch {
                                        offset.animateTo(size.width.toFloat(), tween(180))
                                        removeNow()
                                    }
                                }
                                else -> scope.launch { offset.animateTo(0f, tween(220)) }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(220)) } },
                    ) { change, dragAmount ->
                        change.consume()
                        // Only the ways this row allows, and increasingly
                        // resistant: the row can be pulled clearly past the
                        // threshold but never far enough to look like it is
                        // being torn out of the list.
                        val next = (offset.value + dragAmount).coerceIn(
                            if (onQueue != null) -maximum else 0f,
                            if (onRemove != null) maximum else 0f,
                        )
                        scope.launch { offset.snapTo(next) }
                    }
                },
        ) {
            content()
        }
    }
}

private val THRESHOLD = 84.dp
private val MAX_DRAG = 132.dp
