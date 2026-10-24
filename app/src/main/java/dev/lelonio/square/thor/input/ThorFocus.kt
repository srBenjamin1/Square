package dev.lelonio.square.thor.input

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode as ComposeInputMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

/** An activity-local registry. Never shared between displays. */
class ThorFocusRegistry {
    private data class Target(val state: ScrollableState, val orientation: Orientation,
        var bounds: Rect, val priority: Int)
    private val targets = linkedMapOf<Any, Target>()
    private var activeScroll: Target? = null
    private data class Focus(val bounds: Rect, val owner: Any?, val scope: String?, val localArrows: Boolean,
                             val allArrows: Boolean)
    private val focused = mutableMapOf<Any, Focus>()
    private data class Candidate(val owner: Any, val scope: String?, val request: () -> Boolean, val priority: Int)
    private data class Scene(val owner: Any, val key: String, val priority: Int, val enter: () -> Boolean)
    private val candidates = mutableMapOf<Any, Candidate>()
    private val scenes = linkedMapOf<Any, Scene>()
    private val lastInScene = mutableMapOf<Pair<Any, String>, Any>()
    fun candidate(id: Any, owner: Any, scope: String?, request: (() -> Boolean)?, priority: Int = 0) {
        if (request == null) candidates.remove(id) else candidates[id] = Candidate(owner, scope, request, priority)
    }
    fun scene(id: Any, owner: Any, key: String, priority: Int, enter: (() -> Boolean)?) {
        if (enter == null) scenes.remove(id) else scenes[id] = Scene(owner, key, priority, enter)
    }
    fun activeScene(owner: Any): Any? = scenes.entries.filter {
        it.value.owner === owner && (modals.isEmpty() || it.value.priority >= 2)
    }.maxWithOrNull(compareBy<Map.Entry<Any, Scene>> { it.value.priority }
        .thenBy { scenes.keys.indexOf(it.key) })?.key
    fun enterScene(owner: Any): Boolean = scenes[activeScene(owner)]?.enter?.invoke() ?: false
    fun restore(owner: Any, scope: String): Boolean {
        val id = lastInScene[owner to scope]
        if (candidates[id]?.takeIf { it.owner === owner && it.scope == scope }?.request?.invoke() == true) return true
        return candidates.values.filter { it.owner === owner && it.scope == scope && it.priority > 0 }
            .sortedByDescending { it.priority }.any { it.request() }
    }
    fun handlesDirectionLocally(direction: AnalogNavigation.Direction): Boolean {
        val current = focused.values.lastOrNull()
        return current?.allArrows == true ||
            ((direction == AnalogNavigation.Direction.LEFT || direction == AnalogNavigation.Direction.RIGHT) &&
                (adjustments.isNotEmpty() || current?.localArrows == true))
    }
    private val modals = mutableMapOf<Any, Int>()
    fun modal(window: Any, active: Boolean) {
        val count = (modals[window] ?: 0) + if (active) 1 else -1
        if (count > 0) modals[window] = count else modals.remove(window)
    }
    private val adjustments = mutableSetOf<Any>()
    fun adjusting(id: Any, active: Boolean) {
        if (active) adjustments.add(id) else adjustments.remove(id)
    }
    val hasFocus get() = focused.isNotEmpty()
    fun hasFocusIn(owner: Any) = focused.values.any { it.owner === owner }
    fun focus(id: Any, bounds: Rect?, owner: Any? = null, scope: String? = null, localArrows: Boolean = false,
              allArrows: Boolean = false) {
        if (bounds != null && id !in focused) activeScroll = null
        if (bounds == null) focused.remove(id) else {
            focused[id] = Focus(bounds, owner, scope, localArrows, allArrows)
            if (owner != null && scope != null) lastInScene[owner to scope] = id
        }
    }
    fun register(id: Any, state: ScrollableState, orientation: Orientation, priority: Int) {
        targets[id] = Target(state, orientation, Rect.Zero, priority)
    }
    fun bounds(id: Any, bounds: Rect) { targets[id]?.bounds = bounds }
    fun remove(id: Any) { targets.remove(id) }
    fun scroll(x: Float, y: Float) {
        if (adjustments.isNotEmpty()) { activeScroll = null; return }
        val horizontal = abs(x) > abs(y)
        val amount = if (horizontal) x else y
        if (abs(amount) <= 0.25f) { activeScroll = null; return }
        val candidates = targets.values.filter {
            it.orientation == (if (horizontal) Orientation.Horizontal else Orientation.Vertical) &&
                it.bounds.width > 0 && it.bounds.height > 0
        }
        val priority = maxOf(if (modals.isEmpty()) 0 else 2, targets.values.maxOfOrNull { it.priority } ?: return)
        val point = focused.values.lastOrNull()?.bounds?.center
        val previous = activeScroll?.takeIf { old -> candidates.any { it === old } && old.priority == priority }
        val target = previous ?: candidates.filter { it.priority == priority }
            .sortedWith(compareByDescending<Target> { point != null && it.bounds.contains(point) }
                .thenBy { it.bounds.width * it.bounds.height }).firstOrNull() ?: return
        activeScroll = target
        // Exactly one container receives the delta; no synthetic mouse or cursor.
        val velocity = (abs(amount) - 0.25f) / 0.75f
        target.state.dispatchRawDelta(velocity * velocity * 24f * if (amount < 0) -1f else 1f)
    }
}

/** Extra reveal space for content underneath floating controls; no input mapping changes. */
val LocalThorObscuredBottom = staticCompositionLocalOf { 0.dp }

val LocalThorFocusRegistry = staticCompositionLocalOf<ThorFocusRegistry?> { null }
val LocalThorScrollPriority = staticCompositionLocalOf { 0 }
val LocalThorFocusScene = staticCompositionLocalOf<String?> { null }

internal fun AnalogNavigation.Direction.asFocusDirection(): FocusDirection = when (this) {
    AnalogNavigation.Direction.UP -> FocusDirection.Up
    AnalogNavigation.Direction.DOWN -> FocusDirection.Down
    AnalogNavigation.Direction.LEFT -> FocusDirection.Left
    AnalogNavigation.Direction.RIGHT -> FocusDirection.Right
}

fun Modifier.thorFocusGroup(): Modifier = composed {
    if (LocalThorEnabled.current) this.focusRestorer().focusGroup() else this
}

/** Observes focus and touch without changing selection or consuming pointer events. */
fun Modifier.thorFocus(shape: Shape? = null, enabled: Boolean = true, localArrows: Boolean = false,
                       priority: Int = 0, allArrows: Boolean = false, square: Boolean = false): Modifier = composed {
    if (!LocalThorEnabled.current) return@composed this
    val registry = LocalThorFocusRegistry.current
    val mode by ThorInteraction.mode.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    val identity = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val scope = LocalThorFocusScene.current
    val target = remember { FocusRequester() }
    val bounds = remember { arrayOf(Rect.Zero) }
    val view = LocalView.current
    val manager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    val session = LocalThorInputSession.current
    val window = remember(view) { ThorWindowInput.dialogWindow(view) }
    DisposableEffect(window, session, registry) {
        val dispatcher = session?.dispatcher
        val release = if (window != null && dispatcher != null) {
            registry?.modal(window, true)
            session.modalWindows.attach(window, dispatcher, moveFocus = { direction ->
                inputMode.requestInputMode(ComposeInputMode.Keyboard)
                if (registry?.hasFocusIn(view) != true) manager.moveFocus(FocusDirection.Next)
                manager.moveFocus(direction.asFocusDirection())
            }) {
                inputMode.requestInputMode(ComposeInputMode.Keyboard)
                if (registry?.hasFocusIn(view) != true) manager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)
            }
        } else null
        onDispose {
            release?.invoke()
            if (release != null && window != null) registry?.modal(window, false)
        }
    }
    DisposableEffect(registry, view, scope, enabled, priority) {
        registry?.candidate(identity, view, scope, if (enabled) {
            { try { target.requestFocus() } catch (_: IllegalStateException) { false } }
        } else null, priority)
        onDispose { registry?.candidate(identity, view, scope, null) }
    }
    val requester = remember { BringIntoViewRequester() }
    val obscuredBottom = with(androidx.compose.ui.platform.LocalDensity.current) { LocalThorObscuredBottom.current.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    val reveal by animateFloatAsState(if (focused && mode == InputMode.GAMEPAD) 1f else 0f,
        tween(if (mode == InputMode.TOUCH) 0 else 110), label = "thorFocus")
    LaunchedEffect(focused, mode, obscuredBottom) {
        if (focused && mode == InputMode.GAMEPAD) {
            if (obscuredBottom > 0) requester.bringIntoView(Rect(0f, 0f, bounds[0].width, bounds[0].height + obscuredBottom))
            else requester.bringIntoView()
        }
    }
    DisposableEffect(registry) { onDispose { registry?.focus(identity, null) } }
    this
        .focusRequester(target)
        // SystemDefined clickable targets reject focus in Android touch mode.
        // Keep the existing target (no duplicate focusable), including when the
        // outline is hidden. Disabled controls still cannot receive focus.
        .focusProperties { canFocus = enabled }
        .bringIntoViewRequester(requester)
        .onGloballyPositioned {
            bounds[0] = it.boundsInWindow()
            if (focused) registry?.focus(identity, bounds[0], view, scope, localArrows, allArrows)
        }
        .onFocusChanged {
            focused = it.isFocused
            registry?.focus(identity, bounds[0].takeIf { _ -> it.isFocused }, view, scope, localArrows, allArrows)
            if (it.isFocused && dev.lelonio.square.BuildConfig.DEBUG) android.util.Log.d("ThorFocus",
                "display=${view.display?.displayId} scope=$scope node=${identity.take(8)} focus=true bounds=${bounds[0]}")
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                ThorInteraction.touch()
            }
        }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (ThorInputDispatcher.isController(key)) ThorInteraction.gamepad()
            // Dialogs and popups own their Android window. Translate locally too,
            // so confirmation and contextual back reach the native Compose controls.
            val translated = when (key.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
                else -> null
            }
            if (translated != null) {
                view.rootView.dispatchKeyEvent(ThorInputDispatcher.replaceKey(key, translated))
                true
            } else false
        }
        .drawWithContent {
            drawContent()
            if (reveal > 0f) {
                val inset = 2.dp.toPx()
                val targetShape = shape ?: if (size.width <= 64.dp.toPx() && abs(size.width - size.height) < 2.dp.toPx()) {
                    CircleShape
                } else RoundedCornerShape(18.dp)
                val outline = targetShape.createOutline(Size((size.width - inset * 2).coerceAtLeast(0f),
                    ((if (square) minOf(size.width, size.height) else size.height) - inset * 2).coerceAtLeast(0f)), layoutDirection, this)
                withTransform({ translate(inset, inset) }) {
                    drawOutline(outline, accent.copy(alpha = 0.13f * reveal), style = Stroke(9.dp.toPx()))
                    drawOutline(outline, Brush.linearGradient(listOf(accent.copy(alpha = reveal),
                        Color.White.copy(alpha = 0.65f * reveal), accent.copy(alpha = reveal))), style = Stroke(2.dp.toPx()))
                }
            }
        }
}

fun Modifier.thorScrollTarget(state: ScrollableState, orientation: Orientation): Modifier = composed {
    if (!LocalThorEnabled.current) return@composed this
    val registry = LocalThorFocusRegistry.current ?: return@composed this
    val priority = LocalThorScrollPriority.current +
        if (ThorWindowInput.dialogWindow(LocalView.current) != null) 2 else 0
    val id = remember { Any() }
    DisposableEffect(registry, state, orientation, priority) {
        registry.register(id, state, orientation, priority)
        onDispose { registry.remove(id) }
    }
    this.onGloballyPositioned { registry.bounds(id, it.boundsInWindow()) }
}

fun Modifier.thorAdjustable(
    value: () -> Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    step: Float = (range.endInclusive - range.start) / 40f,
    allArrows: Boolean = false,
    onChange: (Float) -> Unit,
): Modifier = composed {
    if (!LocalThorEnabled.current) return@composed this
    val registry = LocalThorFocusRegistry.current
    val adjustment = remember { Any() }
    DisposableEffect(registry) { onDispose { registry?.adjusting(adjustment, false) } }
    this.thorFocus(enabled = enabled, localArrows = true, allArrows = allArrows)
        .onFocusChanged { registry?.adjusting(adjustment, it.isFocused) }
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(value(), range)
            setProgress { if (enabled) onChange(it.coerceIn(range)); enabled }
        }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val direction = when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> 0
            }
            if (enabled && direction != 0) {
                if (key.action == KeyEvent.ACTION_DOWN) onChange((value() + direction * step).coerceIn(range))
                true
            } else false
        }.focusable(enabled)
}
