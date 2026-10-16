package dev.digitalrain.synthwave.core.stage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionClipTest {
    private fun file(frames: Int, vararg nodes: Triple<String, Int, FloatArray>): ByteArray {
        val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("SRM1".toByteArray()).putInt(24).putInt(frames).putInt(nodes.size)
        for ((name, channel, values) in nodes) {
            val bytes = name.toByteArray()
            buffer.putInt(bytes.size).put(bytes)
            floatArrayOf(1f, 2f, 3f, 0f, 0f, 0f, 1f, 1f, 1f, 1f).forEach { buffer.putFloat(it) }
            buffer.putInt(channel)
            values.forEach { buffer.putFloat(it) }
        }
        return buffer.array().copyOf(buffer.position())
    }

    private fun pose(node: NodeMotion, time: Float, frames: Int = 2): FloatArray =
        FloatArray(16).also { node.matrixAt(time, 24, frames, it, FloatArray(10)) }

    @Test fun restPoseIsTheRestTranslation() {
        val clip = MotionClip.parse(file(2, Triple("boot", NodeMotion.NONE, FloatArray(0))))
        assertEquals(24, clip.fps)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 2f, 3f, 1f),
            pose(clip.nodes.single(), 0.5f), 1e-6f)
    }

    @Test fun translationIsBlendedBetweenFrames() {
        val clip = MotionClip.parse(file(2, Triple("arm", NodeMotion.TRANSLATION, floatArrayOf(0f, 0f, 0f, 2f, 4f, 6f))))
        val m = pose(clip.nodes.single(), 0.5f / 24)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), floatArrayOf(m[12], m[13], m[14]), 1e-5f)
        val end = pose(clip.nodes.single(), 99f)
        assertArrayEquals(floatArrayOf(2f, 4f, 6f), floatArrayOf(end[12], end[13], end[14]), 1e-5f)
    }

    @Test fun rotationTakesTheShortWayAndStaysUnitLength() {
        val s = Math.sqrt(0.5).toFloat()
        // 0 then 90 degrees about z, the second written with the opposite sign (the same rotation).
        val clip = MotionClip.parse(file(2, Triple("head", NodeMotion.ROTATION, floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, -s, -s))))
        val m = pose(clip.nodes.single(), 1f / 24)
        // The x axis turns to y.
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), floatArrayOf(m[0], m[1], m[2]), 1e-5f)
        val half = pose(clip.nodes.single(), 0.5f / 24)
        val length = Math.hypot(half[0].toDouble(), half[1].toDouble())
        assertEquals(1.0, length, 1e-5)
        assertTrue(half[1] > 0.6f) // about 45 degrees, not the long way round
    }

    @Test fun everyCheckedInMixParses() {
        for (arrangement in listOf("sparse", "driving", "layered")) for (member in listOf("frontman", "keyboardist", "keytar", "drummer")) {
            val bytes = requireNotNull(javaClass.classLoader?.getResource("demo/live/mix/$arrangement-$member.motion")).readBytes()
            val clip = MotionClip.parse(bytes)
            assertEquals(480, clip.frames)
            assertEquals(479f / 24f, clip.duration, 1e-6f)
            assertTrue(clip.nodes.count { it.channel != NodeMotion.NONE } >= 74)
        }
    }
}
