package me.yuanis.copilot.gpt.service.data

import android.content.Context
import com.tencent.mmkv.MMKV
import me.yuanis.copilot.gpt.service.client.entity.InternalToken
import me.yuanis.copilot.gpt.service.utils.json
import java.util.UUID

object RelayRepo {

    /**
     * Save the key of GitHub Copilot, which is used to call the API of GitHub Copilot
     */
    private const val COPILOT_TOKEN = "copilot_token"

    /**
     * Save the accessToken of GitHub Copilot, which is used to call the API of GitHub Copilot
     */
    private const val INTERNAL_TOKEN_PREFIX = "access_token_"

    /**
     * 设备 ID
     */
    private const val DEVICE_ID = "device_id"

    /**
     * Server port
     */
    private const val SERVER_PORT = "server_port"

    /**
     * Session timeout in milliseconds
     */
    private const val SESSION_TIMEOUT = 1000 * 60 * 15

    /**
     * Session ID.
     *
     * @property copilotToken GitHub Copilot key
     * @property expiredAt Expired time
     * @property value Session ID value
     */
    data class SessionId(
        val copilotToken: String,
        val expiredAt: Long,
        val value: String,
    )

    /**
     * Session ID cache.
     *
     * Key: copilotToken
     * Value: SessionId
     */
    private val sessionIdCache = mutableMapOf<String, SessionId>()

    private lateinit var cache: MMKV

    fun init(context: Context) {
        MMKV.initialize(context)
        cache = MMKV.defaultMMKV()
    }

    fun saveCopilotToken(copilotToken: String) {
        cache.encode(COPILOT_TOKEN, copilotToken)
    }

    fun getCopilotToken(): String? {
        return cache.decodeString(COPILOT_TOKEN)
    }

    fun saveInternalToken(copilotToken: String, internalToken: InternalToken) {
        // 序列化 internalToken
        val json = json.encodeToString(InternalToken.serializer(), internalToken)
        cache.encode(INTERNAL_TOKEN_PREFIX + copilotToken, json)
    }

    /**
     * Get a device id.
     *
     * Generate a deviceId through UUID, and will not be modified unless manually cleared.
     */
    fun getDeviceId(): String {
        return cache.decodeString(DEVICE_ID) ?: run {
            val deviceId = UUID.randomUUID().toString()
            cache.encode(DEVICE_ID, deviceId)
            deviceId
        }
    }

    /**
     * Get a session id for copilot token
     */
    fun getSessionId(copilotToken: String): String {
        val sessionId = sessionIdCache[copilotToken]
        val currentTimeMillis = System.currentTimeMillis()
        if (sessionId != null && sessionId.expiredAt > currentTimeMillis) {
            return sessionId.value
        }
        val id = UUID.randomUUID().toString() + currentTimeMillis.toString()
        val newSessionId = SessionId(copilotToken, currentTimeMillis + SESSION_TIMEOUT, id)
        sessionIdCache[copilotToken] = newSessionId
        return newSessionId.value
    }

    fun getInternalToken(copilotToken: String): InternalToken? {
        return runCatching {
            val jsonString = cache.decodeString(INTERNAL_TOKEN_PREFIX + copilotToken)
            return json.decodeFromString(InternalToken.serializer(), jsonString!!)
        }.getOrNull()
    }

    fun saveServerPort(port: Int) {
        cache.encode(SERVER_PORT, port)
    }

    fun getServerPort(): Int {
        if (cache.containsKey(SERVER_PORT).not()) {
            return 8080
        }
        return cache.decodeInt(SERVER_PORT)
    }
}
