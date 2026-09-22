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
    val loopPath: String,
)

@Serializable
data class MemberAsset(val member: MemberId, val stillPath: String)

@Serializable
data class AssetManifest(
    val audio: List<AudioAsset>,
    val concepts: List<ConceptAssets>,
    val members: List<MemberAsset>,
    val controlRoomPath: String,
)
