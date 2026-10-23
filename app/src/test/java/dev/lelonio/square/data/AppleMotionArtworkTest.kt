package dev.lelonio.square.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class AppleMotionArtworkTest {
    @Test fun squareVariantIsKeptAlongsideTallVariant() {
        // Shape and preview dimensions verified on Apple's public GUTS album page.
        val attributes = Json.parseToJsonElement("""{"editorialVideo":{
            "motionDetailTall":{"previewFrame":{"width":2048,"height":2732},"video":"https://example/tall.m3u8"},
            "motionDetailSquare":{"previewFrame":{"width":3840,"height":3840},"video":"https://example/square.m3u8"}
        }}""").jsonObject
        assertEquals("https://example/square.m3u8", AppleCatalog.squareMotionUrl(attributes))
    }
    @Test fun missingNonSquareOrInvalidResourcesDoNotMasqueradeAsSquare() {
        for (body in listOf("{}", """{"editorialVideo":{}}""",
            """{"editorialVideo":{"motionDetailSquare":{"video":""}}}""",
            """{"editorialVideo":{"motionDetailSquare":{"video":"http://example/square.m3u8"}}}""",
            """{"editorialVideo":{"motionDetailSquare":{"video":"https://example/tall.m3u8","previewFrame":{"width":2048,"height":2732}}}}""",
            """{"editorialVideo":{"motionDetailSquare":{"video":"https://example/x.m3u8","previewFrame":{"width":0,"height":0}}}}""")) {
            assertNull(AppleCatalog.squareMotionUrl(Json.parseToJsonElement(body).jsonObject))
        }
    }
    @Test fun oldDownloadedArtworkRemainsReadable() {
        val old = Json.decodeFromString<AppleCatalog.Album>("""{"heroUrl":null,"coverUrl":"still","motionUrl":"https://example/tall.m3u8"}""")
        assertNull(old.motionSquareUrl)
        assertEquals("https://example/tall.m3u8", old.motionUrl)
    }
}
