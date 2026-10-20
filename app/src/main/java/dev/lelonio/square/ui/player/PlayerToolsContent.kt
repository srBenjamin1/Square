package dev.lelonio.square.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.Equalizer
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Info
import com.adamglin.phosphoricons.regular.Queue
import com.adamglin.phosphoricons.regular.SlidersHorizontal
import dev.lelonio.square.R
import dev.lelonio.square.backend.spotify.SpotifyCredits
import dev.lelonio.square.playback.EffectPreset
import dev.lelonio.square.ui.glass.LiquidBottomTab
import dev.lelonio.square.ui.glass.LiquidBottomTabs
import dev.lelonio.square.ui.glass.LiquidButton
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import dev.lelonio.square.ui.settings.EqualizerPage

/** Presentation only: every role consumes the same session and playback state. */
enum class PlayerVisualRole { STANDARD, THOR_TOP, THOR_BOTTOM_TOOLS }

enum class PlayerTool(val title: Int, val icon: ImageVector) {
    QUEUE(R.string.queue, PhosphorIcons.Regular.Queue),
    EQUALIZER(R.string.equalizer, PhosphorIcons.Regular.Equalizer),
    EFFECTS(R.string.effects, PhosphorIcons.Regular.SlidersHorizontal),
    INFO(R.string.credits, PhosphorIcons.Regular.Info),
    SETTINGS(R.string.settings, PhosphorIcons.Regular.GearSix),
}

/** The original player's tools, without composing its artwork or transport. */
@Composable
internal fun PlayerToolsContent(
    state: PlaybackState,
    onCollapse: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onRadio: (() -> Unit)?,
    queue: List<QueueEntry>,
    queueActions: QueueActions,
    credits: SpotifyCredits.Credits?,
    creditsLoading: Boolean,
    onWantCredits: (String?) -> Unit,
    reverb: Float,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onReverb: (Float) -> Unit,
    presets: List<EffectPreset>,
    onApplyPreset: (EffectPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    backdrop: Backdrop,
    settings: @Composable () -> Unit,
    tool: PlayerTool,
    onToolChange: (PlayerTool) -> Unit,
) {
    val selected = rememberUpdatedState(tool.ordinal)
    val selectedIndex = remember { { selected.value } }
    val sdkPlayback by dev.lelonio.square.playback.websdk.WebSdkRecovery.inUse
        .collectAsStateWithLifecycle()
    LaunchedEffect(tool, state.mediaId) {
        if (tool == PlayerTool.INFO) onWantCredits(state.mediaId)
    }

    DismissibleScreen(onDismiss = onCollapse, modifier = Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(
            dev.lelonio.square.thor.input.LocalThorScrollPriority provides 1,
        ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LiquidButton(onClick = onCollapse, backdrop = backdrop) {
                    Icon(PhosphorIcons.Regular.CaretDown, stringResource(R.string.close))
                }
                Text(
                    stringResource(tool.title),
                    style = MaterialTheme.typography.titleLarge,
                    color = GlassInk,
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tool == PlayerTool.QUEUE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ShuffleControl(state, onToggleShuffle, backdrop,
                            enabled = state.hasItem)
                        SongRadioControl(onRadio.takeIf { state.hasItem }, backdrop, header = true)
                        RepeatControl(state, onCycleRepeat, backdrop, enabled = state.hasItem)
                    }
                }
            }
            GlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(24.dp),
                surfaceColor = LocalPlayerFilm.current,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                AnimatedContent(targetState = tool, label = "playerTool") { shown ->
                    dev.lelonio.square.thor.input.ThorFocusScene("tools:$shown", Modifier.fillMaxSize()) {
                        when (shown) {
                            PlayerTool.QUEUE -> QueueList(queue, queueActions)
                            PlayerTool.EQUALIZER -> if (sdkPlayback) {
                                EmptyPanel(stringResource(R.string.web_sdk_limits))
                            } else EqualizerPage(PaddingValues(0.dp), header = {})
                            PlayerTool.EFFECTS -> if (sdkPlayback) {
                                EmptyPanel(stringResource(R.string.web_sdk_limits))
                            } else EffectsPanel(
                                speed = state.speed,
                                pitch = state.pitch,
                                reverb = reverb,
                                onSpeed = onSpeed,
                                onPitch = onPitch,
                                onReverb = onReverb,
                                presets = presets,
                                onApplyPreset = onApplyPreset,
                                onSavePreset = onSavePreset,
                                onDeletePreset = onDeletePreset,
                                backdrop = backdrop,
                            )
                            PlayerTool.INFO -> CreditsView(credits, creditsLoading)
                            PlayerTool.SETTINGS -> settings()
                        }
                    }
                }
            }
            // The same glass tabs as the original player. One list drives both
            // the slot count and the actual buttons, so their indices agree.
            LiquidBottomTabs(
                selectedTabIndex = selectedIndex,
                onTabSelected = { onToolChange(PlayerTool.entries[it]) },
                backdrop = backdrop,
                tabsCount = PlayerTool.entries.size,
                accentColor = GlassInk,
                containerColor = LocalPlayerFilm.current,
                indicatorColor = GlassInk.copy(alpha = 0.18f),
                height = 42.dp,
                lensDepth = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlayerTool.entries.forEach { item ->
                    LiquidBottomTab(onClick = { onToolChange(item) }) {
                        Icon(
                            item.icon,
                            contentDescription = stringResource(item.title),
                            tint = if (tool == item) GlassInk else GlassInkDim,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
        }
    }
}
