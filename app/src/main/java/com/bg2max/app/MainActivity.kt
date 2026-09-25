package com.bg2max.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bg2max.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        binding.inputBotToken.setText(Prefs.getToken(this))
        binding.inputChatId.setText(Prefs.getChatId(this))
        binding.switchEnabled.isChecked = Prefs.isEnabled(this)

        val options = Prefs.getMessageOptions(this)
        binding.checkboxDelta.isChecked = options.includeDelta
        binding.checkboxRate.isChecked = options.includeRate
        binding.checkboxNoise.isChecked = options.includeNoise
        binding.checkboxBattery.isChecked = options.includeBattery
        binding.checkboxSource.isChecked = options.includeSource
        binding.checkboxSensorAge.isChecked = options.includeSensorAge

        binding.btnSave.setOnClickListener {
            saveSettings()
            if (currentToken().isNotBlank() && currentChatId().isNotBlank()) {
                collapseSettings()
            }
        }
        binding.btnEditSettings.setOnClickListener { expandSettings() }
        binding.btnTestMessage.setOnClickListener { sendTestMessage() }
        binding.btnSimulateReading.setOnClickListener { simulateReading() }
        binding.btnDetectChatId.setOnClickListener { detectChatId() }
        binding.btnDisclaimer.setOnClickListener { startActivity(DisclaimerActivity.viewIntent(this)) }
        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            onToggleEnabled(checked)
        }

        if (currentToken().isBlank() || currentChatId().isBlank()) {
            expandSettings()
        } else {
            collapseSettings()
        }

        refreshStatusViews()
    }

    private fun expandSettings() {
        binding.settingsPanel.visibility = View.VISIBLE
        binding.settingsSummary.visibility = View.GONE
    }

    private fun collapseSettings() {
        binding.settingsPanel.visibility = View.GONE
        binding.settingsSummary.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        refreshStatusViews()
    }

    private fun refreshStatusViews() {
        binding.textLastReading.text = Prefs.getLastReadingText(this)
        binding.textLog.text = Prefs.getLog(this)
        binding.textAapsDump.text = Prefs.getLastAapsDumpText(this)
    }

    private fun currentToken(): String = binding.inputBotToken.text?.toString()?.trim().orEmpty()
    private fun currentChatId(): String = binding.inputChatId.text?.toString()?.trim().orEmpty()

    private fun saveSettings() {
        Prefs.setToken(this, currentToken())
        Prefs.setChatId(this, currentChatId())
        Prefs.setMessageOptions(
            this,
            Prefs.MessageOptions(
                includeDelta = binding.checkboxDelta.isChecked,
                includeRate = binding.checkboxRate.isChecked,
                includeNoise = binding.checkboxNoise.isChecked,
                includeBattery = binding.checkboxBattery.isChecked,
                includeSource = binding.checkboxSource.isChecked,
                includeSensorAge = binding.checkboxSensorAge.isChecked
            )
        )
        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
    }

    private fun onToggleEnabled(checked: Boolean) {
        saveSettings()
        Prefs.setEnabled(this, checked)
        if (checked) {
            if (currentToken().isBlank() || currentChatId().isBlank()) {
                Toast.makeText(this, "Сначала укажите токен и chat ID", Toast.LENGTH_LONG).show()
                binding.switchEnabled.isChecked = false
                Prefs.setEnabled(this, false)
                return
            }
            requestNotificationPermissionThenStart()
        } else {
            stopService(Intent(this, GlucoseForwardService::class.java))
            Prefs.appendLog(this, "Трансляция выключена пользователем")
            refreshStatusViews()
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
        refreshStatusViews()
    }

    private fun sendTestMessage() {
        val token = currentToken()
        val chatId = currentChatId()
        if (token.isBlank() || chatId.isBlank()) {
            Toast.makeText(this, "Укажите токен и chat ID", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MaxApiClient.sendMessage(token, chatId, "✅ BG2MAX подключён и готов пересылать показания глюкозы")
                }
            }
            result.onSuccess {
                Prefs.appendLog(this@MainActivity, "Тестовое сообщение отправлено")
                Toast.makeText(this@MainActivity, "Отправлено", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Prefs.appendLog(this@MainActivity, "Ошибка теста: ${e.message}")
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
            refreshStatusViews()
        }
    }

    /**
     * Прогоняет случайное правдоподобное показание через тот же код, что использует
     * настоящий приёмник broadcast от xDrip+ — позволяет проверить весь конвейер
     * (конвертация единиц, стрелка тренда, отправка в MAX) без установки xDrip+.
     */
    private fun simulateReading() {
        val token = currentToken()
        val chatId = currentChatId()
        if (token.isBlank() || chatId.isBlank()) {
            Toast.makeText(this, "Укажите токен и chat ID", Toast.LENGTH_SHORT).show()
            return
        }
        saveSettings()

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
            withContext(Dispatchers.IO) {
                GlucoseEventHandler.handle(this@MainActivity, reading)
            }
            refreshStatusViews()
        }
    }

    private fun detectChatId() {
        val token = currentToken()
        if (token.isBlank()) {
            Toast.makeText(this, "Сначала укажите токен бота", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Ищу диалоги...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { MaxApiClient.detectChatIds(token) }
            }
            result.onSuccess { chats ->
                if (chats.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ничего не найдено. Попробуйте ещё раз сразу после сообщения боту, " +
                            "либо введите Chat ID вручную — его видно в адресе чата на web.max.ru",
                        Toast.LENGTH_LONG
                    ).show()
                    return@onSuccess
                }
                if (chats.size == 1) {
                    binding.inputChatId.setText(chats[0].chatId.toString())
                    Toast.makeText(this@MainActivity, "Chat ID найден: ${chats[0].chatId}", Toast.LENGTH_SHORT).show()
                } else {
                    showChatPicker(chats)
                }
            }.onFailure { e ->
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showChatPicker(chats: List<MaxApiClient.DetectedChat>) {
        val labels = chats.map { "${it.title} (${it.chatId})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Выберите чат")
            .setItems(labels) { _, index ->
                binding.inputChatId.setText(chats[index].chatId.toString())
            }
            .show()
    }
}
