package dev.lelonio.square.data.local

/** Adding a file to a remote queue requires an explicit new play, never a mixed timeline. */
object LocalQueuePolicy {
    fun canAppend(current: String?, incoming: String): Boolean = current.isNullOrBlank() ||
        current.startsWith("local:track:") == incoming.startsWith("local:track:")
}
