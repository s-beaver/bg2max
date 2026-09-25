package com.bg2max.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bg2max.app.databinding.ActivityDisclaimerBinding

/**
 * Отказ от ответственности. При первом запуске (и после изменения текста — см.
 * CURRENT_VERSION) его нужно принять галочкой, иначе приложение не открывается.
 * Из главного экрана открывается повторно только для чтения.
 */
class DisclaimerActivity : AppCompatActivity() {

    companion object {
        /** Увеличить при существенном изменении текста — все пользователи примут его заново. */
        const val CURRENT_VERSION = 1

        private const val EXTRA_VIEW_ONLY = "view_only"

        fun isAccepted(context: Context): Boolean =
            Prefs.getAcceptedDisclaimerVersion(context) >= CURRENT_VERSION

        fun viewIntent(context: Context): Intent =
            Intent(context, DisclaimerActivity::class.java).putExtra(EXTRA_VIEW_ONLY, true)
    }

    private lateinit var binding: ActivityDisclaimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisclaimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Отказ от ответственности"

        binding.textDisclaimer.text =
            resources.openRawResource(R.raw.disclaimer).bufferedReader().use { it.readText() }

        if (intent.getBooleanExtra(EXTRA_VIEW_ONLY, false)) {
            binding.acceptPanel.visibility = View.GONE
            binding.btnClose.visibility = View.VISIBLE
            binding.btnClose.setOnClickListener { finish() }
            return
        }

        binding.checkboxAgree.setOnCheckedChangeListener { _, checked ->
            binding.btnAccept.isEnabled = checked
        }
        binding.btnAccept.setOnClickListener {
            Prefs.setAcceptedDisclaimerVersion(this, CURRENT_VERSION)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        binding.btnDecline.setOnClickListener { finishAffinity() }
    }
}
