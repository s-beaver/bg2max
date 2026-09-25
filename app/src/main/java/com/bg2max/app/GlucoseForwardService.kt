package com.bg2max.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground-сервис: пока запущен, слушает локальные broadcast-интенты от xDrip+
 * (см. Local_Broadcast_Glucose_Protocol.md) и пересылает новые показания глюкозы в MAX.
 */
class GlucoseForwardService : Service() {

    companion object {
        private const val CHANNEL_ID = "bg2max_service"
        private const val NOTIFICATION_ID = 1

        private const val ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"
        private const val ACTION_STATUS_UPDATE = "com.eveningoutpost.dexdrip.StatusUpdate"

        private const val EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        private const val EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        private const val EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time"

        // Необязательные поля того же broadcast'а — см. GlucoseReading.
        private const val EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        private const val EXTRA_SENSOR_BATTERY = "com.eveningoutpost.dexdrip.Extras.SensorBattery"
        private const val EXTRA_NOISE_WARNING = "com.eveningoutpost.dexdrip.Extras.NoiseWarning"
        private const val EXTRA_SENSOR_STARTED_AT = "com.eveningoutpost.dexdrip.Extras.SensorStartedAt"
        private const val EXTRA_SOURCE_DESC = "com.eveningoutpost.dexdrip.Extras.SourceDesc"

        /** xDrip хранит слоп в мг/дл в миллисекунду, переводим в мг/дл в минуту. */
        private const val SLOPE_MS_TO_MIN = 60_000.0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_NEW_BG_ESTIMATE) return
            val extras = intent.extras ?: return
            val mgdl = extras.getDouble(EXTRA_BG_ESTIMATE, 0.0)
            if (mgdl <= 0.0) return

            val trendName = extras.getString(EXTRA_BG_SLOPE_NAME)
            val timestamp = extras.getLong(EXTRA_TIMESTAMP, System.currentTimeMillis())

            val reading = GlucoseReading(
                mgdl = mgdl,
                trendName = trendName,
                timestampMs = timestamp,
                rateMgdlPerMin = if (extras.containsKey(EXTRA_BG_SLOPE)) {
                    extras.getDouble(EXTRA_BG_SLOPE) * SLOPE_MS_TO_MIN
                } else null,
                sensorBatteryPercent = if (extras.containsKey(EXTRA_SENSOR_BATTERY)) {
                    extras.getInt(EXTRA_SENSOR_BATTERY)
                } else null,
                noiseWarning = if (extras.containsKey(EXTRA_NOISE_WARNING)) {
                    extras.getInt(EXTRA_NOISE_WARNING)
                } else null,
                sourceDescription = extras.getString(EXTRA_SOURCE_DESC),
                sensorStartedAtMs = if (extras.containsKey(EXTRA_SENSOR_STARTED_AT)) {
                    extras.getLong(EXTRA_SENSOR_STARTED_AT)
                } else null
            )

            scope.launch {
                GlucoseEventHandler.handle(applicationContext, reading)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(ACTION_NEW_BG_ESTIMATE)
            addAction(ACTION_STATUS_UPDATE)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        Prefs.appendLog(applicationContext, "Сервис v${BuildConfig.VERSION_NAME} запущен, ожидаю данные от xDrip+")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
        Prefs.appendLog(applicationContext, "Сервис остановлен")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
