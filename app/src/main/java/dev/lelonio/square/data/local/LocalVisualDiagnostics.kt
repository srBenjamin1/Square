package dev.lelonio.square.data.local

import android.util.Log
import dev.lelonio.square.BuildConfig

/** Deliberately omits metadata, paths, URLs, bearer tokens and exception messages. */
object LocalVisualDiagnostics {
    enum class Stage {
        METADATA_READY, SEARCH_RESULTS, VIDEO_METADATA, MISSING_METADATA, NO_MATCH, AMBIGUOUS, ALBUM_IDENTIFIED, NO_MOTION,
        MOTION_AVAILABLE, CACHE_HIT, ANIMATION_DISABLED, OFFLINE,
        BLOCKED_VISUAL_MODE, LOAD_ERROR, FIRST_FRAME
    }
    fun record(stage: Stage, key: String, count: Int? = null, code: Int? = null) {
        if (BuildConfig.DEBUG) Log.d("SquareDSLocalVisual", "stage=$stage key=${key.hashCode().toUInt().toString(16)}" +
            (count?.let { " count=$it" } ?: "") + (code?.let { " code=$it" } ?: ""))
    }
}
