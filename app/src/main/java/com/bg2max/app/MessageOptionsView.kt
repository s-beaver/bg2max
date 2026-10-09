package com.bg2max.app

import android.content.Context
import android.view.View
import com.bg2max.app.databinding.ViewMessageOptionsBinding

/** Галочки «Что включать в сообщение» (view_message_options.xml) ↔ Prefs.MessageOptions. */
object MessageOptionsView {

    fun show(binding: ViewMessageOptionsBinding, options: Prefs.MessageOptions) {
        binding.checkboxDelta.isChecked = options.includeDelta
        binding.checkboxRate.isChecked = options.includeRate
        binding.checkboxNoise.isChecked = options.includeNoise
        binding.checkboxBattery.isChecked = options.includeBattery
        binding.checkboxSource.isChecked = options.includeSource
        binding.checkboxSensorAge.isChecked = options.includeSensorAge
        binding.checkboxIob.isChecked = options.includeIob
        binding.checkboxCob.isChecked = options.includeCob
        binding.checkboxReservoir.isChecked = options.includeReservoir
        binding.checkboxPumpBattery.isChecked = options.includePumpBattery
        binding.checkboxPhoneBattery.isChecked = options.includePhoneBattery
        binding.checkboxProfile.isChecked = options.includeProfile
        binding.checkboxPumpStatus.isChecked = options.includePumpStatus
    }

    /**
     * Блок «Из AndroidAPS»: у кого только xDrip+, тот его не видит. Показываем, если от AAPS
     * хоть раз приходили данные или галочка из блока уже включена (чтобы её можно было снять).
     * Время последних данных видно под заголовком — так заметно, что в AAPS выключена рассылка.
     */
    fun refreshAaps(binding: ViewMessageOptionsBinding, context: Context) {
        val receivedAt = AapsStatus.lastReceivedAt(context)
        val anyChecked = listOf(
            binding.checkboxIob, binding.checkboxCob, binding.checkboxReservoir, binding.checkboxPumpBattery,
            binding.checkboxPhoneBattery, binding.checkboxProfile, binding.checkboxPumpStatus
        ).any { it.isChecked }
        binding.aapsOptions.visibility = if (receivedAt != 0L || anyChecked) View.VISIBLE else View.GONE
        binding.textAapsReceived.text = if (receivedAt == 0L) {
            "⚠ Данных от AndroidAPS не было — включите в AAPS рассылку данных (модуль «Samsung Tizen» / «Data Broadcaster»)"
        } else {
            val time = android.text.format.DateFormat.format("dd.MM HH:mm:ss", receivedAt)
            val stale = System.currentTimeMillis() - receivedAt > AapsStatus.MAX_AGE_MS
            "Последние данные от AAPS: $time" + if (stale) " — устарели, в сообщение не идут" else ""
        }
    }

    fun read(binding: ViewMessageOptionsBinding) = Prefs.MessageOptions(
        includeDelta = binding.checkboxDelta.isChecked,
        includeRate = binding.checkboxRate.isChecked,
        includeNoise = binding.checkboxNoise.isChecked,
        includeBattery = binding.checkboxBattery.isChecked,
        includeSource = binding.checkboxSource.isChecked,
        includeSensorAge = binding.checkboxSensorAge.isChecked,
        includeIob = binding.checkboxIob.isChecked,
        includeCob = binding.checkboxCob.isChecked,
        includeReservoir = binding.checkboxReservoir.isChecked,
        includePumpBattery = binding.checkboxPumpBattery.isChecked,
        includePhoneBattery = binding.checkboxPhoneBattery.isChecked,
        includeProfile = binding.checkboxProfile.isChecked,
        includePumpStatus = binding.checkboxPumpStatus.isChecked
    )
}
