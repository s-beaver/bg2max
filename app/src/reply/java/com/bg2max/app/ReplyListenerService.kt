package com.bg2max.app

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * Следит за уведомлениями MAX и запоминает кнопку «Ответить» выбранного чата.
 * Кнопки других чатов только попадают в список для выбора — отвечать туда нельзя:
 * в пробнике это однажды привело к отправке показаний в школьные чаты.
 *
 * В журнал пишется каждое уведомление MAX (имя чата, без текста сообщения) — по нему
 * видно, доходят ли уведомления, например, при ограничениях мобильного интернета.
 */
class ReplyListenerService : NotificationListenerService() {

    companion object {
        const val MAX_PACKAGE = "ru.oneme.app"
    }

    override fun onListenerConnected() {
        ReplyState.listenerConnected = true
        Prefs.appendLog(this, "Доступ к уведомлениям: слушатель подключён")
        // Уведомление выбранного чата могло остаться в шторке — тогда кнопка
        // восстанавливается сразу после перезапуска процесса.
        activeNotifications?.filter { it.packageName == MAX_PACKAGE }?.forEach { process(it, "в шторке") }
        val wanted = ReplyPrefs.getTarget(this)
        if (wanted != null && ReplyState.target == null && ReadingSender.currentButton(this) != null) {
            ReadingSender.onButtonCaptured(this)
        }
        if (wanted != null && ReplyState.target == null) {
            Prefs.appendLog(this, "Кнопки ответа «${wanted.chatName}» нет — жду новое сообщение из этого чата")
        }
        ReadingSender.updateStatus(this)
        ReplyState.changed()
    }

    override fun onListenerDisconnected() {
        ReplyState.listenerConnected = false
        Prefs.appendLog(this, "Доступ к уведомлениям: слушатель отключён системой, прошу переподключить")
        requestRebind(ComponentName(this, ReplyListenerService::class.java))
        ReplyState.changed()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == MAX_PACKAGE) process(sbn, "новое")
    }

    /**
     * Кто убрал уведомление выбранного чата. Если оно остаётся в шторке, после перезапуска
     * процесса кнопка восстанавливается из неё (см. onListenerConnected) — а по журналу
     * 08–09.10 в шторке к моменту перезапуска ничего не было.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (sbn.packageName != MAX_PACKAGE) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val chatName = (n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: n.extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val wanted = ReplyPrefs.getTarget(this) ?: return
        if (chatName != wanted.chatName && sbn.id != wanted.notificationId) return
        Prefs.appendLog(this, "MAX: уведомление «$chatName», id=${sbn.id} убрано из шторки — ${removalReason(reason)}")
    }

    private fun removalReason(reason: Int): String = when (reason) {
        REASON_CLICK -> "нажали на уведомление"
        REASON_CANCEL -> "смахнули"
        REASON_CANCEL_ALL -> "«очистить все»"
        REASON_APP_CANCEL, REASON_APP_CANCEL_ALL -> "убрал сам MAX (например, чат прочитан или отправлен ответ)"
        REASON_GROUP_SUMMARY_CANCELED -> "убрана вся группа уведомлений"
        REASON_PACKAGE_CHANGED, REASON_PACKAGE_BANNED -> "MAX обновлён или уведомления MAX отключены"
        REASON_LISTENER_CANCEL, REASON_LISTENER_CANCEL_ALL -> "убрал слушатель уведомлений"
        REASON_TIMEOUT -> "истёк срок показа"
        REASON_USER_STOPPED -> "MAX остановлен"
        else -> "причина $reason"
    }

    private fun process(sbn: StatusBarNotification, event: String) {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Для групп title = «Группа: Отправитель», имя чата лежит в conversationTitle.
        val chatName = (n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: n.extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val button = findReplyButton(n, chatName, sbn.id)
        if (button == null) {
            Prefs.appendLog(this, "MAX ($event): «$chatName», id=${sbn.id}, без кнопки ответа")
            return
        }
        ReplyState.addCandidate(button)

        val wanted = ReplyPrefs.getTarget(this)
        val saved = ReplyState.target
        if (wanted == null || !ReplyPrefs.matches(wanted, button)) {
            val sameIntent = saved != null && button.pendingIntent == saved.pendingIntent
            Prefs.appendLog(this, "MAX ($event): «$chatName» (${button.kind}), id=${sbn.id} — не наш чат" +
                if (sameIntent) "" else ", пропускаю")
            if (sameIntent) {
                ReplyState.blockedBy = chatName
                Prefs.appendLog(this, "ОПАСНО: у «$chatName» та же кнопка ответа (PendingIntent), что у «${saved!!.chatName}». " +
                    "Ответ мог бы уйти не туда — отправка остановлена до нового сообщения из «${saved.chatName}»")
                ReadingSender.updateStatus(this)
            }
            ReplyState.changed()
            return
        }

        if (button.channelId != wanted.channelId) {
            Prefs.appendLog(this, "MAX ($event): «$chatName» пришло на канал ${button.channelId} " +
                "(при выборе был ${wanted.channelId}) — чат опознан по id уведомления ${sbn.id}")
        }
        if (wanted.notificationId != 0 && wanted.notificationId != sbn.id) {
            // Проверяем, постоянен ли id уведомления у чата (идея — опознавать чат по нему, а не по имени).
            Prefs.appendLog(this, "ВНИМАНИЕ: у «$chatName» id уведомления ${sbn.id}, а при выборе чата был ${wanted.notificationId}")
        }
        val renewed = saved == null || button.pendingIntent != saved.pendingIntent
        ReplyState.target = button
        if (renewed) ReplyBackup.save(this, button)
        val wasBlocked = ReplyState.blockedBy != null
        ReplyState.blockedBy = null
        Prefs.appendLog(this, "MAX ($event): «$chatName», id=${sbn.id} — кнопка ответа " +
            (if (saved == null) "получена" else if (renewed) "обновлена (новый PendingIntent)" else "та же") +
            if (wasBlocked) ", отправка снова разрешена" else "")
        ReadingSender.onButtonCaptured(this)
        ReplyState.changed()
    }

    private fun findReplyButton(n: Notification, chatName: String, notificationId: Int): ReplyButton? {
        val now = System.currentTimeMillis()
        val channel = n.channelId.orEmpty()
        n.actions.orEmpty().forEach { a ->
            val inputs = a.remoteInputs.orEmpty()
            if (a.actionIntent != null && inputs.any { it.allowFreeFormInput }) {
                return ReplyButton(chatName, channel, notificationId, a.actionIntent, inputs, now)
            }
        }
        // Кнопки для часов — на случай, если в основных ответа нет.
        NotificationCompat.WearableExtender(n).actions.forEach { a ->
            val inputs = a.remoteInputs.orEmpty()
            val intent = a.actionIntent
            if (intent != null && inputs.any { it.allowFreeFormInput }) {
                val platformInputs = inputs.map {
                    RemoteInput.Builder(it.resultKey)
                        .setLabel(it.label)
                        .setAllowFreeFormInput(it.allowFreeFormInput)
                        .setChoices(it.choices)
                        .addExtras(it.extras)
                        .build()
                }.toTypedArray()
                return ReplyButton(chatName, channel, notificationId, intent, platformInputs, now)
            }
        }
        return null
    }
}
