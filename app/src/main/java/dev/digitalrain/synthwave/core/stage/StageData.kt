package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val stageJson = Json { ignoreUnknownKeys = false }

const val BROADCAST_CAMERA = "CAM | Broadcast group"

/** The camera in the stage file that follows [member] for the member cam. */
fun fancamName(member: MemberId): String = when (member) {
    MemberId.FRONTMAN -> "CAM | Frontman fancam"
    MemberId.KEYBOARDIST -> "CAM | Producer fancam"
    MemberId.KEYTAR -> "CAM | Keytar fancam"
    MemberId.DRUMMER -> "CAM | Drummer fancam"
}

/** stage.json from art/blender/export_live_stage.py. Colours are linear; positions in glTF axes. */
@Serializable
data class StageInfo(
    val fps: Int,
    val frames: Int,
    val areaLights: List<AreaLight>,
    val cameras: Map<String, OrthoCamera>,
    val concepts: Map<ConceptId, ConceptLook>,
) {
    companion object {
        fun parse(json: String): StageInfo = stageJson.decodeFromString(json)
    }
}

/** A Blender area light, which glTF can't carry; the app stands a soft spot light in its place. */
@Serializable
data class AreaLight(
    val name: String,
    val color: List<Float>,
    val watts: Float,
    val position: List<Float>,
    val direction: List<Float>,
)

@Serializable
data class OrthoCamera(val ymag: Float)

/** A concept's suit colours and, per light, its colour and its level as a factor of the export's. */
@Serializable
data class ConceptLook(val suits: List<List<Float>>, val lights: Map<String, LightLook>)

@Serializable
data class LightLook(val color: List<Float>, val factor: Float)

/** One mix's light levels (Blender watts) and emission strengths, one value per frame. */
@Serializable
data class StageTracks(val fps: Int, val frames: Int, val lights: List<LightTrack>, val emissive: List<EmissiveTrack>) {
    companion object {
        fun parse(json: String): StageTracks = stageJson.decodeFromString(json)
    }
}

@Serializable
data class LightTrack(val light: String, val watts: List<Float>)

/** [strength] is glTF emissive strength, already multiplied by the brightness of [color]. */
@Serializable
data class EmissiveTrack(val material: String, val color: List<Float>, val strength: List<Float>)

/** [values] (one per frame at [fps]) at [time] seconds, blended between frames, held at the ends. */
fun sampleTrack(values: FloatArray, time: Float, fps: Int): Float {
    val position = (time * fps).coerceIn(0f, (values.size - 1).toFloat())
    val index = position.toInt()
    val next = minOf(index + 1, values.size - 1)
    return values[index] + (values[next] - values[index]) * (position - index)
}
