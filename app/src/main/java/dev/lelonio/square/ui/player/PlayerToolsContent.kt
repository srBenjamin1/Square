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

private enum class Tool(val title: Int, val icon: ImageVector) {
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
) {
    var tool by rememberSaveable { mutableStateOf(Tool.QUEUE) }
    val selected = rememberUpdatedState(tool.ordinal)
    val selectedIndex = remember { { selected.value } }
    val queueControlsFocus = remember { FocusRequester() }
    LaunchedEffect(tool, state.hasItem) {
        if (tool == Tool.QUEUE && state.hasItem) queueControlsFocus.requestFocus()
    }
    val sdkPlayback by dev.lelonio.square.playback.websdk.WebSdkRecovery.inUse
        .collectAsStateWithLifecycle()
    LaunchedEffect(tool, state.mediaId) {
        if (tool == Tool.INFO) onWantCredits(state.mediaId)
    }

    DismissibleScreen(onDismiss = onCollapse, modifier = Modifier.fillMaxSize()) {
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
                if (tool == Tool.QUEUE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ShuffleControl(state, onToggleShuffle, backdrop,
                            Modifier.focusRequester(queueControlsFocus), enabled = state.hasItem)
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
                    Box(Modifier.fillMaxSize()) {
                        when (shown) {
                            Tool.QUEUE -> QueueList(queue, queueActions)
                            Tool.EQUALIZER -> if (sdkPlayback) {
                                EmptyPanel(stringResource(R.string.web_sdk_limits))
                            } else EqualizerPage(PaddingValues(0.dp), header = {})
                            Tool.EFFECTS -> if (sdkPlayback) {
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
                            Tool.INFO -> CreditsView(credits, creditsLoading)
                            Tool.SETTINGS -> settings()
                        }
                    }
                }
            }
            // The same glass tabs as the original player. One list drives both
            // the slot count and the actual buttons, so their indices agree.
            LiquidBottomTabs(
                selectedTabIndex = selectedIndex,
                onTabSelected = { tool = Tool.entries[it] },
                backdrop = backdrop,
                tabsCount = Tool.entries.size,
                accentColor = GlassInk,
                containerColor = LocalPlayerFilm.current,
                indicatorColor = GlassInk.copy(alpha = 0.18f),
                height = 42.dp,
                lensDepth = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tool.entries.forEach { item ->
                    LiquidBottomTab(onClick = { tool = item }) {
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
