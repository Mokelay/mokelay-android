package com.mokelay.android.api

import com.mokelay.android.BuildConfig
import com.mokelay.android.dsl.MokelayPage
import com.mokelay.android.navigation.PageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

class MokelayPageApi(
    val baseUrl: String = defaultBaseUrl(),
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = defaultJson,
) {
    suspend fun fetchPage(uuid: String, source: PageSource = PageSource.USER): MokelayPage = withContext(Dispatchers.IO) {
        val path = if (source == PageSource.SYSTEM) {
            "/api/mokelay/read_mokelay_page_json"
        } else {
            "/api/mokelay/read_page_by_uuid"
        }
        val url = baseUrl.toHttpUrl()
            .newBuilder()
            .encodedPath(path)
            .addQueryParameter("uuid", uuid)
            .build()
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) {
                throw MokelayPageApiException.HttpStatus(it.code)
            }

            val body = it.body?.string() ?: throw MokelayPageApiException.InvalidResponse
            val envelope = json.decodeFromString(ReadPageEnvelope.serializer(), body)

            if (!envelope.ok) {
                throw MokelayPageApiException.Api(envelope.error?.message ?: envelope.error?.code ?: "API request failed.")
            }

            envelope.data?.page ?: throw MokelayPageApiException.PageNotFound(uuid)
        }
    }

    suspend fun sendJsonRequest(
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBuilder = Request.Builder().url(url)

        headers.forEach { (key, value) -> requestBuilder.header(key, value) }

        val normalizedMethod = method.uppercase()
        val requestBody = body?.let { json.encodeToString(JsonElement.serializer(), it).toRequestBody(mediaType) }

        when (normalizedMethod) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody ?: ByteArray(0).toRequestBody(mediaType))
            "PUT" -> requestBuilder.put(requestBody ?: ByteArray(0).toRequestBody(mediaType))
            "PATCH" -> requestBuilder.patch(requestBody ?: ByteArray(0).toRequestBody(mediaType))
            "DELETE" -> requestBuilder.delete(requestBody)
            else -> requestBuilder.method(normalizedMethod, requestBody)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        response.use {
            if (!it.isSuccessful) {
                throw MokelayPageApiException.HttpStatus(it.code)
            }

            val responseBody = it.body?.string() ?: throw MokelayPageApiException.InvalidResponse
            json.decodeFromString(JsonElement.serializer(), responseBody)
        }
    }

    companion object {
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun defaultBaseUrl(): String {
            return if (BuildConfig.DEBUG) {
                BuildConfig.MOKELAY_LOCAL_BASE_URL
            } else {
                BuildConfig.MOKELAY_REMOTE_BASE_URL
            }
        }
    }
}

@Serializable
private data class ReadPageEnvelope(
    val ok: Boolean = false,
    val data: ReadPagePayload? = null,
    val error: ApiErrorPayload? = null,
)

@Serializable
private data class ReadPagePayload(
    val page: MokelayPage? = null,
)

@Serializable
private data class ApiErrorPayload(
    val code: String? = null,
    val message: String? = null,
)

sealed class MokelayPageApiException(message: String? = null) : IOException(message) {
    data object InvalidResponse : MokelayPageApiException("Invalid response from Mokelay server.")
    data class HttpStatus(val code: Int) : MokelayPageApiException("Mokelay server returned HTTP $code.")
    data class Api(val apiMessage: String) : MokelayPageApiException(apiMessage)
    data class PageNotFound(val uuid: String) : MokelayPageApiException("Page not found: $uuid")
}
