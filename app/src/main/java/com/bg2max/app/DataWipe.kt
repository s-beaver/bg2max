package com.bg2max.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * «Удалить все данные» и ссылка на политику конфиденциальности — требование RuStore
 * (п. 5.1 правил модерации). Всё, что хранит приложение, лежит на телефоне: SharedPreferences,
 * журнал в filesDir, копия журнала для «Поделиться» в cacheDir, кнопка ответа в уведомлении.
 */
object DataWipe {

    const val PRIVACY_URL = "https://s-beaver.github.io/bg2max/privacy.html"

    fun privacyIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))

    /** Останавливает трансляцию и удаляет всё; после этого приложение как после установки. */
    fun wipeAll(context: Context) {
        context.stopService(Intent(context, GlucoseForwardService::class.java))
        context.getSystemService(NotificationManager::class.java).cancelAll()
        // Под тем же замком, что Prefs.appendLog, — чтобы журнал не дописался посреди удаления.
        synchronized(Prefs) {
            File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { file ->
                val name = file.name.removeSuffix(".xml")
                // clear() — ещё и для копии в памяти процесса, delete — для файла.
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                context.deleteSharedPreferences(name)
            }
            context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}
