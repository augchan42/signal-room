package dev.digitalrain.synthwave.core.data

import dev.digitalrain.synthwave.core.model.RunSnapshot
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val snapshotJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
}

class FileSnapshotStore(
    private val directory: Path,
    private val atomicMove: (Path, Path) -> Unit = { from, to ->
        Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        Unit
    },
) : SnapshotStore {
    private val target: Path = directory.resolve("run-snapshot.json")

    override suspend fun load(): RunSnapshot? {
        if (!Files.exists(target)) return null
        val bytes = Files.readAllBytes(target)
        return snapshotJson.decodeFromString(String(bytes, StandardCharsets.UTF_8))
    }

    override suspend fun save(snapshot: RunSnapshot) {
        Files.createDirectories(directory)
        val bytes = snapshotJson.encodeToString(snapshot).toByteArray(StandardCharsets.UTF_8)
        val temp = Files.createTempFile(directory, ".run-snapshot-", ".tmp")
        try {
            FileChannel.open(temp, WRITE, TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            atomicMove(temp, target)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
