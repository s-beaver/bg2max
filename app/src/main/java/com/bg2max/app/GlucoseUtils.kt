package com.bg2max.app

/** Преобразования показателей глюкозы из xDrip+ (см. Local_Broadcast_Glucose_Protocol.md). */
object GlucoseUtils {

    /**
     * Сенсоры старше этого считаем ошибкой данных: при источнике «Other App» (данные из
     * другого приложения) xDrip+ присылает дату старта своей давно забытой записи о сенсоре —
     * 09.10.2026 вышло «день 493». Самые долгие массовые сенсоры носятся 14–15 дней.
     */
    private const val MAX_SENSOR_AGE_DAYS = 30

    fun mgdlToMmol(mgdl: Double): Double = mgdl / 18.0182

    /** xDrip передаёт тренд как Nightscout-style строку, конвертируем в стрелку. */
    fun trendArrow(slopeName: String?): String = when (slopeName) {
        "DoubleUp" -> "⇈"       // ⇈
        "SingleUp" -> "↑"       // ↑
        "FortyFiveUp" -> "↗"    // ↗
        "Flat" -> "→"           // →
        "FortyFiveDown" -> "↘"  // ↘
        "SingleDown" -> "↓"     // ↓
        "DoubleDown" -> "⇊"     // ⇊
        else -> ""
    }

    /** [aaps] — свежий статус AndroidAPS или null (AAPS нет или данные устарели). */
    fun formatMessage(reading: GlucoseReading, options: Prefs.MessageOptions, aaps: AapsStatus? = null): String {
        val mmol = mgdlToMmol(reading.mgdl)
        val arrow = trendArrow(reading.trendName)
        val timeStr = android.text.format.DateFormat.format("HH:mm", reading.timestampMs)
        val arrowPart = if (arrow.isNotEmpty()) " $arrow" else ""

        val lines = mutableListOf("🩸 Глюкоза: %.1f ммоль/л%s".format(mmol, arrowPart))

        if (options.includeDelta) {
            reading.deltaMgdl?.let { delta ->
                val deltaMmol = mgdlToMmol(delta)
                val minutes = reading.deltaMinutes?.takeIf { it > 0 }?.let { " за $it мин" } ?: ""
                lines += "Δ Изменение: %+.1f ммоль/л%s".format(deltaMmol, minutes)
            }
        }
        if (options.includeRate) {
            reading.rateMgdlPerMin?.let { rate ->
                val rateMmol = mgdlToMmol(rate)
                val icon = if (rateMmol < 0) "📉" else "📈"
                lines += "$icon Скорость: %+.2f ммоль/л/мин".format(rateMmol)
            }
        }
        if (options.includeNoise) {
            reading.noiseWarning?.takeIf { it > 0 }?.let { level ->
                lines += "⚠️ Шум сигнала: уровень $level"
            }
        }
        if (options.includeBattery) {
            // -1 — xDrip+ не знает заряд (обычно при источнике «Other App»).
            reading.sensorBatteryPercent?.takeIf { it in 0..100 }?.let { battery ->
                lines += "🔋 Батарея сенсора: $battery%"
            }
        }
        if (options.includeSource) {
            reading.sourceDescription?.takeIf { it.isNotBlank() }?.let { source ->
                lines += "📡 Источник: $source"
            }
        }
        if (options.includeSensorAge) {
            reading.sensorStartedAtMs?.takeIf { it > 0 && it <= reading.timestampMs }?.let { startedAt ->
                val ageDays = ((reading.timestampMs - startedAt) / 86_400_000L).toInt()
                if (ageDays < MAX_SENSOR_AGE_DAYS) lines += "🗓 Сенсор: день ${ageDays + 1}"
            }
        }
        if (aaps != null) lines += aapsLines(aaps, options, reading.timestampMs)

        lines += "🕒 $timeStr"
        return lines.joinToString("\n")
    }

    private fun aapsLines(a: AapsStatus, options: Prefs.MessageOptions, nowMs: Long): List<String> {
        val lines = mutableListOf<String>()
        if (options.includeIob) a.iob?.let { lines += "💉 Активный инсулин: %.2f Ед".format(it) }
        if (options.includeCob) a.cob?.let { lines += "🍞 Активные углеводы: %.0f г".format(it) }
        if (options.includeReservoir) a.reservoir?.let { lines += "🧪 Резервуар помпы: %.0f Ед".format(it) }
        if (options.includePumpBattery) a.pumpBattery?.let { lines += "🔋 Батарея помпы: $it%" }
        if (options.includePhoneBattery) a.phoneBattery?.let { lines += "📱 Батарея телефона: $it%" }
        if (options.includeProfile) a.profile?.let { lines += "⚙️ Профиль: $it" }
        if (options.includePumpStatus) {
            a.pumpTimeMs?.let { pumpTime ->
                val minutes = ((nowMs - pumpTime) / 60_000L).coerceAtLeast(0)
                lines += "📶 Связь с помпой: $minutes мин назад"
            }
            a.lastBolus?.let { lines += "💧 Последний болюс: $it" }
        }
        return lines
    }
}
