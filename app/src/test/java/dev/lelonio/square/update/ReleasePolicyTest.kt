package dev.lelonio.square.update

import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
    private val version = "2.6.1-squareds.2"
    private fun asset(abi: String, repo: String = ReleasePolicy.REPO) = """{
        "name":"squareds-$version-$abi.apk", "size":1234,
        "browser_download_url":"https://github.com/$repo/releases/download/v$version/squareds-$version-$abi.apk"
    }"""
    private fun release(assets: String = asset("arm64-v8a"), flags: String = "", tag: String = "v$version") =
        """{"tag_name":"$tag","body":" Fork notes ","assets":[$assets]$flags}"""
    private fun parse(body: String, installed: String = "2.6.1-squareds.1", abis: List<String> = listOf("arm64-v8a", "armeabi-v7a")) =
        ReleasePolicy.parseResponse(200, body, abis, installed)
    private fun fails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }

    @Test fun onlyForkEndpoints() {
        assertEquals("https://api.github.com/repos/srBenjamin1/SquareDS/releases/latest", ReleasePolicy.LATEST_URL)
        assertEquals("https://github.com/srBenjamin1/SquareDS/releases", ReleasePolicy.RELEASES_URL)
        assertEquals(ReleasePolicy.REPO, Updater.REPO)
        assertFalse(Updater.REPO.contains("Lelonio"))
    }
    @Test fun arm64PreferredRegardlessOfAssetOrderAndNotesPreserved() {
        val result = parse(release(asset("armeabi-v7a") + "," + asset("universal") + "," + asset("arm64-v8a")))!!
        assertTrue(result.url.endsWith("-arm64-v8a.apk"))
        assertEquals(1234L, result.bytes)
        assertEquals("Fork notes", result.notes)
    }
    @Test fun universalFallbackButNeverAnIncompatibleSplit() {
        assertTrue(parse(release(asset("armeabi-v7a") + "," + asset("universal")), abis = listOf("arm64-v8a"))!!.url.endsWith("-universal.apk"))
        fails { parse(release(asset("armeabi-v7a")), abis = listOf("arm64-v8a")) }
        fails { parse(release("")) }
    }
    @Test fun draftsAndPrereleasesAreIgnored() {
        assertNull(parse(release(flags = ",\"draft\":true")))
        assertNull(parse(release(flags = ",\"prerelease\":true")))
        assertFalse(ReleasePolicy.isNewer("2.6.1-squareds.2-rc.1", "2.6.1-squareds.1"))
    }
    @Test fun installedAndOlderVersionsAreNotOffered() {
        assertNull(parse(release(), installed = version))
        assertNull(parse(release(), installed = "2.6.1-squareds.10"))
        assertNull(parse(release(""), installed = version))
    }
    @Test fun numericComparisonAndLegacyMigration() {
        assertTrue(ReleasePolicy.isNewer("v2.6.1-squareds.1", "2.6.0"))
        assertTrue(ReleasePolicy.isNewer("2.6.1-squareds.10", "2.6.1-squareds.9"))
        assertTrue(ReleasePolicy.isNewer("2.10.0-squareds.1", "2.9.0-squareds.99"))
        assertFalse(ReleasePolicy.isNewer("2.6.1-squareds.20", "2.7.0-squareds.1"))
    }
    @Test fun upstreamAndInvalidTagsCannotBeUpdates() {
        for (tag in listOf("v2.6.1", "garbage", "2.6.1-squareds.0", "2.6.1-squareds.99999999999999999999999")) {
            assertFalse(ReleasePolicy.isNewer(tag, "2.6.0"))
            fails { parse(release(tag = tag)) }
        }
        assertFalse(ReleasePolicy.isNewer(version, "garbage"))
    }
    @Test fun historicalThorReleaseIsNotOfferedAgain() {
        assertNull(parse(release(tag = "thor-v1-controls")))
        assertNull(parse(release(tag = "thor-v1.1-spatial-navigation")))
    }
    @Test fun upstreamAssetsAndWrongReleaseAreRejected() {
        fails { parse(release(asset("arm64-v8a", "Lelonio/Square"))) }
        fails { parse(release().replace("download/v$version/", "download/v2.6.0/")) }
        fails { parse(release().replace("squareds-$version-arm64-v8a.apk", "square-2.6.1-arm64-v8a.apk")) }
    }
    @Test fun unsafeUrlsRejectedAtDownloadBoundary() {
        for (url in listOf("http://github.com/srBenjamin1/SquareDS/releases/download/v1/a.apk",
            "https://github.com/other/repo/releases/download/v1/a.apk",
            "https://github.com/srBenjamin1/SquareDS/releases/download/v1/../a.apk",
            "https://github.com/srBenjamin1/SquareDS/releases/download/v1/%2e%2e.apk",
            "https://github.com/srBenjamin1/SquareDS/releases/download/v1/a.apk?x=1")) {
            fails { ReleasePolicy.requireAssetUrl(url) }
        }
    }
    @Test fun httpAndMalformedResponsesFailSafely() {
        for (code in listOf(403, 404, 429, 500)) {
            fails { ReleasePolicy.parseResponse(code, "", listOf("arm64-v8a"), "2.6.0") }
        }
        for (body in listOf("", "[]", "{}", "not json", release().replace("1234", "0"))) fails { parse(body) }
    }
    @Test fun nullNotesAreEmpty() {
        assertEquals("", parse(release().replace("\" Fork notes \"", "null"))!!.notes)
    }
}
