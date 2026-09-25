package com.bg2max.app

import android.content.Context

/**
 * Единая точка обработки нового показания глюкозы — используется и настоящим
 * приёмником broadcast от xDrip+ (GlucoseForwardService), и симулятором из
 * MainActivity для тестирования без xDrip+. Выполняет сетевой запрос,
 * поэтому вызывать нужно из фонового потока/корутины.
 */
object GlucoseEventHandler {

    /** Если предыдущее показание старше, дельта теряет смысл (был пропуск данных). */
    private const val MAX_DELTA_GAP_MS = 15 * 60_000L

    fun handle(context: Context, incoming: GlucoseReading) {
        val reading = withDelta(incoming, Prefs.getLastMgdl(context))
        val mmol = GlucoseUtils.mgdlToMmol(reading.mgdl)
        val arrow = GlucoseUtils.trendArrow(reading.trendName)
        Prefs.setLastReading(context, reading.mgdl, mmol, arrow, reading.timestampMs)

        val token = Prefs.getToken(context)
        val chatId = Prefs.getChatId(context)
        if (token.isBlank() || chatId.isBlank()) {
            Prefs.appendLog(context, "Показание получено, но токен/chat ID не заданы")
            return
        }

        val text = GlucoseUtils.formatMessage(reading, Prefs.getMessageOptions(context))
        try {
            MaxApiClient.sendMessage(token, chatId, text)
            Prefs.appendLog(context, "Отправлено: %.1f ммоль/л %s".format(mmol, arrow))
        } catch (e: Exception) {
            Prefs.appendLog(context, "Ошибка отправки: ${e.message}")
        }
    }

    private fun withDelta(reading: GlucoseReading, previous: Pair<Double, Long>?): GlucoseReading {
        val (prevMgdl, prevTime) = previous ?: return reading
        val gapMs = reading.timestampMs - prevTime
        if (gapMs <= 0 || gapMs > MAX_DELTA_GAP_MS) return reading
        return reading.copy(
            deltaMgdl = reading.mgdl - prevMgdl,
            deltaMinutes = Math.round(gapMs / 60_000.0).toInt()
        )
    }
}
