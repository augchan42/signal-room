package dev.digitalrain.synthwave.feature.booking

import java.math.BigDecimal
import java.math.RoundingMode

fun displayScore(value: Double): String = BigDecimal.valueOf(value.coerceAtLeast(0.0))
    .setScale(2, RoundingMode.DOWN).toPlainString()
