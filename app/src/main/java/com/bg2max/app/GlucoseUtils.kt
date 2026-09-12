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

    fun formatMessage(mmol: Double, trendArrow: String, timestampMs: Long): String {
        val timeStr = android.text.format.DateFormat.format("HH:mm", timestampMs)
        val arrowPart = if (trendArrow.isNotEmpty()) " $trendArrow" else ""
        return "🩸 Глюкоза: %.1f ммоль/л%s\n🕒 %s".format(mmol, arrowPart, timeStr)
    }
}
