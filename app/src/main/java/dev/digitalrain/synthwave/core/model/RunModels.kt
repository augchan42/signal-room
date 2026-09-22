package dev.digitalrain.synthwave.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Phase { BRIEF, DESK, PREPARATION, BROADCAST, VERDICT, RECAP }

@Serializable
data class RunSnapshot(
    val schemaVersion: Int,
    val contentVersion: Int,
    val attemptId: Int,
    val phase: Phase,
    val selection: Selection,
    val preparation: Preparation,
    val result: BookingResult? = null,
    val previousResult: BookingResult? = null,
    val viewingCompleted: Boolean = false,
    val broadcastPositionMs: Long = 0,
)
