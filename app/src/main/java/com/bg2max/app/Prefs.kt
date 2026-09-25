package com.bg2max.app

import android.content.Context

/** Простое хранилище настроек в SharedPreferences. */
object Prefs {
    private const val FILE = "bg2max_prefs"
    private const val KEY_TOKEN = "bot_token"
    private const val KEY_CHAT_ID = "chat_id"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_MMOL = "last_mmol"
    private const val KEY_LAST_TREND = "last_trend"
    private const val KEY_LAST_TIME = "last_time"
    private const val KEY_LAST_MGDL = "last_mgdl"
    private const val KEY_LOG = "log"
    private const val KEY_AAPS_DUMP = "aaps_dump"
    private const val KEY_AAPS_DUMP_TIME = "aaps_dump_time"
    private const val KEY_DISCLAIMER_VERSION = "disclaimer_accepted_version"
    private const val KEY_INCLUDE_DELTA = "include_delta"
    private const val KEY_INCLUDE_RATE = "include_rate"
    private const val KEY_INCLUDE_NOISE = "include_noise"
    private const val KEY_INCLUDE_BATTERY = "include_battery"
    private const val KEY_INCLUDE_SOURCE = "include_source"
    private const val KEY_INCLUDE_SENSOR_AGE = "include_sensor_age"

    /** Какие необязательные поля добавлять в текст сообщения, отправляемого в MAX. */
    data class MessageOptions(
        val includeDelta: Boolean,
        val includeRate: Boolean,
        val includeNoise: Boolean,
        val includeBattery: Boolean,
        val includeSource: Boolean,
        val includeSensorAge: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getToken(context: Context): String = prefs(context).getString(KEY_TOKEN, "") ?: ""
    fun setToken(context: Context, value: String) {
        prefs(context).edit().putString(KEY_TOKEN, value).apply()
    }

    fun getChatId(context: Context): String = prefs(context).getString(KEY_CHAT_ID, "") ?: ""
    fun setChatId(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CHAT_ID, value).apply()
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** Предыдущее показание (мг/дл и время) — для расчёта дельты; null, если его ещё не было. */
    fun getLastMgdl(context: Context): Pair<Double, Long>? {
        val p = prefs(context)
        if (!p.contains(KEY_LAST_MGDL)) return null
        val time = p.getLong(KEY_LAST_TIME, 0L)
        if (time == 0L) return null
        return p.getFloat(KEY_LAST_MGDL, 0f).toDouble() to time
    }

    fun setLastReading(context: Context, mgdl: Double, mmol: Double, trendArrow: String, timestamp: Long) {
        prefs(context).edit()
            .putFloat(KEY_LAST_MGDL, mgdl.toFloat())
            .putFloat(KEY_LAST_MMOL, mmol.toFloat())
            .putString(KEY_LAST_TREND, trendArrow)
            .putLong(KEY_LAST_TIME, timestamp)
            .apply()
    }

    fun getLastReadingText(context: Context): String {
        val time = prefs(context).getLong(KEY_LAST_TIME, 0L)
        if (time == 0L) return "—"
        val mmol = prefs(context).getFloat(KEY_LAST_MMOL, 0f)
        val trend = prefs(context).getString(KEY_LAST_TREND, "") ?: ""
        val timeStr = android.text.format.DateFormat.format("HH:mm", time)
        return "%.1f ммоль/л %s (%s)".format(mmol, trend, timeStr)
    }

    fun appendLog(context: Context, line: String) {
        val timeStr = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        val existing = prefs(context).getString(KEY_LOG, "") ?: ""
        val updated = (listOf("[$timeStr] $line") + existing.lines().filter { it.isNotBlank() })
            .take(30)
            .joinToString("\n")
        prefs(context).edit().putString(KEY_LOG, updated).apply()
    }

    fun setLastAapsDump(context: Context, receivedAt: Long, dump: String) {
        prefs(context).edit()
            .putLong(KEY_AAPS_DUMP_TIME, receivedAt)
            .putString(KEY_AAPS_DUMP, dump)
            .apply()
    }

    fun getLastAapsDumpText(context: Context): String {
        val time = prefs(context).getLong(KEY_AAPS_DUMP_TIME, 0L)
        if (time == 0L) return "Данных от AndroidAPS пока не было"
        val timeStr = android.text.format.DateFormat.format("dd.MM HH:mm:ss", time)
        return "Получено $timeStr\n" + (prefs(context).getString(KEY_AAPS_DUMP, "") ?: "")
    }

    /** Версия отказа от ответственности, с которой согласился пользователь (0 — ещё не соглашался). */
    fun getAcceptedDisclaimerVersion(context: Context): Int =
        prefs(context).getInt(KEY_DISCLAIMER_VERSION, 0)

    fun setAcceptedDisclaimerVersion(context: Context, version: Int) {
        prefs(context).edit().putInt(KEY_DISCLAIMER_VERSION, version).apply()
    }

    fun getLog(context: Context): String = prefs(context).getString(KEY_LOG, "") ?: ""

    fun getMessageOptions(context: Context): MessageOptions {
        val p = prefs(context)
        return MessageOptions(
            includeDelta = p.getBoolean(KEY_INCLUDE_DELTA, false),
            includeRate = p.getBoolean(KEY_INCLUDE_RATE, false),
            includeNoise = p.getBoolean(KEY_INCLUDE_NOISE, false),
            includeBattery = p.getBoolean(KEY_INCLUDE_BATTERY, false),
            includeSource = p.getBoolean(KEY_INCLUDE_SOURCE, false),
            includeSensorAge = p.getBoolean(KEY_INCLUDE_SENSOR_AGE, false)
        )
    }

    fun setMessageOptions(context: Context, options: MessageOptions) {
        prefs(context).edit()
            .putBoolean(KEY_INCLUDE_DELTA, options.includeDelta)
            .putBoolean(KEY_INCLUDE_RATE, options.includeRate)
            .putBoolean(KEY_INCLUDE_NOISE, options.includeNoise)
            .putBoolean(KEY_INCLUDE_BATTERY, options.includeBattery)
            .putBoolean(KEY_INCLUDE_SOURCE, options.includeSource)
            .putBoolean(KEY_INCLUDE_SENSOR_AGE, options.includeSensorAge)
            .apply()
    }
}
