package dev.digitalrain.synthwave.core.stage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * One mix's motion from art/blender/live_motion.py: what differs from the shared stage file. Each
 * moving node has one channel with a key per frame; the others only sit somewhere else.
 */
class MotionClip(val fps: Int, val frames: Int, val nodes: List<NodeMotion>) {
    /** Time of the last key, in seconds. */
    val duration: Float get() = (frames - 1).toFloat() / fps

    companion object {
        private const val MAGIC = 0x314D5253 // "SRM1", little-endian

        fun parse(bytes: ByteArray): MotionClip {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(buffer.int == MAGIC) { "Not a motion file" }
            val fps = buffer.int
            val frames = buffer.int
            val count = buffer.int
            require(fps > 0 && frames > 0 && count >= 0) { "Bad motion header" }
            val nodes = List(count) {
                val name = ByteArray(buffer.int).also { buffer.get(it) }.decodeToString()
                val rest = FloatArray(10) { buffer.float }
                val channel = buffer.int
                val width = when (channel) {
                    NodeMotion.NONE -> 0
                    NodeMotion.TRANSLATION -> 3
                    NodeMotion.ROTATION -> 4
                    else -> throw IllegalArgumentException("Unknown channel $channel for $name")
                }
                NodeMotion(name, rest, channel, FloatArray(frames * width) { buffer.float })
            }
            require(!buffer.hasRemaining()) { "Trailing bytes in motion file" }
            return MotionClip(fps, frames, nodes)
        }
    }
}

/** A node's rest transform (translation, rotation xyzw, scale) and its animated channel, if any. */
class NodeMotion(val name: String, val rest: FloatArray, val channel: Int, val values: FloatArray) {
    /** Writes this node's local transform at [time] seconds to [out], column-major. */
    fun matrixAt(time: Float, fps: Int, frames: Int, out: FloatArray, scratch: FloatArray) {
        rest.copyInto(scratch)
        if (channel != NONE) {
            val position = (time * fps).coerceIn(0f, (frames - 1).toFloat())
            val index = position.toInt()
            val next = minOf(index + 1, frames - 1)
            val f = position - index
            if (channel == TRANSLATION) {
                for (i in 0 until 3) scratch[i] = values[index * 3 + i] * (1 - f) + values[next * 3 + i] * f
            } else {
                val a = index * 4
                val b = next * 4
                var dot = 0f
                for (i in 0 until 4) dot += values[a + i] * values[b + i]
                val sign = if (dot < 0) -1f else 1f // q and -q are the same turn; blend the near one
                var length = 0f
                for (i in 0 until 4) {
                    val v = values[a + i] * (1 - f) + sign * values[b + i] * f
                    scratch[3 + i] = v
                    length += v * v
                }
                val scale = 1f / sqrt(length)
                for (i in 3 until 7) scratch[i] *= scale
            }
        }
        trsMatrix(scratch, out)
    }

    companion object {
        const val NONE = 0
        const val TRANSLATION = 1
        const val ROTATION = 2
    }
}

/** Translation, rotation (x y z w) and scale to a column-major 4x4 matrix. */
fun trsMatrix(trs: FloatArray, out: FloatArray) {
    val x = trs[3]; val y = trs[4]; val z = trs[5]; val w = trs[6]
    val sx = trs[7]; val sy = trs[8]; val sz = trs[9]
    out[0] = (1 - 2 * (y * y + z * z)) * sx; out[1] = 2 * (x * y + z * w) * sx; out[2] = 2 * (x * z - y * w) * sx; out[3] = 0f
    out[4] = 2 * (x * y - z * w) * sy; out[5] = (1 - 2 * (x * x + z * z)) * sy; out[6] = 2 * (y * z + x * w) * sy; out[7] = 0f
    out[8] = 2 * (x * z + y * w) * sz; out[9] = 2 * (y * z - x * w) * sz; out[10] = (1 - 2 * (x * x + y * y)) * sz; out[11] = 0f
    out[12] = trs[0]; out[13] = trs[1]; out[14] = trs[2]; out[15] = 1f
}
