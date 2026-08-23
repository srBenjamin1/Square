package dev.lelonio.square.backend.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The video a Spotify track has, when it has one.
 *
 * A track that comes with a video carries the id of it in the metadata the
 * context player already receives — `media.manifest_id`. That id is all this
 * needs: one request returns everything about the video, and everything about
 * the video is templates rather than a list of files.
 *
 * The shape is Spotify's own, not DASH: a set of profiles (the quality ladder),
 * two CDN roots, and two URL templates with `{{profile_id}}`, `{{file_type}}`
 * and `{{segment_timestamp}}` in them. [toMpd] turns that into the DASH a
 * player understands; see the note there for why translating beats writing a
 * media source by hand.
 */
object SpotifyVideo {

    /** Where the manifest lives. The access point's own host answers it too. */
    private const val HOST = "https://spclient.wg.spotify.com"

    private val http = OkHttpClient()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads the manifest for a video file id.
     *
     * The listener's ordinary access token is enough: the client token every
     * Spotify client also sends is not asked for here, which was worth
     * checking before building anything on top.
     */
    suspend fun manifest(fileId: String, accessToken: String): VideoManifest =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$HOST/manifests/v9/json/sources/$fileId/options/supports_drm")
                .header("authorization", "Bearer $accessToken")
                .header("accept", "application/json")
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) {
                    "manifest ${response.code} for $fileId"
                }
                json.decodeFromString<VideoManifest>(body)
            }
        }
}

@Serializable
data class VideoManifest(
    val contents: List<VideoContent> = emptyList(),
    @SerialName("base_urls") val baseUrls: List<String> = emptyList(),
    @SerialName("initialization_template") val initTemplate: String = "",
    @SerialName("segment_template") val segmentTemplate: String = "",
    @SerialName("end_time_millis") val endTimeMillis: Long = 0,
    val seekpanels: SeekPanels? = null,
)

/**
 * The thumbnails behind the scrubber: sheets of small frames, in the clear.
 *
 * Worth more than a preview here. The video itself is decrypted into a
 * protected buffer that nothing can read back — which is what stops the glow
 * the video player draws for YouTube — while these are ordinary JPEGs on a
 * CDN, one frame a second along the whole song. Sampled, they give the same
 * effect from the same pictures, without ever touching the protected path.
 */
@Serializable
data class SeekPanels(
    @SerialName("base_urls") val baseUrls: List<String> = emptyList(),
    val templates: List<String> = emptyList(),
    val variants: List<SeekPanelVariant> = emptyList(),
)

@Serializable
data class SeekPanelVariant(
    val id: String = "0",
    @SerialName("frame_width") val frameWidth: Int = 0,
    @SerialName("frame_height") val frameHeight: Int = 0,
    val rows: Int = 0,
    val columns: Int = 0,
)

/**
 * Where the thumbnail for a moment lives, and where it sits in that sheet.
 *
 * One frame a second, laid out in sheets of `rows * columns`: the second gives
 * the frame, the frame gives the sheet and the square inside it.
 */
data class PanelFrame(val url: String, val left: Int, val top: Int, val width: Int, val height: Int)

fun VideoManifest.panelAt(positionMs: Long): PanelFrame? {
    val panels = seekpanels ?: return null
    val base = panels.baseUrls.firstOrNull() ?: return null
    val template = panels.templates.firstOrNull() ?: return null
    val variant = panels.variants.firstOrNull() ?: return null
    if (variant.rows <= 0 || variant.columns <= 0) return null

    val perSheet = variant.rows * variant.columns
    val frame = (positionMs / 1000L).toInt().coerceAtLeast(0)
    val sheet = frame / perSheet
    val within = frame % perSheet

    val url = base.trimEnd('/') + "/" + template
        .replace("{{variant_id}}", variant.id)
        .replace("{{panel_id}}", sheet.toString())

    return PanelFrame(
        url = url,
        left = (within % variant.columns) * variant.frameWidth,
        top = (within / variant.columns) * variant.frameHeight,
        width = variant.frameWidth,
        height = variant.frameHeight,
    )
}

@Serializable
data class VideoContent(
    @SerialName("encoding_id") val encodingId: String = "",
    /** Seconds of media in each segment, and the step the template counts by. */
    @SerialName("segment_length") val segmentLength: Int = 4,
    @SerialName("end_time_millis") val endTimeMillis: Long = 0,
    val profiles: List<VideoProfile> = emptyList(),
    @SerialName("encryption_infos") val encryption: List<VideoEncryption> = emptyList(),
)

@Serializable
data class VideoProfile(
    val id: Int = 0,
    @SerialName("file_type") val fileType: String = "",
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("video_codec") val videoCodec: String? = null,
    @SerialName("audio_codec") val audioCodec: String? = null,
    @SerialName("video_width") val width: Int = 0,
    @SerialName("video_height") val height: Int = 0,
    @SerialName("video_bitrate") val videoBitrate: Int = 0,
    @SerialName("audio_bitrate") val audioBitrate: Int = 0,
    @SerialName("max_bitrate") val maxBitrate: Int = 0,
)

@Serializable
data class VideoEncryption(
    @SerialName("key_system") val keySystem: String = "",
    @SerialName("encryption_scheme") val scheme: String = "",
    @SerialName("license_server_endpoint") val licenseEndpoint: String = "",
    /** The PSSH box, base64, exactly as a player wants to be handed it. */
    @SerialName("encryption_data") val encryptionData: String = "",
)

/** Widevine's own, which is what an Android device can play. */
val VideoManifest.widevine: VideoEncryption?
    get() = contents.firstOrNull()?.encryption?.firstOrNull { it.keySystem == "widevine" }

/** Where the licence for this video is asked for. */
fun VideoManifest.licenseUrl(): String? =
    widevine?.licenseEndpoint?.let { "https://spclient.wg.spotify.com$it" }

/**
 * The same video, written as DASH.
 *
 * Translating rather than writing a media source by hand, and the reason is
 * that this manifest already *is* DASH in everything but spelling: a duration,
 * fixed-length segments, a ladder of renditions, and one template per kind of
 * file. What DASH calls a SegmentTemplate with `$Time$` is what Spotify calls
 * `{{segment_timestamp}}`, counting in seconds; the rest is renaming.
 *
 * The mp4 profiles only. They carry the same content as the WebM ones and are
 * encrypted the way every DASH player already knows how to decrypt — common
 * encryption, with the key box handed over in the manifest below.
 */
fun VideoManifest.toMpd(): String {
    val content = contents.firstOrNull() ?: error("manifest with no content")
    val base = baseUrls.firstOrNull() ?: error("manifest with no CDN")
    val seconds = content.segmentLength.coerceAtLeast(1)
    val duration = (content.endTimeMillis.takeIf { it > 0 } ?: endTimeMillis) / 1000.0

    val mp4 = content.profiles.filter { it.fileType == "mp4" }
    val video = mp4.filter { it.mimeType.startsWith("video/") }.sortedBy { it.videoBitrate }
    val audio = mp4.filter { it.mimeType.startsWith("audio/") }
    check(video.isNotEmpty() && audio.isNotEmpty()) { "no mp4 ladder in this manifest" }

    // A representation is named by its profile id, which is exactly what the
    // template wants where it says `{{profile_id}}` — so the two line up
    // without a table to keep in step.
    fun template(source: String) = source
        .replace("{{profile_id}}", "\$RepresentationID\$")
        .replace("{{file_type}}", "mp4")
        .replace("{{segment_timestamp}}", "\$Time\$")
        .xml()

    val protection = widevine?.let { widevine ->
        """
        <ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc"/>
        <ContentProtection schemeIdUri="urn:uuid:$WIDEVINE_UUID">
          <cenc:pssh>${widevine.encryptionData}</cenc:pssh>
        </ContentProtection>
        """.trimIndent()
    }.orEmpty()

    val segments = """
        <SegmentTemplate timescale="1" duration="$seconds" startNumber="0"
          initialization="${template(initTemplate)}"
          media="${template(segmentTemplate)}"/>
    """.trimIndent()

    fun representation(profile: VideoProfile): String = if (profile.width > 0) {
        """<Representation id="${profile.id}" mimeType="video/mp4" codecs="${profile.videoCodec}" """ +
            """width="${profile.width}" height="${profile.height}" """ +
            """bandwidth="${profile.videoBitrate.takeIf { it > 0 } ?: profile.maxBitrate}"/>"""
    } else {
        """<Representation id="${profile.id}" mimeType="audio/mp4" codecs="${profile.audioCodec}" """ +
            """audioSamplingRate="44100" """ +
            """bandwidth="${profile.audioBitrate.takeIf { it > 0 } ?: profile.maxBitrate}">""" +
            """<AudioChannelConfiguration """ +
            """schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>""" +
            """</Representation>"""
    }

    return """
<?xml version="1.0" encoding="utf-8"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" xmlns:cenc="urn:mpeg:cenc:2013"
     profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static"
     mediaPresentationDuration="PT${"%.3f".format(java.util.Locale.ROOT, duration)}S" minBufferTime="PT4S">
  <Period>
    <BaseURL>${base.xml()}</BaseURL>
    <AdaptationSet contentType="video" segmentAlignment="true" startWithSAP="1">
      $protection
      $segments
      ${video.joinToString("\n      ") { representation(it) }}
    </AdaptationSet>
    <AdaptationSet contentType="audio" segmentAlignment="true" startWithSAP="1" lang="und">
      $protection
      $segments
      ${audio.joinToString("\n      ") { representation(it) }}
    </AdaptationSet>
  </Period>
</MPD>
""".trimIndent()
}

/** Widevine's system id, as DASH spells it. */
private const val WIDEVINE_UUID = "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"

/**
 * The signed URLs carry query strings, and a bare `&` ends an XML document.
 */
private fun String.xml(): String = replace("&", "&amp;")
