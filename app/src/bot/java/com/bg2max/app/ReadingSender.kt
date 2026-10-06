package com.bg2max.app

import android.content.Context

/** Вариант bot: отправка показания через MAX Bot API в заданный chat ID. Вызывать из фонового потока. */
object ReadingSender {

    fun send(context: Context, text: String, summary: String, readingTimeMs: Long) {
        val token = Prefs.getToken(context)
        val chatId = Prefs.getChatId(context)
        if (token.isBlank() || chatId.isBlank()) {
            Prefs.appendLog(context, "Показание получено, но токен/chat ID не заданы")
            return
        }
        try {
            MaxApiClient.sendMessage(token, chatId, text)
            Prefs.appendLog(context, "Отправлено: $summary")
        } catch (e: Exception) {
            Prefs.appendLog(context, "Ошибка отправки: ${e.message}")
        }
    }
}
