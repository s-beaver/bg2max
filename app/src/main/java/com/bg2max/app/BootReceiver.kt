package com.bg2max.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Перезапускает сервис трансляции, если он был включён: после перезагрузки устройства
 * и после обновления приложения — при обновлении Android останавливает процесс,
 * и сервис сам не поднимается (START_STICKY тут не срабатывает).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "после перезагрузки телефона"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "после обновления приложения"
            else -> return
        }
        GlucoseForwardService.ensureRunning(context, reason)
    }
}
