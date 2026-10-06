package com.bg2max.app

import android.content.Context

/**
 * Единая точка обработки нового показания глюкозы — используется и настоящим
 * приёмником broadcast от xDrip+ (GlucoseForwardService), и симулятором из
 * MainActivity для тестирования без xDrip+. Может выполнять сетевой запрос (вариант bot),
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

        val text = GlucoseUtils.formatMessage(reading, Prefs.getMessageOptions(context))
        // Способ отправки свой у каждого варианта приложения: src/bot или src/reply.
        ReadingSender.send(context, text, "%.1f ммоль/л %s".format(mmol, arrow), reading.timestampMs)
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
