package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageDataTest {
    private fun text(path: String) = requireNotNull(javaClass.classLoader?.getResource(path)).readText()

    @Test fun stageDescribesEveryConceptAndCamera() {
        val info = StageInfo.parse(text("demo/live/stage.json"))
        assertEquals(setOf(*ConceptId.entries.toTypedArray()), info.concepts.keys)
        for (look in info.concepts.values) {
            assertEquals(4, look.suits.size)
            assertEquals(6, look.lights.size)
        }
        assertEquals(4.65f, info.cameras.getValue(BROADCAST_CAMERA).ymag, 0.01f)
        assertEquals(350f / 300f, info.concepts.getValue(ConceptId.ELECTRIC).lights.getValue("Warm portrait key").factor, 1e-4f)
        assertEquals(4, info.areaLights.size)
    }

    @Test fun everyMixHasItsLightAndEmissionLevels() {
        for (arrangement in listOf("sparse", "driving", "layered")) for (member in listOf("frontman", "keyboardist", "keytar", "drummer")) {
            val tracks = StageTracks.parse(text("demo/live/mix/$arrangement-$member.tracks.json"))
            assertEquals(480, tracks.frames)
            assertTrue(tracks.lights.all { it.watts.size == 480 })
            assertTrue(tracks.emissive.all { it.strength.size == 480 && it.color.size == 3 })
        }
    }

    @Test fun aFullWidthCamWindowIsTheBroadcastFrame() {
        val window = camWindow(CamFocus(0.5f, 0.5f, 1f), 4.65f, 540, 960)
        assertArrayEquals(floatArrayOf(-4.65f * 9 / 16, 4.65f * 9 / 16, -4.65f, 4.65f), window, 1e-5f)
    }

    @Test fun theFrontmanCamIsTheOldCloseUp() {
        // The video cam showed 13% of the frame's width around the singer's face, at the inset's aspect.
        val (left, right, bottom, top) = camWindow(camFocus(MemberId.FRONTMAN), 4.65f, 280, 360).toList()
        val frameHalfWidth = 4.65f * 9 / 16
        assertEquals(0.13f * 2 * frameHalfWidth, right - left, 1e-5f)
        assertEquals((right - left) * 360 / 280, top - bottom, 1e-5f)
        assertEquals((0.585f * 2 - 1) * frameHalfWidth, (left + right) / 2, 1e-5f)
        assertEquals((1 - 0.515f * 2) * 4.65f, (bottom + top) / 2, 1e-5f)
    }

    @Test fun tracksAreBlendedBetweenFramesAndHeldAtTheEnds() {
        val values = floatArrayOf(0f, 10f, 20f)
        assertEquals(5f, sampleTrack(values, 0.5f / 24, 24), 1e-5f)
        assertEquals(0f, sampleTrack(values, -1f, 24), 0f)
        assertEquals(20f, sampleTrack(values, 5f, 24), 0f)
    }
}
