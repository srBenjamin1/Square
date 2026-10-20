package dev.lelonio.square.thor.display

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import dev.lelonio.square.thor.ui.ThorActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ThorDisplayInfo(
    val id: Int,
    val name: String,
    val state: Int,
    val flags: Int,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
) {
    val isPresentation: Boolean get() = flags and Display.FLAG_PRESENTATION != 0
    val isOn: Boolean get() = state == Display.STATE_ON
}

data class ThorDisplays(
    val primaryId: Int,
    val secondaryId: Int?,
    val available: List<ThorDisplayInfo>,
)

class ThorDisplayManager(context: Context) {
    private val appContext = context.applicationContext
    private val displayManager = appContext.getSystemService(DisplayManager::class.java)
    private val preferences = appContext.getSharedPreferences("square_preferences", Context.MODE_PRIVATE)
    private val _displays = MutableStateFlow(readDisplays())
    val displays: StateFlow<ThorDisplays> = _displays.asStateFlow()

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    init {
        displayManager.registerDisplayListener(listener, null)
    }

    fun refresh() {
        _displays.value = readDisplays()
    }

    fun close() {
        displayManager.unregisterDisplayListener(listener)
    }

    fun setManualSelection(playerDisplayId: Int?, panelDisplayId: Int?) {
        preferences.edit()
            .apply {
                if (playerDisplayId == null) remove("thor_player_display")
                else putString("thor_player_display", playerDisplayId.toString())
                if (panelDisplayId == null) remove("thor_panel_display")
                else putString("thor_panel_display", panelDisplayId.toString())
            }
            .apply()
        refresh()
    }

    /** Launches only the secondary surface; the owning activity stays on display 0. */
    fun launchPanel(activity: Activity): Boolean {
        val pair = readDisplays()
        val panelDisplayId = pair.secondaryId ?: return false
        val panel = pair.available.firstOrNull { it.id == panelDisplayId }
        if (panel == null || !panel.isOn) {
            Log.w(TAG, "Panel launch deferred: display $panelDisplayId is ${panel?.stateName()}")
            return false
        }
        return try {
            activity.startActivity(
                Intent(activity, ThorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(ThorActivity.EXTRA_ROLE, ThorActivity.ROLE_PANEL)
                    .putExtra(ThorActivity.EXTRA_DISPLAY_ID, panelDisplayId),
                ActivityOptions.makeBasic().setLaunchDisplayId(panelDisplayId).toBundle(),
            )
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Firmware denied panel launch on display $panelDisplayId", error)
            false
        } catch (error: android.content.ActivityNotFoundException) {
            Log.e(TAG, "Thor panel activity is not available", error)
            false
        }
    }

    fun closePair(context: Context) {
        context.sendBroadcast(
            Intent(ThorActivity.ACTION_CLOSE).setPackage(context.packageName),
        )
    }

    private fun readDisplays(): ThorDisplays {
        val available = displayManager.displays.map { display ->
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)
            ThorDisplayInfo(
                id = display.displayId,
                name = display.name,
                state = display.state,
                flags = display.flags,
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
            )
        }
        available.forEach { display ->
            Log.i(
                TAG,
                "display id=${display.id} name=${display.name} state=${display.stateName()} " +
                    "flags=0x${display.flags.toString(16)} presentation=${display.isPresentation} " +
                    "size=${display.width}x${display.height} density=${display.densityDpi}",
            )
        }

        val defaultId = Display.DEFAULT_DISPLAY
        val configuredPlayer = preferences.getString("thor_player_display", null)?.toIntOrNull()
        val configuredPanel = preferences.getString("thor_panel_display", null)?.toIntOrNull()
        val player = available.firstOrNull { it.id == configuredPlayer }
            ?: available.firstOrNull { it.id == defaultId }
        val panel = available.firstOrNull { it.id == configuredPanel && it.id != player?.id }
            ?: available.firstOrNull {
                it.id != player?.id && it.isPresentation && it.isOn
            }
            ?: available.firstOrNull { it.id != player?.id && it.isOn }

        if (player?.state == Display.STATE_OFF) {
            Log.w(TAG, "Primary Thor display ${player.id} is OFF; Android exposes it but cannot be woken by this app")
        }
        if (panel == null) {
            Log.w(TAG, "No usable secondary presentation display found")
        }
        return ThorDisplays(player?.id ?: defaultId, panel?.id, available)
    }

    private fun ThorDisplayInfo.stateName() = when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        else -> state.toString()
    }

    companion object {
        private const val TAG = "ThorDisplayManager"
    }
}
