package me.yuanis.copilot.gpt.service.utils

import kotlinx.serialization.json.Json

/**
 * kotlinx JSON serializer configuration.
 */
val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    useArrayPolymorphism = false
}
