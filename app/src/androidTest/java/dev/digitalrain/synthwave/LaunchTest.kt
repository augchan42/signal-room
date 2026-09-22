package dev.digitalrain.synthwave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesIntoLateNightBooking() {
        compose.onNodeWithText("Late-night debut").assertIsDisplayed()
    }

    @Test
    fun bookingTitleHasReadableLightPixels() {
        val pixels = compose.onNodeWithText("Late-night debut")
            .assertIsDisplayed()
            .captureToImage()
            .toPixelMap()

        val hasLightTextPixel = (0 until pixels.height).any { y ->
            (0 until pixels.width).any { x ->
                val color = pixels[x, y]
                color.red + color.green + color.blue > 1.8f
            }
        }
        assert(hasLightTextPixel) { "Booking title has no readable light pixels" }
    }

    @Test
    fun showsBlenderControlRoomBackdrop() {
        compose.onNodeWithContentDescription("Signal Room control room")
            .assertIsDisplayed()
    }
}
