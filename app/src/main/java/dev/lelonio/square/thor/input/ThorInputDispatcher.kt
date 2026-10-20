package dev.lelonio.square.thor.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** The lower activity's only gamepad dispatcher. Touch never passes through it. */
class ThorInputDispatcher(
    private val session: ThorInputSession,
    private val dispatch: (KeyEvent) -> Boolean,
    private val back: () -> Unit,
    private val displayId: () -> Int = { -1 },
) {
    private val handler = Handler(Looper.getMainLooper())
    private val navigation = AnalogNavigation()
    private val leftTrigger = TriggerLatch()
    private val rightTrigger = TriggerLatch()
    private var x = 0f
    private var y = 0f
    private var hatX = 0f
    private var hatY = 0f
    private var lastSyntheticKey = 0
    private var lastSyntheticAt = -100L
    private var rightX = 0f
    private var rightY = 0f
    private var polling = false
    private var destination = dispatch
    private var focusDestination = session.ensureFocus
    private var moveDestination = session.moveFocus
    private var lastMotionLog = 0L
    private val pressedDpad = mutableSetOf<Int>()
    fun routedKey(event: KeyEvent): Boolean = if (isController(event)) {
        session.modalWindows.key(event) ?: key(event)
    } else false
    fun routedMotion(event: MotionEvent): Boolean = if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
        session.modalWindows.motion(event) ?: motion(event)
    } else false
    private val tick = object : Runnable {
        override fun run() {
            if (!polling) return
            if (ThorInteraction.mode.value != InputMode.GAMEPAD) { stop(); return }
            val now = SystemClock.uptimeMillis()
            val stickActive = maxOf(kotlin.math.abs(x), kotlin.math.abs(y)) >= 0.30f
            if (pressedDpad.isEmpty()) navigation.step(if (stickActive) x else hatX,
                if (stickActive) y else hatY, now)?.let {
                val code = when (it) {
                    AnalogNavigation.Direction.UP -> KeyEvent.KEYCODE_DPAD_UP
                    AnalogNavigation.Direction.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                    AnalogNavigation.Direction.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    AnalogNavigation.Direction.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                }
                lastSyntheticKey = code
                lastSyntheticAt = now
                if (session.handlesArrowsLocally(it)) {
                    focusDestination()
                    destination(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    destination(KeyEvent(KeyEvent.ACTION_UP, code))
                } else {
                    val moved = moveDestination(it)
                    traceMove("stick", it, moved)
                }
            }
            if (kotlin.math.abs(rightX) > 0.25f || kotlin.math.abs(rightY) > 0.25f) {
                session.scroll(rightX, rightY)
            } else session.scroll(0f, 0f)
            if (maxOf(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(hatX), kotlin.math.abs(hatY)) >= 0.30f ||
                maxOf(kotlin.math.abs(rightX), kotlin.math.abs(rightY)) > 0.25f) {
                handler.postDelayed(this, 16L)
            } else polling = false
        }
    }

    fun key(event: KeyEvent, target: (KeyEvent) -> Boolean = dispatch,
            ensureFocus: () -> Unit = session.ensureFocus, onBack: () -> Unit = back,
            moveFocus: (AnalogNavigation.Direction) -> Boolean = session.moveFocus): Boolean {
        if (!isController(event)) return false
        val code = event.keyCode
        if (code !in handledKeys) return false
        destination = target
        focusDestination = ensureFocus
        moveDestination = moveFocus
        ThorInteraction.gamepad()
        if (dev.lelonio.square.BuildConfig.DEBUG && event.repeatCount == 0) {
            android.util.Log.d("ThorInput", "phase=key targetDisplay=${displayId()} device=${event.device?.name} source=0x${event.source.toString(16)} key=$code scan=${event.scanCode} action=${event.action}")
        }
        val down = event.action == KeyEvent.ACTION_DOWN
        if (code in dpadKeys) {
            if (down) pressedDpad.add(code) else pressedDpad.remove(code)
            if (!down) { hatX = 0f; hatY = 0f; navigation.reset() }
            // Some firmware emits both HAT motion and a native DPAD key.
            // If the axis arrived first, its immediate move has already run.
            if (down && event.repeatCount == 0 && code == lastSyntheticKey &&
                event.eventTime - lastSyntheticAt in 0L..50L) return true
            val direction = directionOf(code)
            if (direction == null || (session.handlesArrowsLocally(direction))) {
                ensureFocus()
                target(event)
            } else if (down) {
                val moved = moveFocus(direction)
                traceMove("dpad", direction, moved)
            }
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                ensureFocus()
                target(replaceKey(event, KeyEvent.KEYCODE_DPAD_CENTER))
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B -> if (!down) onBack()
            KeyEvent.KEYCODE_BUTTON_L2 -> if (leftTrigger.key(down)) send(ThorCommand.PREVIOUS)
            KeyEvent.KEYCODE_BUTTON_R2 -> if (rightTrigger.key(down)) send(ThorCommand.NEXT)
            KeyEvent.KEYCODE_BUTTON_X -> Unit // Reserved for V2.
            else -> if (down && event.repeatCount == 0) when (code) {
                KeyEvent.KEYCODE_BUTTON_Y -> send(ThorCommand.PLAY_PAUSE)
                KeyEvent.KEYCODE_BUTTON_L1 -> send(ThorCommand.PREVIOUS_SECTION)
                KeyEvent.KEYCODE_BUTTON_R1 -> send(ThorCommand.NEXT_SECTION)
                KeyEvent.KEYCODE_BUTTON_START -> send(ThorCommand.SETTINGS)
                KeyEvent.KEYCODE_BUTTON_SELECT -> send(ThorCommand.SEARCH)
            }
        }
        return true
    }

    fun motion(event: MotionEvent, target: (KeyEvent) -> Boolean = dispatch,
               ensureFocus: () -> Unit = session.ensureFocus,
               moveFocus: (AnalogNavigation.Direction) -> Boolean = session.moveFocus): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return false
        destination = target
        focusDestination = ensureFocus
        moveDestination = moveFocus
        x = event.getAxisValue(MotionEvent.AXIS_X)
        y = event.getAxisValue(MotionEvent.AXIS_Y)
        hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        // The Thor's real keylayout maps ABS_Z / ABS_RZ to these axes.
        rightX = event.getAxisValue(MotionEvent.AXIS_Z)
        rightY = event.getAxisValue(MotionEvent.AXIS_RZ)
        val left = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val right = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        if (maxOf(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(rightX), kotlin.math.abs(rightY),
                kotlin.math.abs(hatX), kotlin.math.abs(hatY), left, right) > 0.30f) {
            ThorInteraction.gamepad()
        }
        val now = SystemClock.uptimeMillis()
        if (dev.lelonio.square.BuildConfig.DEBUG && now - lastMotionLog >= 500 &&
            maxOf(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(hatX), kotlin.math.abs(hatY)) > 0.30f) {
            lastMotionLog = now
            android.util.Log.d("ThorInput", "phase=motion targetDisplay=${displayId()} source=0x${event.source.toString(16)} action=${event.actionMasked} x=$x y=$y hatX=$hatX hatY=$hatY")
        }
        if (leftTrigger.axis(left)) send(ThorCommand.PREVIOUS)
        if (rightTrigger.axis(right)) send(ThorCommand.NEXT)
        if (!polling) { polling = true; handler.post(tick) }
        return true
    }

    fun stop() {
        polling = false
        handler.removeCallbacks(tick)
        session.scroll(0f, 0f)
        x = 0f; y = 0f; rightX = 0f; rightY = 0f
        hatX = 0f; hatY = 0f; lastSyntheticKey = 0; lastSyntheticAt = -100L
        navigation.reset(); leftTrigger.reset(); rightTrigger.reset(); pressedDpad.clear()
        destination = dispatch
        focusDestination = { session.ensureFocus() }
        moveDestination = { session.moveFocus(it) }
    }

    private fun traceMove(origin: String, direction: AnalogNavigation.Direction, moved: Boolean) {
        if (dev.lelonio.square.BuildConfig.DEBUG) android.util.Log.d("ThorFocus",
            "origin=$origin display=${displayId()} direction=$direction moved=$moved")
    }

    private fun send(command: ThorCommand) { session.commands.trySend(command) }

    companion object {
        private fun directionOf(code: Int): AnalogNavigation.Direction? = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> AnalogNavigation.Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> AnalogNavigation.Direction.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> AnalogNavigation.Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> AnalogNavigation.Direction.RIGHT
            else -> null
        }
        private val dpadKeys = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER)
        private val handledKeys = dpadKeys + setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT)
        fun isController(event: KeyEvent): Boolean = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_DPAD)
        fun replaceKey(event: KeyEvent, code: Int) = KeyEvent(event.downTime, event.eventTime,
            event.action, code, event.repeatCount, event.metaState, event.deviceId, event.scanCode,
            event.flags, event.source)
    }
}
