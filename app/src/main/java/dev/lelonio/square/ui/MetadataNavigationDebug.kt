package dev.lelonio.square.ui

import android.util.Log
import dev.lelonio.square.BuildConfig

/** Temporary physical-validation trace: only phases, booleans, service and request sequence. */
internal object MetadataNavigationDebug {
    fun event(phase: String, status: String) {
        if (BuildConfig.DEBUG) Log.d("SquareMetadata", "$phase $status")
    }
}
