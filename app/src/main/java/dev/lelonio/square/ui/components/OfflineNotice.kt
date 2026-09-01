package dev.lelonio.square.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CloudSlash
import dev.lelonio.square.R
import dev.lelonio.square.playback.OfflineMode
import dev.lelonio.square.ui.glass.pressable

/**
 * A line saying the app is offline, and why.
 *
 * Present rather than modal on purpose. Offline is a state the listener browses
 * in for as long as it lasts, not an error to acknowledge, and a dialog in the
 * way of a library that still works would be the wrong shape entirely.
 *
 * Saying *why* is the whole point of it. "No connection", "the connection is
 * worse than your downloads" and "you turned this on" call for three different
 * things from the person reading, and an app that only says "offline" leaves
 * them to guess which one they are in.
 */
@Composable
fun OfflineNotice(
    modifier: Modifier = Modifier,
    /**
     * Tries the servers again, and answers whether it worked.
     *
     * Suspending because the whole point is the wait: a handshake takes a
     * second or two, and a button that returns instantly and changes nothing
     * looks broken whichever way it went.
     */
    onRetry: (suspend () -> Boolean)? = null,
) {
    val reason by OfflineMode.reason.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var trying by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = reason != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                PhosphorIcons.Regular.CloudSlash,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(
                    when (reason) {
                        OfflineMode.Reason.MANUAL -> R.string.offline_banner_manual
                        OfflineMode.Reason.SLOW -> R.string.offline_banner_slow
                        else -> R.string.offline_banner_no_network
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            // The way back. It really does try the servers rather than only
            // clearing the flag: offline because the network went is the common
            // case, and a phone that has since found a signal has no other way
            // of being told to look again.
            onRetry?.let { retry ->
                Text(
                    stringResource(
                        if (trying) R.string.offline_retrying else R.string.go_online,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (trying) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            // Inert while it is already trying, so a second tap
                            // does not start a second handshake.
                            if (trying) {
                                Modifier
                            } else {
                                Modifier.pressable(
                                    onClick = {
                                        trying = true
                                        scope.launch {
                                            retry()
                                            trying = false
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    pressedScale = 0.96f,
                                )
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
