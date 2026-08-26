package dev.lelonio.square.backend.lyrics

/**
 * Trims what upload titles carry and song titles do not.
 *
 * Uploads are named for a video — "(Official Video)", "[4K Remaster]",
 * "(Lyrics)" — and searching a lyrics database for any of that finds nothing.
 * Every source in this package matches on the words of the title, so they all
 * want the same cleaning.
 */
internal fun String.songTitle(): String =
    replace(NOISE, "").replace(WHITESPACE, " ").trim()

private val NOISE = Regex(
    "\\((?:official|lyric|audio|video|visualizer|hd|4k|mv|m/v)[^)]*\\)" +
        "|\\[[^\\]]*(?:official|lyric|audio|video|remaster|hd|4k)[^\\]]*\\]",
    RegexOption.IGNORE_CASE,
)
private val WHITESPACE = Regex("\\s+")
