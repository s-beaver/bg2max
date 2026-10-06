package com.bg2max.app

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context

/**
 * Кнопка «Ответить» из уведомления MAX одного чата. PendingIntent нельзя сохранить
 * на диск — кнопка живёт только в памяти процесса, после его перезапуска нужно новое
 * уведомление из этого чата.
 */
class ReplyButton(
    val chatName: String,
    /** Канал уведомления MAX: у личных диалогов и групп разные — отличает «Папа» от группы «Папа». */
    val channelId: String,
    val notificationId: Int,
    val pendingIntent: PendingIntent,
    val remoteInputs: Array<out RemoteInput>,
    val capturedAt: Long
) {
    val kind: String
        get() = when {
            channelId.contains("dialog") -> "личный чат"
            channelId.contains("chat") -> "группа"
            else -> "канал $channelId"
        }
}

/** Общее состояние процесса: слушатель уведомлений пишет, отправка и экран читают. */
object ReplyState {

    /** Сохранённая кнопка ответа выбранного чата; null — отправлять пока некуда. */
    @Volatile
    var target: ReplyButton? = null

    /**
     * Имя другого чата, в уведомлении которого пришёл ТОТ ЖЕ PendingIntent, что у сохранённой
     * кнопки. Тогда ответ мог бы уйти туда — отправка блокируется до нового уведомления
     * из выбранного чата. Обычно у каждого чата свой PendingIntent (проверено 02.10.2026).
     */
    @Volatile
    var blockedBy: String? = null

    @Volatile
    var listenerConnected = false

    /** Чаты, из которых видели уведомление с кнопкой ответа с момента запуска процесса — для выбора. */
    private val candidates = LinkedHashMap<String, ReplyButton>()

    @Volatile
    var onChange: (() -> Unit)? = null

    fun addCandidate(button: ReplyButton) {
        synchronized(candidates) {
            val key = button.channelId + "\u0000" + button.chatName
            candidates.remove(key)
            candidates[key] = button
        }
    }

    /** Самые свежие сверху. */
    fun candidates(): List<ReplyButton> = synchronized(candidates) { candidates.values.reversed() }

    fun changed() {
        onChange?.invoke()
    }
}

/** Выбранный чат — сохраняется на диск, в отличие от самой кнопки. */
object ReplyPrefs {
    private const val FILE = "bg2max_reply"
    private const val KEY_CHAT_NAME = "chat_name"
    private const val KEY_CHANNEL_ID = "channel_id"
    private const val KEY_NOTIFICATION_ID = "notification_id"

    data class Target(val chatName: String, val channelId: String, val notificationId: Int)

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getTarget(context: Context): Target? {
        val p = prefs(context)
        val name = p.getString(KEY_CHAT_NAME, null) ?: return null
        return Target(name, p.getString(KEY_CHANNEL_ID, "").orEmpty(), p.getInt(KEY_NOTIFICATION_ID, 0))
    }

    fun setTarget(context: Context, button: ReplyButton) {
        prefs(context).edit()
            .putString(KEY_CHAT_NAME, button.chatName)
            .putString(KEY_CHANNEL_ID, button.channelId)
            .putInt(KEY_NOTIFICATION_ID, button.notificationId)
            .apply()
    }

    /** Тот ли это чат: имя совпадает точно, и тип (личный/группа) тоже. */
    fun matches(target: Target, button: ReplyButton): Boolean =
        button.chatName == target.chatName && button.channelId == target.channelId
}
