package dev.digitalrain.synthwave.core.data

import dev.digitalrain.synthwave.core.model.BookingContent
import kotlinx.serialization.json.Json

private val contentJson = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
}

fun loadContent(json: String): BookingContent = contentJson.decodeFromString(json)
