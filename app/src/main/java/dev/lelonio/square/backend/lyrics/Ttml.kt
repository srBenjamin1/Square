package dev.lelonio.square.backend.lyrics

import android.util.Xml
import dev.lelonio.square.data.LyricLine
import dev.lelonio.square.data.LyricWord
import dev.lelonio.square.data.Lyrics
import java.io.StringReader
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

/**
 * Timed Text Markup Language, the format synced lyrics are published in.
 *
 * TTML is what Apple Music ships its word-by-word lyrics as, and what every
 * timed-lyrics archive has settled on since. A document is a `<body>` of
 * `<div>` sections — verses — each holding `<p>` elements that are the sung
 * lines, and each line carries `begin` and `end` times. Inside a line the words
 * are usually further split into `<span>`s with their own times, which is what
 * makes karaoke highlighting possible.
 *
 * Square's lyrics model is a flat list of lines, each carrying its text, its own
 * `begin`, and the words it was built from with their moments. A line with no
 * `begin` of its own takes the first time inside it, so a document that places
 * only its spans still yields placed lines.
 *
 * Nothing here raises. A document that is not TTML, or is TTML with no sung
 * text, answers null the same way a lyrics lookup that found nothing does.
 */
object Ttml {

    /**
     * @return null when [raw] is not a TTML document, or carries no lines.
     */
    fun parse(raw: String): Lyrics? {
        val document = runCatching { read(raw) }.getOrNull() ?: return null
        val lines = document.lines
        if (lines.isEmpty()) return null

        // Every line must be placed. A document that times some of its lines
        // and not others would leave the view highlighting one line while
        // another is being sung, which reads worse than no timing at all — and
        // this source is only asked because its timing is the better one, so an
        // untimed document is a miss and the caller falls through to LrcLib or
        // Spotify rather than losing the sync it would have had.
        if (lines.any { it.startMs == null }) return null

        return Lyrics(withGaps(lines, chosen(document.translations)), synced = true)
    }

    /** One `<p>`: when it starts, when it stops, and its words, timed and joined. */
    private class Line(
        val startMs: Long?,
        val endMs: Long?,
        val text: String,
        val words: List<LyricWord> = emptyList(),
        /** `itunes:key`, which is how a translation names the line it is for. */
        val key: String? = null,
    )

    private fun read(raw: String): Document {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(raw))

        val lines = mutableListOf<Line>()
        val translations = mutableMapOf<String, MutableMap<String, String>>()
        var inBody = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    // Times also appear in the head, on styles and regions;
                    // only the body holds anything sung.
                    "body" -> inBody = true
                    "p" -> if (inBody) lines += readLine(parser)
                    // Sits in the head, beside the songwriters, and never in
                    // the body — so it is read whichever order the two come in.
                    "translation" -> readTranslation(parser, translations)
                }

                XmlPullParser.END_TAG -> if (parser.name == "body") inBody = false
            }
        }
        return Document(lines, translations)
    }

    /** A parsed document: the sung lines, and the translations offered for them. */
    private class Document(
        val lines: List<Line>,
        /** Language tag to the line keys it translates. */
        val translations: Map<String, Map<String, String>>,
    )

    /**
     * Reads one `<translation>` block into [into], keyed by its language.
     *
     * A document can carry several — a label ships the subtitle tracks it has —
     * and each is a flat list of `<text for="L4">` naming the line it belongs
     * to by the `itunes:key` that line carries. Leaves the parser on the
     * closing tag.
     */
    private fun readTranslation(
        parser: XmlPullParser,
        into: MutableMap<String, MutableMap<String, String>>,
    ) {
        val language = parser.getAttributeValue(XML_NS, "lang")
            ?: parser.getAttributeValue(null, "lang")
            ?: return
        val texts = into.getOrPut(language) { mutableMapOf() }

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "text") {
                        val key = parser.getAttributeValue(null, "for")
                        val text = runCatching { parser.nextText() }.getOrNull()
                        // nextText leaves the parser past the closing tag it
                        // read, so that tag is no longer coming.
                        if (text != null) depth--
                        if (key != null && !text.isNullOrBlank()) texts[key] = text.trim()
                    }
                }

                XmlPullParser.END_TAG -> depth--

                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }
    }

    /**
     * The translation to show, out of everything the document offers.
     *
     * Only the listener's own language. A document that carries English
     * subtitles for somebody reading Italian is offering the wrong thing, and
     * the caller can translate the song itself: better a machine's Italian than
     * a label's English underneath a Spanish line.
     */
    private fun chosen(translations: Map<String, Map<String, String>>): Map<String, String> {
        if (translations.isEmpty()) return emptyMap()
        val wanted = Locale.getDefault().language
        return translations.entries
            .firstOrNull { it.key.substringBefore('-').equals(wanted, ignoreCase = true) }
            ?.value
            .orEmpty()
    }

    /**
     * Reads one line, with the parser sitting on its `<p>`.
     *
     * The spans inside are the words, each with its own moment, and they are
     * kept as well as joined: the view sweeps a highlight across a line word by
     * word where a source has actually timed them, and falls back to lighting
     * the whole line where it has not. `<br/>` is a line break the model has no
     * room for, so it comes back as a space. Leaves the parser on the closing
     * `</p>`.
     */
    private fun readLine(parser: XmlPullParser): Line {
        val startMs = time(parser.getAttributeValue(null, "begin"))
        val endMs = time(parser.getAttributeValue(null, "end"))
        val key = parser.getAttributeValue(null, "key")

        val text = StringBuilder()
        val words = mutableListOf<LyricWord>()
        // The first time found inside the line, for documents that place only
        // their words and leave the line itself unplaced.
        var firstSpanMs: Long? = null
        // The span the text being read belongs to, if it is a timed one.
        var wordStart: Long? = null
        var wordEnd: Long? = null
        var depth = 1

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val begin = time(parser.getAttributeValue(null, "begin"))
                    if (firstSpanMs == null) firstSpanMs = begin
                    if (parser.name == "span") {
                        wordStart = begin
                        wordEnd = time(parser.getAttributeValue(null, "end"))
                    }
                    // A break is a space, not a join: without this the last word
                    // of one half runs into the first of the next.
                    if (parser.name == "br") text.append(' ')
                }

                XmlPullParser.END_TAG -> {
                    depth--
                    if (parser.name == "span") {
                        wordStart = null
                        wordEnd = null
                    }
                }

                XmlPullParser.TEXT -> {
                    val piece = parser.text.orEmpty()
                    text.append(piece)
                    val start = wordStart
                    // The whitespace between two spans is a span's text as far
                    // as the parser is concerned, and it is not a word.
                    if (start != null && piece.isNotBlank()) {
                        // A span that says when it starts and not when it stops
                        // is rare; a short guess beats dropping the word, since
                        // the next word's start ends the highlight anyway.
                        words += LyricWord(start, wordEnd ?: (start + WORD_MS), piece.trim())
                    }
                }

                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }

        return Line(
            startMs = startMs ?: firstSpanMs,
            endMs = endMs,
            // TTML collapses whitespace unless a document asks otherwise, and
            // the indentation between spans is whitespace: left alone, every
            // line arrives padded with the newlines of the source file.
            text = WHITESPACE.replace(text, " ").trim(),
            words = words,
            key = key,
        )
    }

    /**
     * Keeps the view honest through the instrumental stretches.
     *
     * A line holds highlighted until the next one starts, so a solo between two
     * verses would leave the last words sung sitting lit for half a minute.
     * TTML says when a line ends, which is exactly what is needed to close it:
     * an empty line at that moment, whenever the silence that follows is long
     * enough to notice. This is the same shape LRC files are written in by
     * hand, and the view already draws those blanks as a gap.
     */
    private fun withGaps(lines: List<Line>, translations: Map<String, String>): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        lines.forEachIndexed { index, line ->
            out += LyricLine(
                startTimeMs = line.startMs,
                text = line.text,
                words = line.words,
                translation = line.key?.let(translations::get),
            )

            val endMs = line.endMs ?: return@forEachIndexed
            val nextMs = lines.getOrNull(index + 1)?.startMs
            // Nothing after the last line, so close it either way.
            if (nextMs == null || nextMs - endMs >= GAP_MS) {
                out += LyricLine(startTimeMs = endMs, text = "")
            }
        }
        return out
    }

    /** How long a word is assumed to last when its span does not say. */
    private const val WORD_MS = 300L

    /** Silence shorter than this is the breath between two lines, not a gap. */
    private const val GAP_MS = 3_000L

    /**
     * A TTML time expression in milliseconds, or null if it is not one.
     *
     * Two forms are in use: a clock time, `[hh:]mm:ss[.fff]`, and an offset,
     * a number with a unit — `12.5s`, `1200ms` — whose unit Apple leaves off,
     * writing plain seconds. The frame-based clock form
     * (`hh:mm:ss:ff`) needs a frame rate off the document to mean anything and
     * no lyrics publisher writes it, so it is left unread.
     */
    private fun time(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null

        CLOCK.matchEntire(text)?.let { match ->
            val (hours, minutes, seconds) = match.destructured
            return (hours.ifEmpty { "0" }.toLong() * 3_600_000) +
                (minutes.toLong() * 60_000) +
                Math.round(seconds.toDouble() * 1_000)
        }

        OFFSET.matchEntire(text)?.let { match ->
            val (amount, unit) = match.destructured
            val millis = when (unit) {
                "h" -> 3_600_000.0
                "m" -> 60_000.0
                // Apple writes its times as bare seconds — "37.700", no unit —
                // and the documents this reads are Apple's, so a number on its
                // own is seconds rather than something to refuse.
                "s", "" -> 1_000.0
                "ms" -> 1.0
                else -> return null
            }
            return Math.round(amount.toDouble() * millis)
        }

        return null
    }

    private val CLOCK = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{1,2}(?:\\.\\d+)?)")
    private val OFFSET = Regex("(\\d+(?:\\.\\d+)?)(ms|h|m|s)?")
    private val WHITESPACE = Regex("\\s+")

    /** Where `xml:lang` lives, which is the one namespace never declared. */
    private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
}
