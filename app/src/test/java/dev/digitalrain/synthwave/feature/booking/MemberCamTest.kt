package dev.digitalrain.synthwave.feature.booking

import dev.digitalrain.synthwave.core.model.MemberId
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberCamTest {
    private val width = 263f
    private val height = 352f

    @Test fun centresEachMemberInTheInset() {
        for (member in MemberId.entries) {
            val focus = camFocus(member)
            val m = camMatrixValues(width, height, focus)
            // The focus point of the stretched video is at (focus.x * width, focus.y * height).
            assertEquals(width / 2, m[0] * focus.x * width + m[2], 0.01f)
            assertEquals(height / 2, m[4] * focus.y * height + m[5], 0.01f)
        }
    }

    @Test fun keepsTheVideoAspectRatioAndField() {
        for (member in MemberId.entries) {
            val focus = camFocus(member)
            val m = camMatrixValues(width, height, focus)
            val pixelsPerVideoPixelX = m[0] * width / 540f
            val pixelsPerVideoPixelY = m[4] * height / 960f
            assertEquals(pixelsPerVideoPixelX, pixelsPerVideoPixelY, 0.0001f)
            assertEquals(width / (focus.fieldWidth * 540f), pixelsPerVideoPixelX, 0.0001f)
        }
    }
}
