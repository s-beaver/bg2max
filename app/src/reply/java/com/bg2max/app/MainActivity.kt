package com.bg2max.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bg2max.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Экран варианта reply: доступ к уведомлениям, выбор чата, работа в фоне, журнал. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            startForwardService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DisclaimerActivity.isAccepted(this)) {
            startActivity(Intent(this, DisclaimerActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textVersion.text = "v${BuildConfig.VERSION_NAME}"
        binding.switchEnabled.isChecked = Prefs.isEnabled(this)
        MessageOptionsView.show(binding.messageOptions, Prefs.getMessageOptions(this))

        binding.btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnPickChat.setOnClickListener { pickChat() }
        binding.btnBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }
        binding.btnAppSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        binding.btnSave.setOnClickListener { saveOptions(showToast = true) }
        binding.btnTestMessage.setOnClickListener { sendTestMessage() }
        binding.btnSimulateReading.setOnClickListener { simulateReading() }
        binding.btnShareLog.setOnClickListener { shareLog() }
        binding.btnClearLog.setOnClickListener { confirmClearLog() }
        binding.btnDisclaimer.setOnClickListener { startActivity(DisclaimerActivity.viewIntent(this)) }
        binding.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggleEnabled(checked) }
    }

    override fun onResume() {
        super.onResume()
        ReplyState.onChange = { runOnUiThread { refresh() } }
        // После выдачи доступа или перезапуска процесса система не всегда сразу подключает слушателя.
        if (hasListenerAccess() && !ReplyState.listenerConnected) {
            NotificationListenerService.requestRebind(ComponentName(this, ReplyListenerService::class.java))
        }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        ReplyState.onChange = null
    }

    private fun refresh() {
        binding.textAccess.text = when {
            !hasListenerAccess() -> "❌ Нет доступа — без него приложение не видит уведомления MAX"
            !ReplyState.listenerConnected -> "⏳ Доступ есть, слушатель ещё не подключился"
            else -> "✅ Доступ есть"
        }

        val wanted = ReplyPrefs.getTarget(this)
        val button = ReplyState.target
        binding.textTarget.text = when {
            wanted == null -> "Чат не выбран"
            ReplyState.blockedBy != null ->
                "«${wanted.chatName}» — ⚠ отправка остановлена: у «${ReplyState.blockedBy}» та же кнопка ответа. " +
                    "Нужно новое сообщение из «${wanted.chatName}»"
            button == null ->
                "«${wanted.chatName}» — ⚠ кнопки ответа нет. Нужно новое сообщение из этого чата в MAX"
            else -> "«${wanted.chatName}» (${button.kind}) — ✅ кнопка ответа получена в " +
                android.text.format.DateFormat.format("HH:mm", button.capturedAt)
        }

        val pm = getSystemService(PowerManager::class.java)
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        binding.textBattery.text =
            if (ignoring) "✅ Оптимизация батареи отключена" else "⚠ Оптимизация батареи включена — Android может усыплять приложение"
        binding.btnBattery.isEnabled = !ignoring

        binding.textLastReading.text = Prefs.getLastReadingText(this)
        binding.textLog.text = Prefs.getLog(this)
        binding.textAapsDump.text = Prefs.getLastAapsDumpText(this)
        MessageOptionsView.refreshAaps(binding.messageOptions, this)
    }

    private fun hasListenerAccess() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun pickChat() {
        val candidates = ReplyState.candidates()
        if (candidates.isEmpty()) {
            Toast.makeText(
                this,
                if (hasListenerAccess()) "Пока не было уведомлений MAX с кнопкой «Ответить». " +
                    "Попросите получателя написать на этот телефон и попробуйте снова"
                else "Сначала дайте доступ к уведомлениям (шаг 1)",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val labels = candidates.map {
            "${it.chatName} (${it.kind}, " + android.text.format.DateFormat.format("HH:mm", it.capturedAt) + ")"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Куда отправлять показания?")
            .setItems(labels) { _, index -> confirmChat(candidates[index]) }
            .show()
    }

    private fun confirmChat(button: ReplyButton) {
        AlertDialog.Builder(this)
            .setTitle("Отправлять в «${button.chatName}»?")
            .setMessage(
                "Показания глюкозы будут уходить в этот чат (${button.kind}) от имени вашего аккаунта MAX " +
                    "каждые 5 минут. Их увидят все участники чата."
            )
            .setPositiveButton("Да") { _, _ ->
                ReplyPrefs.setTarget(this, button)
                ReplyState.target = button
                ReplyState.blockedBy = null
                Prefs.appendLog(this, "Выбран чат «${button.chatName}» (${button.kind}, канал ${button.channelId}, id=${button.notificationId})")
                ReadingSender.updateStatus(this)
                refresh()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun saveOptions(showToast: Boolean) {
        Prefs.setMessageOptions(this, MessageOptionsView.read(binding.messageOptions))
        if (showToast) Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
    }

    private fun onToggleEnabled(checked: Boolean) {
        saveOptions(showToast = false)
        if (checked) {
            val problem = when {
                !hasListenerAccess() -> "Сначала дайте доступ к уведомлениям (шаг 1)"
                ReplyPrefs.getTarget(this) == null -> "Сначала выберите чат (шаг 2)"
                else -> null
            }
            if (problem != null) {
                Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
                binding.switchEnabled.isChecked = false
                return
            }
            Prefs.setEnabled(this, true)
            requestNotificationPermissionThenStart()
        } else if (Prefs.isEnabled(this)) {
            Prefs.setEnabled(this, false)
            stopService(Intent(this, GlucoseForwardService::class.java))
            Prefs.appendLog(this, "Трансляция выключена пользователем")
            refresh()
        }
    }

    private fun requestNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startForwardService()
        }
    }

    private fun startForwardService() {
        ContextCompat.startForegroundService(this, Intent(this, GlucoseForwardService::class.java))
        ReadingSender.updateStatus(this)
        refresh()
    }

    private fun sendTestMessage() {
        val ok = ReadingSender.sendTest(this, "✅ BG2MAX подключён и готов пересылать показания глюкозы")
        Toast.makeText(
            this,
            if (ok) "Передано в MAX — проверьте, появилось ли сообщение в чате" else "Не отправлено — причина в журнале",
            Toast.LENGTH_LONG
        ).show()
        refresh()
    }

    /** То же, что делает приёмник xDrip+, но со случайным показанием — проверка всей цепочки. */
    private fun simulateReading() {
        saveOptions(showToast = false)
        val trends = listOf("Flat", "SingleUp", "FortyFiveUp", "DoubleUp", "SingleDown", "FortyFiveDown", "DoubleDown")
        val timestamp = System.currentTimeMillis()
        val reading = GlucoseReading(
            mgdl = (70..220).random().toDouble(),
            trendName = trends.random(),
            timestampMs = timestamp,
            rateMgdlPerMin = (-30..30).random() / 10.0,
            sensorBatteryPercent = (40..100).random(),
            noiseWarning = listOf(0, 0, 0, 1, 2).random(),
            sourceDescription = "Симуляция (без xDrip+)",
            sensorStartedAtMs = timestamp - (0..9).random() * 86_400_000L
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { GlucoseEventHandler.handle(this@MainActivity, reading) }
            refresh()
        }
    }

    /** Полный журнал файлом: за несколько дней он не влезает в текст сообщения. */
    private fun shareLog() {
        val dir = File(cacheDir, "logs").apply { mkdirs() }
        val stamp = android.text.format.DateFormat.format("yyyy-MM-dd_HH-mm", System.currentTimeMillis())
        val file = File(dir, "bg2max-reply-log_$stamp.txt")
        file.writeText(
            "BG2MAX Ответ v${BuildConfig.VERSION_NAME}, ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n\n" +
                Prefs.getFullLog(this)
        )
        val uri = FileProvider.getUriForFile(this, "$packageName.logs", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Журнал BG2MAX"))
    }

    private fun confirmClearLog() {
        AlertDialog.Builder(this)
            .setTitle("Очистить журнал?")
            .setMessage("Если идёт проверка, сначала перешлите журнал — после очистки его не вернуть.")
            .setPositiveButton("Очистить") { _, _ ->
                Prefs.clearLog(this)
                refresh()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
