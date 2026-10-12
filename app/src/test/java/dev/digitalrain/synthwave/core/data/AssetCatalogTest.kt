package dev.digitalrain.synthwave.core.data

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AssetCatalogTest {
    private val manifest by lazy {
        loadAssetManifest(requireNotNull(javaClass.classLoader?.getResource("demo/manifest.json")).readText())
    }

    @Test
    fun checkedInManifestResolvesEveryId() {
        val catalog = AssetCatalog(manifest, AssetOpener { true })
        for (arrangement in ArrangementId.entries) {
            for (member in MemberId.entries) {
                val asset = catalog.audio(Selection(arrangement, member, ConceptId.MONOCHROME))
                assertEquals("${arrangement.name.lowercase()}-${member.name.lowercase()}", asset.id)
            }
        }
        for (concept in ConceptId.entries) {
            assertEquals("visual/concept-${concept.name.lowercase()}.webp", catalog.concept(concept).stillPath)
        }
        for (member in MemberId.entries) {
            assertEquals("visual/member-${member.name.lowercase()}.webp", catalog.member(member).stillPath)
        }
        assertEquals("visual/control-room.webp", catalog.controlRoom())
    }

    @Test
    fun missingAudioBlocksWithStableAssetId() {
        val catalog = AssetCatalog(manifest, AssetOpener { it != "audio/sparse-frontman.m4a" })
        val error = expectAssetProblem {
            catalog.audio(Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.WARM))
        }
        assertEquals("sparse-frontman", error.assetId)
        assertEquals(AssetProblem.Kind.AUDIO, error.kind)
    }

    @Test
    fun missingOrFailedVideoUsesOnlyItsMatchingStill() {
        val catalog = AssetCatalog(manifest, AssetOpener { it != "visual/loop-warm.mp4" })
        assertEquals(
            VisualPlayback.Still("visual/concept-warm.webp"),
            catalog.visualPlayback(ConceptId.WARM),
        )
        assertEquals(
            AssetRecovery.UseStill("loop-warm", "visual/concept-warm.webp"),
            catalog.recoveryForDecoderError("visual/loop-warm.mp4"),
        )
        assertEquals(
            VisualPlayback.Loop("visual/loop-electric.mp4"),
            catalog.visualPlayback(ConceptId.ELECTRIC),
        )
    }

    @Test
    fun decoderErrorMapsAudioToBlockingId() {
        val catalog = AssetCatalog(manifest, AssetOpener { true })
        assertEquals(
            AssetRecovery.BlockBooking("layered-keytar"),
            catalog.recoveryForDecoderError("audio/layered-keytar.m4a"),
        )
    }

    @Test
    fun missingStillAndUnknownIdsFailExplicitly() {
        val missingStill = AssetCatalog(manifest, AssetOpener { it != "visual/concept-warm.webp" })
        val error = expectAssetProblem { missingStill.visualPlayback(ConceptId.WARM) }
        assertEquals("concept-warm", error.assetId)
        assertEquals(AssetProblem.Kind.STILL, error.kind)

        val incomplete = AssetCatalog(manifest.copy(audio = emptyList()), AssetOpener { true })
        val unknown = expectAssetProblem {
            incomplete.audio(Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.WARM))
        }
        assertEquals("sparse-frontman", unknown.assetId)
        assertTrue(unknown.message!!.contains("not in manifest"))

        try {
            incomplete.recoveryForDecoderError("audio/not-listed.m4a")
            fail("Unknown decoder path must fail")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun expectAssetProblem(block: () -> Unit): AssetProblem {
        try {
            block()
            fail("Expected AssetProblem")
        } catch (error: AssetProblem) {
            return error
        }
        error("unreachable")
    }
}
