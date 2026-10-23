package dev.lelonio.square.update

import kotlinx.serialization.json.*
import java.net.URI

/** Stable SquareDS channel. The suffix is a fork revision, not a SemVer prerelease. */
internal object ReleasePolicy {
    const val REPO = "srBenjamin1/SquareDS"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_URL = "https://github.com/$REPO/releases"
    private val forkVersion = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)-squareds\\.([1-9][0-9]*)$")
    private val legacyVersion = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")

    data class Release(val version: String, val url: String, val bytes: Long, val notes: String)

    private fun parts(value: String, legacy: Boolean = false): List<Long>? {
        val normalized = value.removePrefix("v")
        val match = forkVersion.matchEntire(normalized)
            ?: (if (legacy) legacyVersion.matchEntire(normalized) else null)
            ?: return null
        val numbers = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
        return if (numbers.size == 3) numbers + 0L else numbers
    }

    fun isNewer(candidate: String, installed: String): Boolean {
        val next = parts(candidate) ?: return false
        val current = parts(installed, legacy = true) ?: return false
        for (i in next.indices) {
            if (next[i] != current[i]) return next[i] > current[i]
        }
        return false
    }

    fun requireAssetUrl(url: String) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) { "Invalid SquareDS asset URL" }
        val path = uri.rawPath.removePrefix("/$REPO/releases/download/")
        require(uri.rawPath.startsWith("/$REPO/releases/download/") && path.split('/').size == 2 &&
            path.split('/').all { it.isNotBlank() && '%' !in it && it != "." && it != ".." } &&
            path.endsWith(".apk")) { "Asset is not a SquareDS release APK" }
    }

    fun parseResponse(status: Int, text: String, abis: List<String>, installed: String): Release? {
        check(status in 200..299) { "HTTP $status" }
        val body = Json.parseToJsonElement(text).jsonObject
        if (body["draft"]?.jsonPrimitive?.boolean == true ||
            body["prerelease"]?.jsonPrimitive?.boolean == true) return null
        val tag = body["tag_name"]?.jsonPrimitive?.contentOrNull ?: error("Missing release tag")
        // Published Thor builds predate the fork's numeric version convention.
        if (tag in setOf("thor-v1-controls", "thor-v1.1-spatial-navigation")) return null
        require(parts(tag) != null) { "Invalid SquareDS version" }
        require(parts(installed, legacy = true) != null) { "Invalid installed version" }
        if (!isNewer(tag, installed)) return null
        val version = tag.removePrefix("v")
        val assets = (body["assets"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
        val asset = (abis + "universal").distinct().firstNotNullOfOrNull { abi ->
            assets.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == "squareds-$version-$abi.apk" }
        } ?: error("No compatible SquareDS APK")
        val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: error("Missing APK URL")
        requireAssetUrl(url)
        val name = asset.getValue("name").jsonPrimitive.content
        require(URI(url).rawPath == "/$REPO/releases/download/$tag/$name") { "APK does not match release" }
        val bytes = asset["size"]?.jsonPrimitive?.longOrNull ?: error("Missing APK size")
        require(bytes > 0L) { "Empty APK" }
        return Release(version, url, bytes, body["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim())
    }
}
