package com.bg2max.app

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Минимальный клиент MAX Bot API.
 *
 * В открытых источниках встречаются два описания одного и того же API:
 *  - официальные Go/TS SDK от max-messenger: host platform-api2.max.ru,
 *    токен передаётся в заголовке Authorization;
 *  - опубликованная OpenAPI-схема: host botapi.max.ru,
 *    токен передаётся query-параметром access_token.
 * Пробуем первый вариант (он подтверждён двумя независимыми SDK), при неудаче — второй.
 */
object MaxApiClient {

    private const val TIMEOUT_MS = 15000

    private data class Endpoint(val baseUrl: String, val useHeaderAuth: Boolean)

    private val endpoints = listOf(
        Endpoint("https://platform-api2.max.ru", useHeaderAuth = true),
        Endpoint("https://botapi.max.ru", useHeaderAuth = false)
    )

    class ApiException(message: String) : Exception(message)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun request(endpoint: Endpoint, path: String, params: String, token: String, method: String, jsonBody: String?): String {
        val query = if (endpoint.useHeaderAuth) params else {
            if (params.isEmpty()) "access_token=${encode(token)}" else "access_token=${encode(token)}&$params"
        }
        val url = URL("${endpoint.baseUrl}$path${if (query.isNotEmpty()) "?$query" else ""}")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (endpoint.useHeaderAuth) {
            connection.setRequestProperty("Authorization", token)
        }
        if (jsonBody != null) {
            connection.doOutput = true
            connection.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { it.write(jsonBody) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        if (code !in 200..299) {
            throw ApiException("HTTP $code: $text")
        }
        return text
    }

    /** Пытается выполнить запрос через оба известных варианта API, пока один не сработает. */
    private fun requestWithFallback(path: String, params: String, token: String, method: String, jsonBody: String?): String {
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                return request(endpoint, path, params, token, method, jsonBody)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: ApiException("Неизвестная ошибка запроса к MAX API")
    }

    /** Проверяет токен, вызывая GET /me. Бросает ApiException при неверном токене. */
    fun checkToken(token: String) {
        requestWithFallback("/me", "", token, "GET", null)
    }

    fun sendMessage(token: String, chatId: String, text: String) {
        val body = JSONObject().apply { put("text", text) }.toString()
        requestWithFallback("/messages", "chat_id=${encode(chatId)}", token, "POST", body)
    }

    data class DetectedChat(val chatId: Long, val title: String)

    /**
     * Читает последние обновления бота (GET /updates) и извлекает chat_id
     * из событий message_created / bot_started / user_added.
     * Пользователь должен предварительно написать боту в MAX.
     */
    fun detectChatIds(token: String): List<DetectedChat> {
        val text = requestWithFallback("/updates", "limit=100", token, "GET", null)

        val json = JSONObject(text)
        val updates = json.optJSONArray("updates") ?: return emptyList()
        val result = LinkedHashMap<Long, String>()

        for (i in 0 until updates.length()) {
            val update = updates.getJSONObject(i)
            when (update.optString("update_type")) {
                "message_created" -> {
                    val message = update.optJSONObject("message") ?: continue
                    val chatId = message.optJSONObject("recipient")?.optLong("chat_id", -1L) ?: -1L
                    val name = message.optJSONObject("sender")?.optString("name")
                    if (chatId > 0) result[chatId] = name ?: "Диалог"
                }
                "bot_started", "user_added" -> {
                    val chatId = update.optLong("chat_id", -1L)
                    val name = update.optJSONObject("user")?.optString("name")
                    if (chatId > 0) result[chatId] = name ?: "Диалог"
                }
            }
        }
        return result.map { DetectedChat(it.key, it.value) }
    }
}
