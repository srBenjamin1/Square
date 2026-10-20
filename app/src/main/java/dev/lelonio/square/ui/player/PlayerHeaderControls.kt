package dev.lelonio.square.ui.player

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.regular.Broadcast
import com.adamglin.phosphoricons.regular.Repeat
import com.adamglin.phosphoricons.regular.RepeatOnce
import com.adamglin.phosphoricons.regular.Shuffle
import dev.lelonio.square.R
import dev.lelonio.square.ui.glass.backdrop.Backdrop

@Composable
internal fun FavoriteIcon(liked: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (liked) PhosphorIcons.Fill.Heart else PhosphorIcons.Regular.Heart,
        stringResource(if (liked) R.string.remove_from_liked else R.string.liked_songs),
        tint = if (liked) SavedInk else GlassInk,
        modifier = modifier.size(20.dp),
    )
}

@Composable
internal fun FavoriteControl(liked: Boolean, onToggle: (() -> Unit)?, backdrop: Backdrop) {
    RoundGlassButton(
        backdrop, 44.dp,
        modifier = Modifier
            .semantics { selected = liked },
        enabled = onToggle != null,
        onClick = { onToggle?.invoke() },
    ) { FavoriteIcon(liked) }
}

@Composable
internal fun ShuffleControl(
    state: PlaybackState,
    onClick: () -> Unit,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ToggleIcon(
        icon = PhosphorIcons.Regular.Shuffle,
        description = stringResource(if (state.smartShuffle) R.string.smart_shuffle else R.string.shuffle_play),
        active = state.shuffleEnabled,
        smart = state.smartShuffle,
        onClick = onClick,
        backdrop = backdrop,
        modifier = if (backdrop == null) modifier else modifier
            .semantics { selected = state.shuffleEnabled },
        enabled = enabled,
    )
}

@Composable
internal fun RepeatControl(
    state: PlaybackState,
    onClick: () -> Unit,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ToggleIcon(
        icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) PhosphorIcons.Regular.RepeatOnce
            else PhosphorIcons.Regular.Repeat,
        description = stringResource(R.string.repeat),
        active = state.repeatMode != Player.REPEAT_MODE_OFF,
        onClick = onClick,
        backdrop = backdrop,
        modifier = if (backdrop == null) modifier else modifier
            .semantics { selected = state.repeatMode != Player.REPEAT_MODE_OFF },
        enabled = enabled,
    )
}

@Composable
internal fun SongRadioControl(
    onClick: (() -> Unit)?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    header: Boolean = false,
) {
    RoundGlassButton(
        backdrop, if (header) 44.dp else 40.dp,
        modifier = modifier,
        enabled = onClick != null,
        onClick = { onClick?.invoke() },
    ) {
        Icon(PhosphorIcons.Regular.Broadcast, stringResource(R.string.radio),
            tint = GlassInk, modifier = Modifier.size(20.dp))
    }
}
