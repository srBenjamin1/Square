package dev.lelonio.square.ui.local

/** Window dimensions in dp, independent of the device's resolution. */
internal data class LocalLayoutPolicy(val compact: Boolean, val albumSize: Float, val songSize: Float, val artistSize: Float) {
    companion object {
        fun forWindow(width: Int, height: Int): LocalLayoutPolicy {
            val compact = width > height && height < 480
            val album = if (compact) (height * .30f).coerceIn(96f, 132f) else 152f
            return LocalLayoutPolicy(compact, album, if (compact) (album * .85f).coerceAtLeast(88f) else 128f,
                if (compact) (album * .75f).coerceAtLeast(80f) else 110f)
        }
    }
}
