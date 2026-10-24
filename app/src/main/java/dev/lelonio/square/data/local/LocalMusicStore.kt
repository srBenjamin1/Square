package dev.lelonio.square.data.local

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A single atomic snapshot, using the project's existing JSON persistence approach. */
class LocalMusicStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun read(): LocalMusicSnapshot = if (file.isFile) json.decodeFromString(file.readText()) else LocalMusicSnapshot()
    fun write(snapshot: LocalMusicSnapshot) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { stream ->
            stream.write(json.encodeToString(snapshot).toByteArray())
            stream.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
