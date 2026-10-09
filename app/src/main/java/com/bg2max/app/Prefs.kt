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
    private const val KEY_LAST_EXIT_LOGGED = "last_exit_logged"
    private const val KEY_INCLUDE_DELTA = "include_delta"
    private const val KEY_INCLUDE_RATE = "include_rate"
    private const val KEY_INCLUDE_NOISE = "include_noise"
    private const val KEY_INCLUDE_BATTERY = "include_battery"
    private const val KEY_INCLUDE_SOURCE = "include_source"
    private const val KEY_INCLUDE_SENSOR_AGE = "include_sensor_age"
    private const val KEY_INCLUDE_IOB = "include_iob"
    private const val KEY_INCLUDE_COB = "include_cob"
    private const val KEY_INCLUDE_RESERVOIR = "include_reservoir"
    private const val KEY_INCLUDE_PUMP_BATTERY = "include_pump_battery"
    private const val KEY_INCLUDE_PHONE_BATTERY = "include_phone_battery"
    private const val KEY_INCLUDE_PROFILE = "include_profile"
    private const val KEY_INCLUDE_PUMP_STATUS = "include_pump_status"
    private const val LOG_FILE = "bg2max.log"
    private const val LOG_FILE_MAX_BYTES = 1_000_000L

    /** Какие необязательные поля добавлять в текст сообщения, отправляемого в MAX. */
    data class MessageOptions(
        val includeDelta: Boolean,
        val includeRate: Boolean,
        val includeNoise: Boolean,
        val includeBattery: Boolean,
        val includeSource: Boolean,
        val includeSensorAge: Boolean,
        // Из AndroidAPS: без AAPS (или при устаревших данных) эти строки не выводятся.
        val includeIob: Boolean = false,
        val includeCob: Boolean = false,
        val includeReservoir: Boolean = false,
        val includePumpBattery: Boolean = false,
        val includePhoneBattery: Boolean = false,
        val includeProfile: Boolean = false,
        val includePumpStatus: Boolean = false
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

    /**
     * Короткий журнал (30 строк, новые сверху) для экрана и полный — в файле, чтобы
     * после многодневной проверки можно было переслать его целиком («Поделиться журналом»).
     */
    @Synchronized
    fun appendLog(context: Context, line: String) {
        val now = System.currentTimeMillis()
        val timeStr = android.text.format.DateFormat.format("HH:mm:ss", now)
        val existing = prefs(context).getString(KEY_LOG, "") ?: ""
        val updated = (listOf("[$timeStr] $line") + existing.lines().filter { it.isNotBlank() })
            .take(30)
            .joinToString("\n")
        prefs(context).edit().putString(KEY_LOG, updated).apply()

        val dateStr = android.text.format.DateFormat.format("dd.MM HH:mm:ss", now)
        runCatching {
            val file = logFile(context)
            if (file.length() > LOG_FILE_MAX_BYTES) {
                file.copyTo(java.io.File(context.filesDir, "$LOG_FILE.old"), overwrite = true)
                file.writeText("")
            }
            file.appendText("[$dateStr] $line\n")
        }
    }

    private fun logFile(context: Context) = java.io.File(context.filesDir, LOG_FILE)

    /** Полный журнал из файла: предыдущая часть (если файл уже переполнялся) + текущая. */
    @Synchronized
    fun getFullLog(context: Context): String {
        val old = java.io.File(context.filesDir, "$LOG_FILE.old")
        return (if (old.exists()) old.readText() else "") +
            (logFile(context).takeIf { it.exists() }?.readText() ?: "")
    }

    @Synchronized
    fun clearLog(context: Context) {
        prefs(context).edit().remove(KEY_LOG).apply()
        logFile(context).delete()
        java.io.File(context.filesDir, "$LOG_FILE.old").delete()
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

    /** Время последней записанной в журнал гибели процесса — чтобы не писать её повторно. */
    fun getLastExitLogged(context: Context): Long = prefs(context).getLong(KEY_LAST_EXIT_LOGGED, 0L)
    fun setLastExitLogged(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_LAST_EXIT_LOGGED, value).apply()
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
            includeSensorAge = p.getBoolean(KEY_INCLUDE_SENSOR_AGE, false),
            includeIob = p.getBoolean(KEY_INCLUDE_IOB, false),
            includeCob = p.getBoolean(KEY_INCLUDE_COB, false),
            includeReservoir = p.getBoolean(KEY_INCLUDE_RESERVOIR, false),
            includePumpBattery = p.getBoolean(KEY_INCLUDE_PUMP_BATTERY, false),
            includePhoneBattery = p.getBoolean(KEY_INCLUDE_PHONE_BATTERY, false),
            includeProfile = p.getBoolean(KEY_INCLUDE_PROFILE, false),
            includePumpStatus = p.getBoolean(KEY_INCLUDE_PUMP_STATUS, false)
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
            .putBoolean(KEY_INCLUDE_IOB, options.includeIob)
            .putBoolean(KEY_INCLUDE_COB, options.includeCob)
            .putBoolean(KEY_INCLUDE_RESERVOIR, options.includeReservoir)
            .putBoolean(KEY_INCLUDE_PUMP_BATTERY, options.includePumpBattery)
            .putBoolean(KEY_INCLUDE_PHONE_BATTERY, options.includePhoneBattery)
            .putBoolean(KEY_INCLUDE_PROFILE, options.includeProfile)
            .putBoolean(KEY_INCLUDE_PUMP_STATUS, options.includePumpStatus)
            .apply()
    }
}
