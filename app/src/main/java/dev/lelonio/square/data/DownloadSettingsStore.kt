package dev.lelonio.square.data

import android.content.Context
import androidx.annotation.StringRes
import dev.lelonio.square.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a download should be, and when one is allowed to happen.
 *
 * Separate from [QualityStore] on purpose. Streaming quality is a running
 * negotiation with the connection — [Quality.Auto] moves it up and down as the
 * link changes — and a download is the opposite: it happens once, it is kept,
 * and dropping it to 96 kbps because the Wi-Fi was briefly poor would leave a
 * bad copy on the phone for good. So there is no Auto here.
 */
enum class DownloadQuality(
    val key: String,
    @StringRes val label: Int,
    val kbps: Int,
) {
    High("high", R.string.quality_high, 320),
    Medium("medium", R.string.quality_medium, 160),
    Low("low", R.string.quality_low, 96),
    ;

    companion object {
        fun from(key: String?): DownloadQuality =
            entries.firstOrNull { it.key == key } ?: High
    }
}

class DownloadSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _quality = MutableStateFlow(DownloadQuality.from(prefs.getString(KEY_QUALITY, null)))

    /** Defaults to the best available: a download is kept, so it may as well be good. */
    val quality: StateFlow<DownloadQuality> = _quality.asStateFlow()

    fun setQuality(value: DownloadQuality) {
        if (value == _quality.value) return
        _quality.value = value
        prefs.edit().putString(KEY_QUALITY, value.key).apply()
    }

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))

    /**
     * On by default.
     *
     * A playlist is hundreds of megabytes, and the tap that starts it looks
     * exactly like every other tap in the app. Someone who means to download on
     * mobile data can say so; someone who does not should not find out from
     * their bill.
     */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        if (value == _wifiOnly.value) return
        _wifiOnly.value = value
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

    private val _likedSongs = MutableStateFlow(prefs.getBoolean(KEY_LIKED, false))

    /** Whether the whole of Liked Songs is kept offline, and kept in step. */
    val downloadLikedSongs: StateFlow<Boolean> = _likedSongs.asStateFlow()

    fun setDownloadLikedSongs(value: Boolean) {
        if (value == _likedSongs.value) return
        _likedSongs.value = value
        prefs.edit().putBoolean(KEY_LIKED, value).apply()
    }

    private val _offline = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE, false))

    /**
     * The listener's own switch, which beats anything the app works out for
     * itself. Persisted, so a phone put into offline mode is still in it after
     * a restart — the alternative is an app that quietly starts streaming again
     * on mobile data overnight.
     */
    val offlineMode: StateFlow<Boolean> = _offline.asStateFlow()

    fun setOfflineMode(value: Boolean) {
        if (value == _offline.value) return
        _offline.value = value
        prefs.edit().putBoolean(KEY_OFFLINE, value).apply()
    }

    private companion object {
        const val FILE_NAME = "square_downloads"
        const val KEY_QUALITY = "quality"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_LIKED = "liked_songs"
        const val KEY_OFFLINE = "offline_mode"
    }
}
