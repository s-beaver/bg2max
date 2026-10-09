package com.bg2max.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Пишет в журнал, почему система завершила прошлый процесс приложения (Android 11+).
 * Процесс могут убить молча — без onDestroy и строки «Сервис остановлен», — а для
 * варианта reply это потеря кнопки ответа. По причине видно, кто виноват: нехватка
 * памяти, оптимизация батареи прошивки, смахивание из недавних, обновление и т. п.
 */
object ExitReasons {

    fun logNew(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(ActivityManager::class.java)
        val exits = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 5) }
            .getOrNull().orEmpty()
        val lastLogged = Prefs.getLastExitLogged(context)
        val fresh = exits.filter { it.timestamp > lastLogged }.sortedBy { it.timestamp }
        if (fresh.isEmpty()) return
        fresh.forEach { e ->
            val time = android.text.format.DateFormat.format("dd.MM HH:mm:ss", e.timestamp)
            val details = listOfNotNull(
                e.description?.takeIf { it.isNotBlank() }?.let { "«$it»" },
                "важность ${e.importance}",
                "память ${e.pss / 1024} МБ"
            ).joinToString(", ")
            Prefs.appendLog(context, "Прошлый процесс завершён $time: ${reasonText(e.reason)} ($details)")
        }
        Prefs.setLastExitLogged(context, fresh.last().timestamp)
    }

    private fun reasonText(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "приложение завершилось само"
        ApplicationExitInfo.REASON_SIGNALED -> "убит сигналом (часто — оптимизация батареи прошивки)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "нехватка памяти"
        ApplicationExitInfo.REASON_CRASH -> "сбой приложения"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "сбой в нативном коде"
        ApplicationExitInfo.REASON_ANR -> "приложение не отвечало (ANR)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "ошибка запуска"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "изменены разрешения"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "слишком много ресурсов"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "остановлено пользователем (смахнули из недавних / «Остановить»)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "остановлено пользователем"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "умер зависимый процесс"
        ApplicationExitInfo.REASON_OTHER -> "другая причина системы"
        ApplicationExitInfo.REASON_FREEZER -> "заморожено системой"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "изменилось состояние пакета"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "приложение обновлено"
        else -> "неизвестно (код $reason)"
    }
}
