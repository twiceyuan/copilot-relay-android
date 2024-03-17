package me.yuanis.copilot.gpt.service.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import me.yuanis.copilot.gpt.service.data.RelayRepo

/**
 * Mock request to relay service
 *
 * return true if request is successful, otherwise false
 */
suspend fun mockRequestRelayService(): Boolean {
    val client = HttpClient(Android)
    val response = client.request {
        url("http://localhost:8080/v1/chat/completions")
        method = HttpMethod.Post
        headers {
            append("Authorization", "Bearer ${RelayRepo.getCopilotToken()}")
        }
        setBody(
            """
            {
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ],
                "model": "gpt-4",
                "max_tokens": 1024,
                "stream": false,
                "n": 1,
                "temperature": 0.0
            }
            """.trimIndent()
        )
    }
    return response.body<HttpResponse>().status == HttpStatusCode.OK
}