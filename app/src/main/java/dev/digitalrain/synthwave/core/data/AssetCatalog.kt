package dev.digitalrain.synthwave.core.data

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.AssetManifest
import dev.digitalrain.synthwave.core.model.AudioAsset
import dev.digitalrain.synthwave.core.model.ConceptAssets
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberAsset
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Selection
import dev.digitalrain.synthwave.core.stage.StageRequest
import kotlinx.serialization.json.Json

private val assetJson = Json { ignoreUnknownKeys = false }

fun loadAssetManifest(json: String): AssetManifest = assetJson.decodeFromString(json)

fun interface AssetOpener {
    /** Must attempt to open and read the bundled asset, not merely trust its manifest entry. */
    fun canOpen(path: String): Boolean
}

class AssetProblem(
    val assetId: String,
    val kind: Kind,
    reason: String,
) : IllegalStateException("$assetId: $reason") {
    enum class Kind { AUDIO, VIDEO, STILL }
}

sealed interface VisualPlayback {
    data class Performance(val path: String) : VisualPlayback
    data class Loop(val path: String) : VisualPlayback
    data class Still(val path: String) : VisualPlayback
}

sealed interface AssetRecovery {
    data class BlockBooking(val assetId: String) : AssetRecovery
    data class UseStill(val assetId: String, val stillPath: String) : AssetRecovery
}

class AssetCatalog(
    private val manifest: AssetManifest,
    private val opener: AssetOpener,
) {
    init {
        require(manifest.schemaVersion == 3) { "Unsupported asset manifest version" }
    }

    fun audio(selection: Selection): AudioAsset {
        val id = audioId(selection.arrangement, selection.member)
        val asset = manifest.audio.singleOrNull {
            it.arrangement == selection.arrangement && it.member == selection.member && it.id == id
        } ?: throw AssetProblem(id, AssetProblem.Kind.AUDIO, "not in manifest")
        checkOpen(asset.path, id, AssetProblem.Kind.AUDIO)
        return asset
    }

    fun concept(id: ConceptId): ConceptAssets {
        val asset = manifest.concepts.singleOrNull { it.concept == id }
            ?: throw AssetProblem("concept-${id.name.lowercase()}", AssetProblem.Kind.STILL, "not in manifest")
        checkOpen(asset.stillPath, "concept-${id.name.lowercase()}", AssetProblem.Kind.STILL)
        return asset
    }

    fun member(id: MemberId): MemberAsset {
        val asset = manifest.members.singleOrNull { it.member == id }
            ?: throw AssetProblem("member-${id.name.lowercase()}", AssetProblem.Kind.STILL, "not in manifest")
        checkOpen(asset.stillPath, "member-${id.name.lowercase()}", AssetProblem.Kind.STILL)
        return asset
    }

    fun controlRoom(): String {
        checkOpen(manifest.controlRoomPath, "control-room", AssetProblem.Kind.STILL)
        return manifest.controlRoomPath
    }

    /** The band playing the selected mix in the selected concept; else the concept's loop; else its still. */
    fun visualPlayback(selection: Selection): VisualPlayback {
        val asset = concept(selection.concept)
        val performance = manifest.performances.singleOrNull {
            it.arrangement == selection.arrangement && it.member == selection.member && it.concept == selection.concept
        }
        return when {
            performance != null && opener.canOpen(performance.path) -> VisualPlayback.Performance(performance.path)
            opener.canOpen(asset.loopPath) -> VisualPlayback.Loop(asset.loopPath)
            else -> VisualPlayback.Still(asset.stillPath)
        }
    }

    /** The live stage files for this show, or null if any of them can't be opened. */
    fun liveStage(selection: Selection): StageRequest? {
        val live = manifest.live
        val mix = live.mixes.singleOrNull { it.arrangement == selection.arrangement && it.member == selection.member }
            ?: return null
        val screen = live.screens.singleOrNull { it.concept == selection.concept } ?: return null
        val paths = listOf(live.stagePath, live.infoPath, mix.motionPath, mix.tracksPath, screen.path)
        if (!paths.all(opener::canOpen)) return null
        return StageRequest("demo/${live.stagePath}", "demo/${live.infoPath}", "demo/${mix.motionPath}",
            "demo/${mix.tracksPath}", "demo/${screen.path}", selection.concept, selection.member)
    }

    fun recoveryForDecoderError(path: String): AssetRecovery {
        manifest.audio.singleOrNull { it.path == path }?.let { return AssetRecovery.BlockBooking(it.id) }
        manifest.concepts.singleOrNull { it.loopPath == path }?.let { concept ->
            checkOpen(concept.stillPath, "concept-${concept.concept.name.lowercase()}", AssetProblem.Kind.STILL)
            return AssetRecovery.UseStill("loop-${concept.concept.name.lowercase()}", concept.stillPath)
        }
        manifest.performances.singleOrNull { it.path == path }?.let { performance ->
            val concept = concept(performance.concept)
            return AssetRecovery.UseStill(path.substringAfterLast('/').removeSuffix(".mp4"), concept.stillPath)
        }
        manifest.concepts.singleOrNull { it.stillPath == path }?.let {
            return AssetRecovery.BlockBooking("concept-${it.concept.name.lowercase()}")
        }
        manifest.members.singleOrNull { it.stillPath == path }?.let {
            return AssetRecovery.BlockBooking("member-${it.member.name.lowercase()}")
        }
        if (manifest.controlRoomPath == path) return AssetRecovery.BlockBooking("control-room")
        throw IllegalArgumentException("Decoder path not in asset manifest: $path")
    }

    private fun checkOpen(path: String, id: String, kind: AssetProblem.Kind) {
        if (!opener.canOpen(path)) throw AssetProblem(id, kind, "bundled asset cannot be opened: $path")
    }

    private fun audioId(arrangement: ArrangementId, member: MemberId): String =
        "${arrangement.name.lowercase()}-${member.name.lowercase()}"
}
