package com.bg2max.app

/**
 * Одно показание из broadcast xDrip+ (см. Local_Broadcast_Glucose_Protocol.md) вместе
 * с необязательными полями, которые пользователь может включить в сообщение MAX.
 * Поля, которых не было в конкретном broadcast (или которые эмулятор не заполнил),
 * остаются null и просто не попадают в текст сообщения.
 */
data class GlucoseReading(
    val mgdl: Double,
    val trendName: String?,
    val timestampMs: Long,
    val rateMgdlPerMin: Double? = null,
    val sensorBatteryPercent: Int? = null,
    val noiseWarning: Int? = null,
    val sourceDescription: String? = null,
    val sensorStartedAtMs: Long? = null
)
