package dev.digitalrain.synthwave.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsTitleThenFinishesAfterHold() {
        var done = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { SignalRoomTheme { SplashScreen(onDone = { done++ }) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("SIGNAL ROOM").assertIsDisplayed()
        compose.onNodeWithText("LATE-NIGHT BROADCAST").assertIsDisplayed()
        assertEquals(0, done)
        compose.mainClock.advanceTimeBy(SPLASH_HOLD_MS)
        assertEquals(1, done)
    }

    @Test fun tapSkips() {
        var done = 0
        compose.setContent { SignalRoomTheme { SplashScreen(onDone = { done++ }) } }
        compose.onNodeWithTag("splash").performClick()
        assertEquals(1, done)
    }
}
