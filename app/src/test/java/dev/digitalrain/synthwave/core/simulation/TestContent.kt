package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.BookingContent

object TestContent {
    fun lateNight(): BookingContent {
        val json = requireNotNull(javaClass.classLoader?.getResource("content/late-night.json")) {
            "Missing content/late-night.json test resource"
        }.readText()
        return loadContent(json)
    }
}
