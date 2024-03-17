package me.yuanis.copilot.gpt.service.client.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Internal token
 *
 * @property expiresAt Unix timestamp, when token will be expired
 * @property token Token for request copilot api
 */
@Serializable
data class InternalToken(
    @SerialName("expires_at")
    val expiresAt: Int,
    @SerialName("token")
    val token: String,
) {
    fun isExpired() = System.currentTimeMillis() / 1000 > expiresAt
}
