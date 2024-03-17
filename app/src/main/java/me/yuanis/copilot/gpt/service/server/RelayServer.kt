package me.yuanis.copilot.gpt.service.server

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import me.yuanis.copilot.gpt.service.App.Companion.TAG
import me.yuanis.copilot.gpt.service.client.entity.InternalToken
import me.yuanis.copilot.gpt.service.data.RelayRepo
import me.yuanis.copilot.gpt.service.utils.json
import org.json.JSONObject
import java.util.UUID

private val client = HttpClient(Android)

/**
 * Start server.
 *
 * @return ApplicationEngine
 */
fun startServer(): ApplicationEngine {
    return embeddedServer(CIO, port = 8080) {
        install(DefaultHeaders) {
            header("Access-Control-Allow-Origin", "*")
            header("Access-Control-Allow-Credentials", "true")
            header("Access-Control-Allow-Methods", "*")
            header("Access-Control-Allow-Headers", "*")
        }

        routing {
            options("/v1/chat/completions") {
                call.respondText("", status = HttpStatusCode.OK)
            }

            post("/v1/chat/completions") {
                Log.d(TAG, "Request -> [POST] ${call.request.uri}")
                chatCompletion()
                Log.d(TAG, "Request <- [POST] ${call.request.uri}")
            }
        }
    }.start()
}

/**
 * Handle chat/completions request
 *
 * @receiver PipelineContext<Unit, ApplicationCall>
 */
private suspend fun PipelineContext<Unit, ApplicationCall>.chatCompletion() {
    val url = "https://api.githubcopilot.com/chat/completions"
    val receivedBody = call.receiveText()
    val isStream = try {
        JSONObject(receivedBody).optBoolean("stream", false)
    } catch (e: Throwable) {
        Log.e(TAG, "Parse request body error: $receivedBody", e)
        throw e
    }

    val copilotToken = RelayRepo.getCopilotToken() ?: error("Copilot token not found")
    val headers = getHeader(copilotToken, isStream)
    val statement = client.preparePost(url) {
        method = HttpMethod.Post
        headers {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        setBody(receivedBody)
    }

    with(call.response) {
        header("X-Accel-Buffering", "no")
        if (isStream) {
            header("Content-Type", "text/event-stream; charset=utf-8")
        } else {
            header("Content-Type", "application/json; charset=utf-8")
        }
        header("Cache-Control", "no-cache")
        header("Connection", "keep-alive")
    }

    statement.execute { response ->
        if (isStream) {
            handleAsStream(response)
        } else {
            handleAsText(response)
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleAsText(
    response: HttpResponse,
) {
    val responseBody = overwriteObjectField(response.bodyAsText(), false)
    call.respondText(
        responseBody,
        status = response.status
    )
    Log.d(TAG, "handleAsText: $responseBody")
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleAsStream(
    response: HttpResponse,
) {
    call.response.cacheControl(CacheControl.NoCache(null))
    call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
        writeStringUtf8("\n")
        flush()
        response.bodyAsChannel().onEachLine { line ->
            var toWrite = line
            if (line.isNotEmpty()) {
                val newLine = overwriteObjectField(line.removePrefix("data: "), true)
                toWrite = "data: $newLine\n"
            }
            writeStringUtf8(toWrite)
            writeStringUtf8("\n")
            flush()
        }
        Log.d(TAG, "handleAsStream: read done")
    }
    Log.d(TAG, "handleAsStream: write end")
}

private suspend fun ByteReadChannel.onEachLine(block: suspend (String) -> Unit) {
    while (!isClosedForRead) {
        awaitContent()
        val line = readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue
        block(line)
    }
}

private fun overwriteObjectField(json: String, isStreaming: Boolean): String {
    if (json.trim() == "[DONE]") {
        return json
    }
    return runCatching {
        val jsonObject = JSONObject(json)
        if (isStreaming) {
            jsonObject.put("object", "chat.completion.chunk")
        } else {
            jsonObject.put("object", "chat.completion")
        }
        jsonObject.put("created", System.currentTimeMillis() / 1000)
        return jsonObject.toString()
    }.onFailure {
        Log.e(TAG, "overwriteObjectField error: $json", it)
    }.getOrDefault(json)
}

private suspend fun getHeader(copilotToken: String, stream: Boolean): Map<String, String> {
    val internalToken = getInternalToken(copilotToken)
    val contentType = if (stream) {
        "text/event-stream; charset=utf-8"
    } else {
        "application/json; charset=utf-8"
    }
    return mapOf(
        "Authorization" to "Bearer $internalToken",
        "X-Request-Id" to UUID.randomUUID().toString(),
        "Vscode-Sessionid" to RelayRepo.getSessionId(copilotToken),
        "Vscode-Machineid" to RelayRepo.getDeviceId(),
        "Editor-Version" to "vscode/1.83.1",
        "Editor-Plugin-Version" to "copilot-chat/0.8.0",
        "Openai-Organization" to "github-copilot",
        "Openai-Intent" to "conversation-panel",
        "Content-Type" to contentType,
        "User-Agent" to "GitHubCopilotChat/0.8.0",
        "Accept" to "*/*",
        "Accept-Encoding" to "gzip,deflate,br",
        "Connection" to "close"
    )
}

private suspend fun getInternalToken(copilotToken: String): String {
    val internalTokenUrl = "https://api.github.com/copilot_internal/v2/token"

    Log.d(TAG, "getInternalToken... cToken=$copilotToken")
    val internalToken = RelayRepo.getInternalToken(copilotToken)
    if (internalToken != null && internalToken.isExpired().not()) {
        Log.d(TAG, "getInternalToken -> return cache=${internalToken.token}")
        return internalToken.token
    }

    Log.d(TAG, "getInternalToken... cache invalid=${internalToken?.expiresAt}")

    val response: HttpResponse = client.request(internalTokenUrl) {
        method = HttpMethod.Get
        header("Authorization", "token $copilotToken")
    }

    val body = response.bodyAsText()
    if (response.status != HttpStatusCode.OK) {
        Log.d(TAG, "getInternalToken -> error")
        error("Get GithubCopilot Authorization Token Failed, StatusCode: ${response.status}, Body: $body")
    }

    val freshToken = json.decodeFromString(InternalToken.serializer(), body)
    Log.d(TAG, "getInternalToken -> success")
    RelayRepo.saveInternalToken(copilotToken, freshToken)
    return freshToken.token
}
