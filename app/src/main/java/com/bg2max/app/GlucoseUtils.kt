package com.bg2max.app

/** Преобразования показателей глюкозы из xDrip+ (см. Local_Broadcast_Glucose_Protocol.md). */
object GlucoseUtils {

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

    fun formatMessage(reading: GlucoseReading, options: Prefs.MessageOptions): String {
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
                lines += "📈 Скорость: %+.2f ммоль/л/мин".format(rateMmol)
            }
        }
        if (options.includeNoise) {
            reading.noiseWarning?.takeIf { it > 0 }?.let { level ->
                lines += "⚠️ Шум сигнала: уровень $level"
            }
        }
        if (options.includeBattery) {
            reading.sensorBatteryPercent?.let { battery ->
                lines += "🔋 Батарея сенсора: $battery%"
            }
        }
        if (options.includeSource) {
            reading.sourceDescription?.takeIf { it.isNotBlank() }?.let { source ->
                lines += "📡 Источник: $source"
            }
        }
        if (options.includeSensorAge) {
            reading.sensorStartedAtMs?.takeIf { it > 0 }?.let { startedAt ->
                val ageDays = (reading.timestampMs - startedAt) / 86_400_000.0
                lines += "🗓 Сенсор: день ${ageDays.toInt() + 1}"
            }
        }

        lines += "🕒 $timeStr"
        return lines.joinToString("\n")
    }
}
