package com.bg2max.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Приёмник broadcast'а AndroidAPS с IOB/COB и прочим статусом (модуль «Samsung Tizen»,
 * TizenPlugin.kt в исходниках AAPS). Объявлен в манифесте, а не из кода: AAPS ищет
 * получателей через queryBroadcastReceivers и шлёт каждому адресно, динамически
 * зарегистрированный приёмник он не найдёт.
 *
 * Сохраняем разобранный статус для сообщения (AapsStatus), последний пакет целиком —
 * для диагностики на главном экране, и пишем в журнал краткую строку.
 */
class AapsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_AAPS_STATUS = "info.nightscout.androidaps.status"
        private const val TAG = "bg2max.aaps"

        /** AAPS шлёт пакет по нескольку раз в минуту — одинаковые строки в журнал не пишем. */
        private const val REPEAT_LOG_INTERVAL_MS = 10 * 60_000L

        @Volatile
        private var lastLogged: String? = null

        @Volatile
        private var lastLoggedAt = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AAPS_STATUS) return
        val extras = intent.extras ?: return

        val dump = dumpExtras(extras)
        Log.d(TAG, dump)
        val now = System.currentTimeMillis()
        Prefs.setLastAapsDump(context, now, dump)
        AapsStatus.save(context, AapsStatus.fromExtras(extras, now))
        val line = "AAPS: " + summary(extras)
        if (line != lastLogged || now - lastLoggedAt > REPEAT_LOG_INTERVAL_MS) {
            lastLogged = line
            lastLoggedAt = now
            Prefs.appendLog(context, line)
        }
    }

    private fun summary(extras: Bundle): String {
        val parts = mutableListOf<String>()
        AapsStatus.number(extras, "iob")?.let { parts += "IOB %.2f Ед".format(it) }
        AapsStatus.number(extras, "cob")?.takeIf { it >= 0 }?.let { parts += "COB %.0f г".format(it) }
        if (parts.isEmpty()) parts += "пакет без IOB/COB, полей: ${extras.size()}"
        return parts.joinToString(", ")
    }

    @Suppress("DEPRECATION")
    private fun dumpExtras(extras: Bundle): String =
        extras.keySet().sorted().joinToString("\n") { key ->
            val value = extras.get(key)
            val type = value?.javaClass?.simpleName ?: "null"
            val text = value.toString().let { if (it.length > 200) it.take(200) + "…" else it }
            "$key ($type) = $text"
        }
}
