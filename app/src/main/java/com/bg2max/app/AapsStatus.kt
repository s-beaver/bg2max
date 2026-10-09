package com.bg2max.app

import android.content.Context
import android.os.Bundle

/**
 * Последний статус из broadcast'а AndroidAPS (см. AapsStatusReceiver) — то, что можно
 * добавить в сообщение. AAPS есть не у всех: без него статуса просто нет, и строки
 * из AAPS в сообщение не попадают.
 *
 * Поля сверены с реальным пакетом 09.10.2026: iob, cob, pumpReservoir, pumpBattery,
 * phoneBattery, profile, pumpTimeStamp, pumpStatus («LastConn: 4 min ago\nLastBolus: 1,50U @ 18:03…»).
 * Данных о сенсоре CGM (заряд, возраст) в пакете нет.
 */
data class AapsStatus(
    val receivedAt: Long,
    val iob: Double?,
    val cob: Double?,
    val reservoir: Double?,
    val pumpBattery: Int?,
    val phoneBattery: Int?,
    val profile: String?,
    /** Время последней связи AAPS с помпой. */
    val pumpTimeMs: Long?,
    /** Последний болюс в виде «1,50 Ед в 18:03» — из текста pumpStatus, если его удалось разобрать. */
    val lastBolus: String?
) {
    companion object {
        /** Статус старше этого в сообщение не идёт: AAPS мог остановиться, значения устарели. */
        const val MAX_AGE_MS = 10 * 60_000L

        private const val FILE = "bg2max_aaps"
        private val LAST_BOLUS = Regex("""LastBolus:\s*([\d.,]+)\s*U\s*@\s*(\d{1,2}:\d{2})""")

        fun fromExtras(extras: Bundle, receivedAt: Long): AapsStatus {
            val pumpStatus = extras.getString("pumpStatus")
            return AapsStatus(
                receivedAt = receivedAt,
                iob = number(extras, "iob"),
                // AAPS шлёт отрицательный COB, когда он неизвестен.
                cob = number(extras, "cob")?.takeIf { it >= 0 },
                reservoir = number(extras, "pumpReservoir")?.takeIf { it >= 0 },
                pumpBattery = number(extras, "pumpBattery")?.toInt()?.takeIf { it in 0..100 },
                phoneBattery = number(extras, "phoneBattery")?.toInt()?.takeIf { it in 0..100 },
                profile = extras.getString("profile")?.takeIf { it.isNotBlank() },
                pumpTimeMs = number(extras, "pumpTimeStamp")?.toLong()?.takeIf { it > 0 },
                lastBolus = pumpStatus?.let { LAST_BOLUS.find(it) }?.let { m ->
                    "${m.groupValues[1].replace('.', ',')} Ед в ${m.groupValues[2]}"
                }
            )
        }

        /** Тип поля заранее неизвестен (Double/Int/Long/String) — берём как есть. */
        fun number(extras: Bundle, key: String): Double? =
            @Suppress("DEPRECATION")
            when (val v = extras.get(key)) {
                is Number -> v.toDouble()
                is String -> v.replace(',', '.').toDoubleOrNull()
                else -> null
            }

        private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        fun save(context: Context, s: AapsStatus) {
            prefs(context).edit().clear().apply {
                putLong("receivedAt", s.receivedAt)
                s.iob?.let { putFloat("iob", it.toFloat()) }
                s.cob?.let { putFloat("cob", it.toFloat()) }
                s.reservoir?.let { putFloat("reservoir", it.toFloat()) }
                s.pumpBattery?.let { putInt("pumpBattery", it) }
                s.phoneBattery?.let { putInt("phoneBattery", it) }
                s.profile?.let { putString("profile", it) }
                s.pumpTimeMs?.let { putLong("pumpTimeMs", it) }
                s.lastBolus?.let { putString("lastBolus", it) }
            }.apply()
        }

        /** Когда последний раз приходили данные от AAPS; 0 — ни разу. */
        fun lastReceivedAt(context: Context): Long = prefs(context).getLong("receivedAt", 0L)

        /** Статус, полученный не раньше чем за MAX_AGE_MS до [now]; иначе null. */
        fun loadFresh(context: Context, now: Long): AapsStatus? {
            val p = prefs(context)
            val receivedAt = p.getLong("receivedAt", 0L)
            if (receivedAt == 0L || now - receivedAt > MAX_AGE_MS) return null
            fun float(key: String) = if (p.contains(key)) p.getFloat(key, 0f).toDouble() else null
            fun int(key: String) = if (p.contains(key)) p.getInt(key, 0) else null
            return AapsStatus(
                receivedAt = receivedAt,
                iob = float("iob"),
                cob = float("cob"),
                reservoir = float("reservoir"),
                pumpBattery = int("pumpBattery"),
                phoneBattery = int("phoneBattery"),
                profile = p.getString("profile", null),
                pumpTimeMs = if (p.contains("pumpTimeMs")) p.getLong("pumpTimeMs", 0L) else null,
                lastBolus = p.getString("lastBolus", null)
            )
        }
    }
}
