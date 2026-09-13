package com.bg2max.app

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Минимальный клиент MAX Bot API.
 *
 * Host: platform-api2.max.ru, токен передаётся в заголовке Authorization
 * (без префикса Bearer). Передача токена через query-параметр access_token
 * отключена платформой — старый host botapi.max.ru отвечает 401 на любой запрос.
 */
object MaxApiClient {

    private const val TIMEOUT_MS = 15000
    private const val BASE_URL = "https://platform-api2.max.ru"

    class ApiException(message: String) : Exception(message)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun request(path: String, params: String, token: String, method: String, jsonBody: String?): String {
        val url = URL("$BASE_URL$path${if (params.isNotEmpty()) "?$params" else ""}")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Authorization", token)
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

    /** Проверяет токен, вызывая GET /me. Бросает ApiException при неверном токене. */
    fun checkToken(token: String) {
        request("/me", "", token, "GET", null)
    }

    fun sendMessage(token: String, chatId: String, text: String) {
        val body = JSONObject().apply { put("text", text) }.toString()
        request("/messages", "chat_id=${encode(chatId)}", token, "POST", body)
    }

    data class DetectedChat(val chatId: Long, val title: String)

    /** Возвращает title группы/канала (GET /chats/{chatId}). Для личных диалогов title всегда null. */
    private fun fetchChatTitle(token: String, chatId: Long): String? = runCatching {
        val text = request("/chats/$chatId", "", token, "GET", null)
        JSONObject(text).optString("title").takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Читает последние обновления бота (GET /updates) и извлекает chat_id
     * из событий message_created / bot_started / user_added (личные диалоги)
     * и bot_added (бота добавили в группу или канал — MAX присылает только
     * chat_id, название группы отдельно запрашивается через GET /chats/{chatId}).
     * Перед вызовом пользователь должен написать боту в MAX или добавить его в группу.
     */
    fun detectChatIds(token: String): List<DetectedChat> {
        val text = request("/updates", "limit=100", token, "GET", null)

        val json = JSONObject(text)
        val updates = json.optJSONArray("updates") ?: return emptyList()
        val result = LinkedHashMap<Long, String>()
        val titleCache = HashMap<Long, String?>()
        fun titleFor(chatId: Long) = titleCache.getOrPut(chatId) { fetchChatTitle(token, chatId) }

        for (i in 0 until updates.length()) {
            val update = updates.getJSONObject(i)
            when (update.optString("update_type")) {
                "message_created" -> {
                    val message = update.optJSONObject("message") ?: continue
                    val chatId = message.optJSONObject("recipient")?.optLong("chat_id", -1L) ?: -1L
                    if (chatId <= 0) continue
                    val senderName = message.optJSONObject("sender")?.optString("name")
                    result[chatId] = titleFor(chatId) ?: senderName ?: "Диалог"
                }
                "bot_started", "user_added" -> {
                    val chatId = update.optLong("chat_id", -1L)
                    if (chatId <= 0) continue
                    val name = update.optJSONObject("user")?.optString("name")
                    result[chatId] = titleFor(chatId) ?: name ?: "Диалог"
                }
                "bot_added" -> {
                    val chatId = update.optLong("chat_id", -1L)
                    if (chatId <= 0) continue
                    val fallback = if (update.optBoolean("is_channel", false)) "Канал" else "Группа"
                    result[chatId] = titleFor(chatId) ?: fallback
                }
            }
        }
        return result.map { DetectedChat(it.key, it.value) }
    }
}
