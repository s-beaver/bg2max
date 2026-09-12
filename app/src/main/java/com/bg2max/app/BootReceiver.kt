package com.bg2max.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Перезапускает сервис трансляции после перезагрузки устройства, если он был включён. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.isEnabled(context)) {
            ContextCompat.startForegroundService(context, Intent(context, GlucoseForwardService::class.java))
        }
    }
}
