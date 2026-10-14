package dev.digitalrain.synthwave.feature.booking

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

fun displayScore(value: Double): String = BigDecimal.valueOf(value.coerceAtLeast(0.0))
    .setScale(2, RoundingMode.DOWN).toPlainString()

/** Seconds left in the 20-second show, rounded up, as "0:14". */
fun timeLeft(positionMs: Long): String {
    val left = (20_000L - positionMs.coerceIn(0L, 20_000L) + 999L) / 1_000L
    return String.format(Locale.ROOT, "0:%02d", left)
}
