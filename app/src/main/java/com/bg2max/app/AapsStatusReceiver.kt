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
 * Пока только диагностика: сохраняем последний пакет целиком, чтобы увидеть на
 * реальном телефоне, какие поля приходят, и пишем в журнал краткую строку.
 */
class AapsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_AAPS_STATUS = "info.nightscout.androidaps.status"
        private const val TAG = "bg2max.aaps"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AAPS_STATUS) return
        val extras = intent.extras ?: return

        val dump = dumpExtras(extras)
        Log.d(TAG, dump)
        Prefs.setLastAapsDump(context, System.currentTimeMillis(), dump)
        Prefs.appendLog(context, "AAPS: " + summary(extras))
    }

    private fun summary(extras: Bundle): String {
        val parts = mutableListOf<String>()
        numberOf(extras, "iob")?.let { parts += "IOB %.2f Ед".format(it) }
        numberOf(extras, "cob")?.takeIf { it >= 0 }?.let { parts += "COB %.0f г".format(it) }
        if (parts.isEmpty()) parts += "пакет без IOB/COB, полей: ${extras.size()}"
        return parts.joinToString(", ")
    }

    /** Тип поля заранее неизвестен (Double/Int/String) — берём как есть. */
    private fun numberOf(extras: Bundle, key: String): Double? =
        @Suppress("DEPRECATION")
        when (val v = extras.get(key)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
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
