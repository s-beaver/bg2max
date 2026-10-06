package com.bg2max.app

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
    }

    fun read(binding: ViewMessageOptionsBinding) = Prefs.MessageOptions(
        includeDelta = binding.checkboxDelta.isChecked,
        includeRate = binding.checkboxRate.isChecked,
        includeNoise = binding.checkboxNoise.isChecked,
        includeBattery = binding.checkboxBattery.isChecked,
        includeSource = binding.checkboxSource.isChecked,
        includeSensorAge = binding.checkboxSensorAge.isChecked
    )
}
