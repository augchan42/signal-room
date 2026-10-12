package dev.digitalrain.synthwave.core.data

import dev.digitalrain.synthwave.core.model.RunSnapshot

interface SnapshotStore {
    suspend fun load(): RunSnapshot?
    suspend fun save(snapshot: RunSnapshot)
}
