package com.bg2max.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle

/**
 * Копия кнопки ответа, которая переживает перезапуск процесса.
 *
 * PendingIntent нельзя записать на диск, но он живёт в системе, пока его держит кто-то,
 * кроме нашего процесса. Поэтому кнопку кладём в extras своего тихого уведомления: система
 * хранит уведомление (а с ним и PendingIntent MAX), даже когда процесс убит. После
 * перезапуска достаём кнопку оттуда — новое сообщение в чате не нужно.
 *
 * Не помогает, если приложение остановлено принудительно (тогда система убирает и его
 * уведомления), если уведомление смахнули или MAX отменил кнопку — тогда, как раньше,
 * ждём новое сообщение из чата. Ночной тест 30.09–01.10.2026: одна кнопка работала ≥8,5 ч.
 */
object ReplyBackup {

    private const val CHANNEL_ID = "bg2max_reply_backup"
    private const val NOTIFICATION_ID = 2

    private const val KEY_ACTION = "bg2max.reply.action"
    private const val KEY_CHAT_NAME = "bg2max.reply.chatName"
    private const val KEY_CHANNEL_ID = "bg2max.reply.channelId"
    private const val KEY_NOTIFICATION_ID = "bg2max.reply.notificationId"
    private const val KEY_CAPTURED_AT = "bg2max.reply.capturedAt"

    fun save(context: Context, button: ReplyButton) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Сохранённая кнопка ответа", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Нужна, чтобы отправка показаний продолжилась после перезапуска приложения"
                    setShowBadge(false)
                }
            )
        }
        val action = Notification.Action.Builder(null, "reply", button.pendingIntent).apply {
            button.remoteInputs.forEach { addRemoteInput(it) }
        }.build()
        val extras = Bundle().apply {
            putParcelable(KEY_ACTION, action)
            putString(KEY_CHAT_NAME, button.chatName)
            putString(KEY_CHANNEL_ID, button.channelId)
            putInt(KEY_NOTIFICATION_ID, button.notificationId)
            putLong(KEY_CAPTURED_AT, button.capturedAt)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Связь с «${button.chatName}» сохранена")
            .setContentText("Не смахивайте: по ней отправка продолжится после перезапуска")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addExtras(extras)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Prefs.appendLog(context, "Не удалось сохранить кнопку ответа: ${it.message}") }
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /**
     * Кнопка из сохранённого уведомления, если она от выбранного сейчас чата; иначе null.
     * Своё уведомление приложение читает через NotificationManager — слушатель для этого не нужен.
     */
    fun restore(context: Context): ReplyButton? {
        val wanted = ReplyPrefs.getTarget(context) ?: return null
        val manager = context.getSystemService(NotificationManager::class.java)
        val sbn = runCatching { manager.activeNotifications }.getOrNull()
            ?.firstOrNull { it.id == NOTIFICATION_ID } ?: return null
        val extras = sbn.notification.extras
        val action = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(KEY_ACTION, Notification.Action::class.java)
        } else {
            @Suppress("DEPRECATION") extras.getParcelable(KEY_ACTION)
        }
        val intent = action?.actionIntent
        val inputs = action?.remoteInputs
        if (intent == null || inputs.isNullOrEmpty()) {
            Prefs.appendLog(context, "Сохранённая кнопка ответа не читается — жду новое сообщение из чата")
            clear(context)
            return null
        }
        val button = ReplyButton(
            chatName = extras.getString(KEY_CHAT_NAME).orEmpty(),
            channelId = extras.getString(KEY_CHANNEL_ID).orEmpty(),
            notificationId = extras.getInt(KEY_NOTIFICATION_ID),
            pendingIntent = intent,
            remoteInputs = inputs,
            capturedAt = extras.getLong(KEY_CAPTURED_AT)
        )
        if (!ReplyPrefs.matches(wanted, button)) {
            Prefs.appendLog(context, "Сохранённая кнопка от «${button.chatName}», а выбран «${wanted.chatName}» — не использую")
            clear(context)
            return null
        }
        return button
    }
}
