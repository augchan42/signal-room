package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.BookingResult
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceAuditTest {
    private val content = TestContent.lateNight()
    private val epsilon = 1e-6

    @Test
    fun allLegalPlansMeetPrototypeBalanceGates() {
        val evaluations = legalPreparations().flatMap { prep ->
            ArrangementId.entries.flatMap { arrangement ->
                MemberId.entries.flatMap { member ->
                    ConceptId.entries.map { concept ->
                        val selection = Selection(arrangement, member, concept)
                        Evaluation(selection, prep, scoreBooking(content, selection, prep))
                    }
                }
            }
        }
        val noResearch = evaluations.filterNot { it.preparation.researched }
        val withResearch = evaluations.filter { it.preparation.researched }

        assertEquals(576, evaluations.size)
        assertEquals(360, noResearch.size)
        assertEquals(216, withResearch.size)

        val totalRate = successRate(evaluations)
        assertTrue("Total pass rate $totalRate", totalRate in .35..0.60)
        ArrangementId.entries.forEach { arrangement ->
            assertTrue(
                "$arrangement has no successful plan",
                evaluations.any { it.selection.arrangement == arrangement && it.result.invited },
            )
        }

        assertNoArrangementDominates(evaluations)
        assertRestCanBeatRehearsal(evaluations)
        assertEveryMemberHasUniquePassingCell(evaluations)
        assertAudienceObjectives(evaluations)
        assertArrangementSensitivities()
        writeReport(evaluations, noResearch, withResearch)
    }

    private fun legalPreparations(): List<Preparation> = buildList {
        for (researched in listOf(false, true)) {
            for (rehearsals in 0..3) {
                for (refinements in 0..3) {
                    for (rests in 0..3) {
                        val prep = Preparation(rehearsals, refinements, rests, researched)
                        if (prep.used == 3) add(prep)
                    }
                }
            }
        }
    }

    private fun assertNoArrangementDominates(evaluations: List<Evaluation>) {
        ArrangementId.entries.forEach { left ->
            ArrangementId.entries.filterNot { it == left }.forEach { right ->
                val pairs = evaluations.filter { it.selection.arrangement == left }.map { leftEvaluation ->
                    val rightEvaluation = evaluations.single {
                        it.selection.arrangement == right &&
                            it.selection.member == leftEvaluation.selection.member &&
                            it.selection.concept == leftEvaluation.selection.concept &&
                            it.preparation == leftEvaluation.preparation
                    }
                    leftEvaluation.result.invitationScore to rightEvaluation.result.invitationScore
                }
                val weaklyDominates = pairs.all { (a, b) -> a + epsilon >= b } &&
                    pairs.any { (a, b) -> a > b + epsilon }
                assertFalse("$left weakly dominates $right", weaklyDominates)
            }
        }
    }

    private fun assertRestCanBeatRehearsal(evaluations: List<Evaluation>) {
        val example = evaluations.any { rested ->
            rested.preparation.rests > 0 && evaluations.any { rehearsed ->
                rehearsed.selection == rested.selection &&
                    rehearsed.preparation.researched == rested.preparation.researched &&
                    rehearsed.preparation.rehearsals == rested.preparation.rehearsals + 1 &&
                    rehearsed.preparation.refinements == rested.preparation.refinements &&
                    rehearsed.preparation.rests == rested.preparation.rests - 1 &&
                    rested.result.invitationScore > rehearsed.result.invitationScore + epsilon
            }
        }
        assertTrue("No rest-over-rehearsal improvement", example)
    }

    private fun assertEveryMemberHasUniquePassingCell(evaluations: List<Evaluation>) {
        val uniqueWinners = mutableSetOf<MemberId>()
        ArrangementId.entries.forEach { arrangement ->
            ConceptId.entries.forEach { concept ->
                val bestByMember = MemberId.entries.associateWith { member ->
                    evaluations.filter {
                        it.selection.arrangement == arrangement &&
                            it.selection.concept == concept &&
                            it.selection.member == member
                    }.maxOf { it.result.invitationScore }
                }
                val maximum = bestByMember.values.max()
                val winners = bestByMember.filterValues { kotlin.math.abs(it - maximum) <= epsilon }
                if (winners.size == 1 && maximum >= content.balance.invitationThreshold) {
                    uniqueWinners += winners.keys.single()
                }
            }
        }
        assertEquals(MemberId.entries.toSet(), uniqueWinners)
    }

    private fun assertAudienceObjectives(evaluations: List<Evaluation>) {
        val cellRates = ArrangementId.entries.flatMap { arrangement ->
            ConceptId.entries.map { concept ->
                (arrangement to concept) to successRate(evaluations.filter {
                    it.selection.arrangement == arrangement && it.selection.concept == concept
                })
            }
        }.toMap()
        val sparseMonoRate = requireNotNull(cellRates[ArrangementId.SPARSE to ConceptId.MONOCHROME])
        assertTrue(cellRates.values.all { sparseMonoRate + epsilon >= it })

        fun maxDelta(arrangement: ArrangementId, concept: ConceptId, segment: String, affinity: Boolean): Double =
            evaluations.filter {
                it.selection.arrangement == arrangement && it.selection.concept == concept
            }.maxOf { evaluation ->
                val initial = requireNotNull(content.segments[segment]).initial
                val after = requireNotNull(evaluation.result.audienceAfter[segment])
                if (affinity) after.affinity - initial.affinity else after.awareness - initial.awareness
            }

        val retroLayeredWarm = maxDelta(ArrangementId.LAYERED, ConceptId.WARM, "retro", true)
        val retroSparseMono = maxDelta(ArrangementId.SPARSE, ConceptId.MONOCHROME, "retro", true)
        assertTrue("Layered/warm retro $retroLayeredWarm <= Sparse/mono $retroSparseMono", retroLayeredWarm > retroSparseMono)

        val drivingElectricReach = maxDelta(ArrangementId.DRIVING, ConceptId.ELECTRIC, "performance", false) to
            maxDelta(ArrangementId.DRIVING, ConceptId.ELECTRIC, "performance", true)
        val competitors = listOf(
            maxDelta(ArrangementId.SPARSE, ConceptId.MONOCHROME, "performance", false) to
                maxDelta(ArrangementId.SPARSE, ConceptId.MONOCHROME, "performance", true),
            maxDelta(ArrangementId.LAYERED, ConceptId.WARM, "performance", false) to
                maxDelta(ArrangementId.LAYERED, ConceptId.WARM, "performance", true),
        )
        assertTrue(
            "Driving/electric performance reach $drivingElectricReach does not lead $competitors",
            competitors.all { competitor ->
                drivingElectricReach.first > competitor.first + epsilon ||
                    (kotlin.math.abs(drivingElectricReach.first - competitor.first) <= epsilon &&
                        drivingElectricReach.second > competitor.second + epsilon)
            },
        )

        val highQualityHighFit = evaluations.flatMap { evaluation ->
            audienceResponse(
                content,
                evaluation.selection,
                evaluation.result.coherence.value,
                evaluation.result.execution.value,
            ).values
        }.filter { it.quality >= 75.0 && it.fit >= 80.0 }
        assertTrue(highQualityHighFit.isNotEmpty())
        assertTrue(highQualityHighFit.all { it.awarenessDelta in 10.0..20.0 })

        val failedWithNicheGain = evaluations.any { evaluation ->
            !evaluation.result.invited && content.segments.any { (id, segment) ->
                requireNotNull(evaluation.result.audienceAfter[id]).affinity - segment.initial.affinity >= 3.0
            }
        }
        assertTrue("No failed invitation retained a +3 niche affinity gain", failedWithNicheGain)
    }

    private fun assertArrangementSensitivities() {
        val prep = Preparation(1, 1, 1, false)
        fun execution(arrangement: ArrangementId, contentOverride: dev.digitalrain.synthwave.core.model.BookingContent = content): Double =
            scoreBooking(
                contentOverride,
                Selection(arrangement, MemberId.FRONTMAN, ConceptId.MONOCHROME),
                prep,
            ).execution.value

        val frontman = requireNotNull(content.members[MemberId.FRONTMAN])
        val lowerSkill = content.copy(
            members = content.members + (MemberId.FRONTMAN to frontman.copy(skill = frontman.skill - 10)),
        )
        val skillDrops = ArrangementId.entries.associateWith { execution(it) - execution(it, lowerSkill) }
        assertTrue(requireNotNull(skillDrops[ArrangementId.SPARSE]) > requireNotNull(skillDrops[ArrangementId.DRIVING]))

        val higherFatigue = content.copy(
            members = content.members + (MemberId.FRONTMAN to frontman.copy(fatigue = frontman.fatigue + 10)),
        )
        val fatigueDrops = ArrangementId.entries.associateWith { execution(it) - execution(it, higherFatigue) }
        assertTrue(requireNotNull(fatigueDrops[ArrangementId.DRIVING]) > requireNotNull(fatigueDrops[ArrangementId.SPARSE]))

        fun coherenceLift(arrangement: ArrangementId): Double {
            val rules = requireNotNull(content.arrangements[arrangement])
            val lifted = content.copy(
                arrangements = content.arrangements + (
                    arrangement to rules.copy(
                        conceptCoherence = rules.conceptCoherence +
                            (ConceptId.MONOCHROME to requireNotNull(rules.conceptCoherence[ConceptId.MONOCHROME]) + 5),
                    )
                ),
            )
            return execution(arrangement, lifted) - execution(arrangement)
        }
        assertTrue(coherenceLift(ArrangementId.LAYERED) > coherenceLift(ArrangementId.SPARSE) + epsilon)
    }

    private fun writeReport(
        evaluations: List<Evaluation>,
        noResearch: List<Evaluation>,
        withResearch: List<Evaluation>,
    ) {
        val ordered = evaluations.sortedByDescending { it.result.invitationScore }
        val audienceOrdered = evaluations.sortedByDescending(::totalAffinityGain)
        val restExample = evaluations.first { rested ->
            rested.preparation.rests > 0 && evaluations.any { rehearsed ->
                rehearsed.selection == rested.selection &&
                    rehearsed.preparation.researched == rested.preparation.researched &&
                    rehearsed.preparation.rehearsals == rested.preparation.rehearsals + 1 &&
                    rehearsed.preparation.refinements == rested.preparation.refinements &&
                    rehearsed.preparation.rests == rested.preparation.rests - 1 &&
                    rested.result.invitationScore > rehearsed.result.invitationScore + epsilon
            }
        }
        val rehearsalComparison = evaluations.single { rehearsed ->
            rehearsed.selection == restExample.selection &&
                rehearsed.preparation.researched == restExample.preparation.researched &&
                rehearsed.preparation.rehearsals == restExample.preparation.rehearsals + 1 &&
                rehearsed.preparation.refinements == restExample.preparation.refinements &&
                rehearsed.preparation.rests == restExample.preparation.rests - 1
        }
        val report = buildJsonObject {
            put("legalPlans", evaluations.size)
            put("successRate", successRate(evaluations))
            put("withoutResearchSuccessRate", successRate(noResearch))
            put("withResearchSuccessRate", successRate(withResearch))
            put("strongestScore", ordered.first().result.invitationScore)
            put("strongestPlan", ordered.first().label())
            put("weakestScore", ordered.last().result.invitationScore)
            put("weakestPlan", ordered.last().label())
            put("audienceObjective", "sum of actual affinity deltas across five segments")
            put("audienceObjectiveWinner", audienceOrdered.first().label())
            put("audienceObjectiveGain", totalAffinityGain(audienceOrdered.first()))
            put("restBenefitExample", buildJsonObject {
                put("restPlan", restExample.label())
                put("restScore", restExample.result.invitationScore)
                put("rehearsalPlan", rehearsalComparison.label())
                put("rehearsalScore", rehearsalComparison.result.invitationScore)
            })
            put("arrangements", buildJsonArray {
                ArrangementId.entries.forEach { arrangement ->
                    add(buildJsonObject {
                        put("id", arrangement.name)
                        put("successRate", successRate(evaluations.filter { it.selection.arrangement == arrangement }))
                    })
                }
            })
            put("cells", buildJsonArray {
                ArrangementId.entries.forEach { arrangement ->
                    ConceptId.entries.forEach { concept ->
                        add(buildJsonObject {
                            val cell = evaluations.filter {
                                it.selection.arrangement == arrangement && it.selection.concept == concept
                            }
                            val memberMaxima = MemberId.entries.associateWith { member ->
                                cell.filter { it.selection.member == member }.maxOf { it.result.invitationScore }
                            }
                            val maximum = memberMaxima.values.max()
                            put("arrangement", arrangement.name)
                            put("concept", concept.name)
                            put("successRate", successRate(cell))
                            put("bestInvitationScore", maximum)
                            put("bestMembers", buildJsonArray {
                                memberMaxima.filterValues { kotlin.math.abs(it - maximum) <= epsilon }
                                    .keys.forEach { add(JsonPrimitive(it.name)) }
                            })
                        })
                    }
                }
            })
            put("pairwiseArrangementComparisons", buildJsonArray {
                ArrangementId.entries.forEach { left ->
                    ArrangementId.entries.filter { it.ordinal > left.ordinal }.forEach { right ->
                        val leftPlans = evaluations.filter { it.selection.arrangement == left }
                        var leftWins = 0
                        var rightWins = 0
                        var ties = 0
                        leftPlans.forEach { leftPlan ->
                            val rightPlan = evaluations.single {
                                it.selection.arrangement == right &&
                                    it.selection.member == leftPlan.selection.member &&
                                    it.selection.concept == leftPlan.selection.concept &&
                                    it.preparation == leftPlan.preparation
                            }
                            when {
                                leftPlan.result.invitationScore > rightPlan.result.invitationScore + epsilon -> leftWins++
                                rightPlan.result.invitationScore > leftPlan.result.invitationScore + epsilon -> rightWins++
                                else -> ties++
                            }
                        }
                        add(buildJsonObject {
                            put("left", left.name)
                            put("right", right.name)
                            put("leftWins", leftWins)
                            put("rightWins", rightWins)
                            put("ties", ties)
                        })
                    }
                }
            })
        }
        val path = Path.of("build/reports/balance/late-night.json")
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use { writer ->
            Json { prettyPrint = true }.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                report,
            ).let(writer::write)
        }
    }

    private fun successRate(evaluations: List<Evaluation>): Double =
        evaluations.count { it.result.invited }.toDouble() / evaluations.size

    private fun totalAffinityGain(evaluation: Evaluation): Double = content.segments.entries.sumOf { (id, segment) ->
        requireNotNull(evaluation.result.audienceAfter[id]).affinity - segment.initial.affinity
    }

    private data class Evaluation(
        val selection: Selection,
        val preparation: Preparation,
        val result: BookingResult,
    ) {
        fun label(): String = "$selection / $preparation"
    }
}
