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

/** The band playing one mix (arrangement and featured member) in one concept's lighting, 20 seconds. */
@Serializable
data class PerformanceAsset(
    val arrangement: ArrangementId,
    val member: MemberId,
    val concept: ConceptId,
    val path: String,
    val sha256: String,
)

@Serializable
data class MemberAsset(val member: MemberId, val stillPath: String, val sha256: String)

/** One mix's motion and light levels for the live stage, from art/blender/export_live_stage.py. */
@Serializable
data class LiveMixAsset(
    val arrangement: ArrangementId,
    val member: MemberId,
    val motionPath: String,
    val motionSha256: String,
    val tracksPath: String,
    val tracksSha256: String,
)

/** The rear-screen image of one concept on the live stage. */
@Serializable
data class LiveScreenAsset(val concept: ConceptId, val path: String, val sha256: String)

/** The shared live stage and what each mix and concept adds to it. */
@Serializable
data class LiveStageAssets(
    val stagePath: String,
    val stageSha256: String,
    val infoPath: String,
    val infoSha256: String,
    val screens: List<LiveScreenAsset>,
    val mixes: List<LiveMixAsset>,
)

@Serializable
data class AssetManifest(
    val schemaVersion: Int,
    val provenance: String,
    val audio: List<AudioAsset>,
    val concepts: List<ConceptAssets>,
    val performances: List<PerformanceAsset>,
    val members: List<MemberAsset>,
    val controlRoomPath: String,
    val controlRoomSha256: String,
    val live: LiveStageAssets,
)
