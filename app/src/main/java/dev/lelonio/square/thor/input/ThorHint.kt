package dev.lelonio.square.thor.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lelonio.square.ui.glass.LocalGlassEffectConfig
import dev.lelonio.square.ui.glass.liquidGlass
import dev.lelonio.square.ui.glass.backdrop.Backdrop

val LocalThorHintBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Informational glass only: no click handler, pointer interceptor or focus target. */
@Composable
fun ThorHint(label: String, modifier: Modifier = Modifier) {
    if (!LocalThorEnabled.current) return
    val mode by ThorInteraction.mode.collectAsStateWithLifecycle()
    if (mode != InputMode.GAMEPAD) return
    val backdrop = LocalThorHintBackdrop.current ?: return
    Box(modifier.liquidGlass(LocalGlassEffectConfig.current, RoundedCornerShape(8.dp), ownBackdrop = backdrop)
        .padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text(label, fontSize = 9.sp, lineHeight = 11.sp,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
