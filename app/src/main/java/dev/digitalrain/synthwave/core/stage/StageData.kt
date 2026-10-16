package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val stageJson = Json { ignoreUnknownKeys = false }

const val BROADCAST_CAMERA = "CAM | Broadcast group"

/**
 * What the member cam frames: a window of the broadcast picture centred on [x], [y] (fractions of
 * the 9:16 frame's width and height, from the top left) and [fieldWidth] of the frame wide. These
 * are the close-ups the video version cut from the performance video.
 */
data class CamFocus(val x: Float, val y: Float, val fieldWidth: Float)

fun camFocus(member: MemberId): CamFocus = when (member) {
    MemberId.FRONTMAN -> CamFocus(0.585f, 0.515f, 0.13f) // hair, face and mic
    MemberId.KEYTAR -> CamFocus(0.78f, 0.565f, 0.18f) // both hands on the keytar
    MemberId.KEYBOARDIST -> CamFocus(0.21f, 0.54f, 0.16f) // hands on the BORG keys
    MemberId.DRUMMER -> CamFocus(0.47f, 0.47f, 0.22f) // sticks on the pads
}

/**
 * Orthographic bounds (left, right, bottom, top) for a [width] x [height] view that shows [focus]
 * through the broadcast camera, whose half height is [broadcastHalfHeight].
 */
fun camWindow(focus: CamFocus, broadcastHalfHeight: Float, width: Int, height: Int): FloatArray {
    val frameHalfWidth = broadcastHalfHeight * 9 / 16
    val centreX = (focus.x * 2 - 1) * frameHalfWidth
    val centreY = (1 - focus.y * 2) * broadcastHalfHeight
    val halfWidth = focus.fieldWidth * frameHalfWidth
    val halfHeight = halfWidth * height / width
    return floatArrayOf(centreX - halfWidth, centreX + halfWidth, centreY - halfHeight, centreY + halfHeight)
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
