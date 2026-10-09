package com.bg2max.app

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.PowerManager

/**
 * Вариант reply: отправка показания «ответом из уведомления» — так же, как если бы
 * пользователь сам ответил на сообщение в шторке. Сообщение уходит от имени аккаунта MAX
 * на этом телефоне в выбранный чат; сетью занимается само приложение MAX.
 *
 * Если кнопки ответа нет (процесс перезапускался, MAX её отменил, отправка заблокирована),
 * последнее показание откладывается и уходит, как только из выбранного чата придёт новое
 * уведомление — если оно к тому времени не устарело.
 */
object ReadingSender {

    /** Отложенное показание старше этого уже не отправляем — придёт следующее. */
    private const val PENDING_MAX_AGE_MS = 10 * 60_000L

    /**
     * Подтверждение «передача возобновлена» уходит, только если подряд не ушло столько показаний
     * (≈15 минут): короткие сбои не стоят лишнего сообщения в семейном чате.
     */
    private const val RESUME_NOTICE_MIN_MISSED = 3

    private enum class Kind { READING, TEST, NOTICE }

    private class Pending(val text: String, val summary: String, val readingTimeMs: Long, val kind: Kind)

    private var pending: Pending? = null

    @Volatile
    private var lastSentAt = 0L

    @Synchronized
    fun send(context: Context, text: String, summary: String, readingTimeMs: Long) {
        trySend(context, Pending(text, summary, readingTimeMs, Kind.READING), keepIfUndelivered = true)
    }

    /** Тестовое сообщение: не откладывается, результат сразу виден в журнале. */
    @Synchronized
    fun sendTest(context: Context, text: String): Boolean =
        trySend(context, Pending(text, "тестовое сообщение", System.currentTimeMillis(), Kind.TEST), keepIfUndelivered = false)

    /** Вызывается слушателем уведомлений, когда получена кнопка ответа выбранного чата. */
    @Synchronized
    fun onButtonCaptured(context: Context) {
        val waiting = pending
        pending = null
        if (waiting != null) {
            val ageMin = (System.currentTimeMillis() - waiting.readingTimeMs) / 60_000
            if (ageMin * 60_000 <= PENDING_MAX_AGE_MS) {
                // Подтверждение о возобновлении (если был перерыв) уйдёт первой строкой этого сообщения.
                Prefs.appendLog(context, "Отправляю отложенное показание ($ageMin мин назад)")
                trySend(context, waiting, keepIfUndelivered = false)
                return
            }
            Prefs.appendLog(context, "Отложенное показание (${waiting.summary}) устарело на $ageMin мин — не отправляю")
        }
        // Показания для отправки нет — сообщаем о возобновлении сразу, не дожидаясь следующего,
        // чтобы написавший в чат увидел, что это сработало.
        val outage = ReplyPrefs.getOutage(context)
        if (outage != null && outage.missed >= RESUME_NOTICE_MIN_MISSED) {
            val notice = resumeNotice(outage) + " Следующее показание придёт в течение 5 минут."
            trySend(context, Pending(notice, "подтверждение о возобновлении", System.currentTimeMillis(), Kind.NOTICE),
                keepIfUndelivered = false)
            return
        }
        updateStatus(context)
    }

    /** «✅ Передача возобновлена. Показания не приходили с 23:28 (пропущено 112).» */
    private fun resumeNotice(outage: ReplyPrefs.Outage): String {
        val sameDay = android.text.format.DateUtils.isToday(outage.sinceMs)
        val since = android.text.format.DateFormat.format(if (sameDay) "HH:mm" else "dd.MM HH:mm", outage.sinceMs)
        return "✅ Передача возобновлена. Показания не приходили с $since (пропущено ${outage.missed})."
    }

    /**
     * Кнопка ответа: из памяти, а после перезапуска процесса — из сохранённой копии
     * (ReplyBackup). null — кнопки нет, нужно новое сообщение из чата.
     */
    @Synchronized
    fun currentButton(context: Context): ReplyButton? {
        ReplyState.target?.let { return it }
        val restored = ReplyBackup.restore(context) ?: return null
        ReplyState.target = restored
        val ageMin = (System.currentTimeMillis() - restored.capturedAt) / 60_000
        Prefs.appendLog(context, "Кнопка ответа «${restored.chatName}» восстановлена после перезапуска " +
            "(получена $ageMin мин назад)")
        ReplyState.changed()
        return restored
    }

    private fun trySend(context: Context, item: Pending, keepIfUndelivered: Boolean): Boolean {
        val wanted = ReplyPrefs.getTarget(context)
        val button = currentButton(context)
        val blockedBy = ReplyState.blockedBy
        val problem = when {
            wanted == null -> "чат не выбран"
            button == null -> "нет кнопки ответа в «${wanted.chatName}» (процесс перезапускался или MAX её отменил) — " +
                "нужно новое сообщение из этого чата"
            blockedBy != null -> "заблокировано: у «$blockedBy» та же кнопка ответа — нужно новое сообщение из «${wanted.chatName}»"
            else -> null
        }
        if (problem != null) {
            if (keepIfUndelivered) pending = item
            if (item.kind == Kind.READING) ReplyPrefs.recordMissed(context, item.readingTimeMs)
            Prefs.appendLog(context, "НЕ отправлено (${item.summary}): $problem; ${deviceState(context)}")
            updateStatus(context)
            return false
        }
        button!!

        // После перерыва первое показание несёт строку о возобновлении.
        val outage = if (item.kind == Kind.READING) ReplyPrefs.getOutage(context) else null
        val text = if (outage != null && outage.missed >= RESUME_NOTICE_MIN_MISSED) {
            resumeNotice(outage) + "\n\n" + item.text
        } else item.text

        val results = Bundle()
        button.remoteInputs.forEach { results.putCharSequence(it.resultKey, text) }
        val fillIn = Intent()
        RemoteInput.addResultsToIntent(button.remoteInputs, fillIn, results)
        if (Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)

        val buttonAgeMin = (System.currentTimeMillis() - button.capturedAt) / 60_000
        return try {
            button.pendingIntent.send(context, 0, fillIn)
            lastSentAt = System.currentTimeMillis()
            if (item.kind != Kind.TEST) {
                ReplyPrefs.getOutage(context)?.let {
                    Prefs.appendLog(context, "Передача возобновлена после перерыва: пропущено ${it.missed} показаний" +
                        if (it.missed >= RESUME_NOTICE_MIN_MISSED) ", в чат отправлено подтверждение" else "")
                }
                ReplyPrefs.clearOutage(context)
            }
            Prefs.appendLog(context, "Передано в MAX → «${button.chatName}»: ${item.summary} " +
                "(кнопке $buttonAgeMin мин; ${deviceState(context)})")
            updateStatus(context)
            true
        } catch (e: PendingIntent.CanceledException) {
            // MAX отменил кнопку — до нового уведомления из чата отправлять нечем.
            if (ReplyState.target === button) ReplyState.target = null
            ReplyBackup.clear(context)
            if (keepIfUndelivered) pending = item
            if (item.kind == Kind.READING) ReplyPrefs.recordMissed(context, item.readingTimeMs)
            Prefs.appendLog(context, "НЕ отправлено (${item.summary}): MAX отменил кнопку ответа " +
                "(ей было $buttonAgeMin мин); ${deviceState(context)}")
            updateStatus(context)
            ReplyState.changed()
            false
        }
    }

    /** Текст постоянного уведомления: видно на телефоне, идёт ли отправка. */
    fun updateStatus(context: Context) {
        val wanted = ReplyPrefs.getTarget(context)
        val text = when {
            wanted == null -> "⚠ Чат для показаний не выбран — откройте приложение"
            ReplyState.blockedBy != null ->
                "⚠ Отправка остановлена. Нужно новое сообщение в MAX из чата «${wanted.chatName}»"
            currentButton(context) == null ->
                "⚠ Нет связи с чатом «${wanted.chatName}». Нужно новое сообщение в MAX из этого чата"
            lastSentAt == 0L -> "Готов отправлять показания в «${wanted.chatName}»"
            else -> "Последнее показание передано в «${wanted.chatName}» в " +
                android.text.format.DateFormat.format("HH:mm", lastSentAt)
        }
        GlucoseForwardService.setStatus(context, text)
    }

    /** Экран, Doze и сеть в момент отправки — чтобы по журналу разбирать задержки и пропуски. */
    fun deviceState(context: Context): String {
        val pm = context.getSystemService(PowerManager::class.java)
        val screen = if (pm.isInteractive) "экран вкл" else "экран выкл"
        val doze = if (pm.isDeviceIdleMode) "Doze" else "не Doze"
        return "$screen, $doze, ${networkState(context)}"
    }

    /**
     * «интернет не подтверждён» — Android не смог достучаться до своего сервера проверки
     * (у Google) — типичная картина при белых списках, хотя MAX при этом может работать.
     */
    private fun networkState(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "сети нет"
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "моб. сеть"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "сеть"
        }
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "$type, интернет " + if (validated) "подтверждён" else "не подтверждён"
    }
}
