package com.credence.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thrown for both network failures and a `{ ok: false, error }` response
 * from MobileApi.gs, so every call site can catch one exception type and
 * show its message directly to the user — the server's error messages
 * (the duplicate-name/duplicate-fee guards, a role-check failure, "not
 * signed in", etc.) are already written in Code.gs to be shown as-is,
 * exactly like Index.html's toast(e.message) already does for the web
 * app.
 */
class ApiException(message: String) : Exception(message)

/**
 * Thin HTTP layer over MobileApi.gs (see that file's own header comment
 * for the full request/response contract). GET is used for every read
 * action, POST with a JSON body for every write action — matching
 * exactly what MobileApi.gs expects on the other end.
 */
object ApiClient {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun get(action: String, params: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val urlBuilder = ApiConfig.BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("api", "1")
                .addQueryParameter("action", action)
            params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
            val request = Request.Builder().url(urlBuilder.build()).get().build()
            executeAndUnwrap(request)
        }

    /**
     * [extra] becomes the top-level keys of the JSON body alongside
     * "action" and "username" — for actions like saveStudent/saveFee/
     * recordPayment, put the nested payload under a "data" key, e.g.
     * `buildJsonObject { put("data", json.encodeToJsonElement(input)) }`,
     * matching exactly what routeMobileApiAction_() in MobileApi.gs
     * reads as `params.data`.
     */
    suspend fun post(action: String, username: String, extra: JsonObject = JsonObject(emptyMap())): JsonElement =
        withContext(Dispatchers.IO) {
            val bodyObject = buildJsonObject {
                put("action", action)
                put("username", username)
                extra.forEach { (key, value) -> put(key, value) }
            }
            val requestBody = bodyObject.toString().toRequestBody(jsonMediaType)
            val url = ApiConfig.BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("api", "1")
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()
            executeAndUnwrap(request)
        }

    private fun executeAndUnwrap(request: Request): JsonElement {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException("Couldn't reach the server — check your internet connection and try again.")
        }
        response.use { resp ->
            val bodyText = resp.body?.string()
                ?: throw ApiException("Empty response from server (HTTP ${resp.code}).")
            if (!resp.isSuccessful) {
                throw ApiException("Server error (HTTP ${resp.code}): ${bodyText.take(200)}")
            }
            val root = try {
                json.parseToJsonElement(bodyText).jsonObject
            } catch (e: Exception) {
                throw ApiException("Unexpected response from server.")
            }
            val ok = root["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!ok) {
                val message = root["error"]?.jsonPrimitive?.contentOrNull ?: "Request failed."
                throw ApiException(message)
            }
            return root["data"] ?: JsonNull
        }
    }
}

/** Decodes a JsonElement (typically ApiClient.get/post's returned "data"
 * element) into a concrete type — e.g. `client.get("getStudents", ...)
 * .decodeAs<List<Student>>()`. */
inline fun <reified T> JsonElement.decodeAs(): T =
    ApiClient.json.decodeFromJsonElement(serializer(), this)
