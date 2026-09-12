package com.bg2max.app

import android.content.Context

/**
 * Единая точка обработки нового показания глюкозы — используется и настоящим
 * приёмником broadcast от xDrip+ (GlucoseForwardService), и симулятором из
 * MainActivity для тестирования без xDrip+. Выполняет сетевой запрос,
 * поэтому вызывать нужно из фонового потока/корутины.
 */
object GlucoseEventHandler {

    fun handle(context: Context, mgdl: Double, trendName: String?, timestampMs: Long) {
        val mmol = GlucoseUtils.mgdlToMmol(mgdl)
        val arrow = GlucoseUtils.trendArrow(trendName)
        Prefs.setLastReading(context, mmol, arrow, timestampMs)

        val token = Prefs.getToken(context)
        val chatId = Prefs.getChatId(context)
        if (token.isBlank() || chatId.isBlank()) {
            Prefs.appendLog(context, "Показание получено, но токен/chat ID не заданы")
            return
        }

        val text = GlucoseUtils.formatMessage(mmol, arrow, timestampMs)
        try {
            MaxApiClient.sendMessage(token, chatId, text)
            Prefs.appendLog(context, "Отправлено: %.1f ммоль/л %s".format(mmol, arrow))
        } catch (e: Exception) {
            Prefs.appendLog(context, "Ошибка отправки: ${e.message}")
        }
    }
}
