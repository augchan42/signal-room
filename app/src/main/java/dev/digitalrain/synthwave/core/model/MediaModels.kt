package dev.digitalrain.synthwave.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioAsset(
    val id: String,
    val path: String,
    val arrangement: ArrangementId,
    val member: MemberId,
    val sha256: String,
)

@Serializable
data class ConceptAssets(
    val concept: ConceptId,
    val stillPath: String,
    val stillSha256: String,
    val loopPath: String,
    val loopSha256: String,
)

@Serializable
data class MemberAsset(val member: MemberId, val stillPath: String, val sha256: String)

@Serializable
data class AssetManifest(
    val schemaVersion: Int,
    val provenance: String,
    val audio: List<AudioAsset>,
    val concepts: List<ConceptAssets>,
    val members: List<MemberAsset>,
    val controlRoomPath: String,
    val controlRoomSha256: String,
)
