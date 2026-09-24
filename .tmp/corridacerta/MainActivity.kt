package com.rafael.radarcorrida

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var statusHelp: TextView
    private lateinit var statusDot: TextView
    private lateinit var statusBox: LinearLayout
    private lateinit var accessibilityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#174EA6")
        window.navigationBarColor = Color.parseColor("#101828")
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusHelp = findViewById(R.id.statusHelp)
        statusDot = findViewById(R.id.statusDot)
        statusBox = findViewById(R.id.statusBox)
        accessibilityButton = findViewById(R.id.accessibilityButton)

        val goodRate = findViewById<EditText>(R.id.goodRateInput)
        val excellentRate = findViewById<EditText>(R.id.excellentRateInput)
        val costPerKm = findViewById<EditText>(R.id.costPerKmInput)
        val minProfit = findViewById<EditText>(R.id.minProfitInput)
        val voice = findViewById<Switch>(R.id.voiceSwitch)

        goodRate.setText(format(AppConfig.goodRate(this)))
        excellentRate.setText(format(AppConfig.excellentRate(this)))
        costPerKm.setText(format(AppConfig.costPerKm(this)))
        minProfit.setText(format(AppConfig.minProfit(this)))
        voice.isChecked = AppConfig.voiceEnabled(this)

        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val g = parse(goodRate.text.toString(), 1.70)
            val e = parse(excellentRate.text.toString(), 2.50)
            val c = parse(costPerKm.text.toString(), 0.80)
            val p = parse(minProfit.text.toString(), 8.00)

            if (e < g) {
                Toast.makeText(
                    this,
                    "O valor de EXCELENTE deve ser igual ou maior que o NORMAL.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            AppConfig.save(this, g, e, c, p, voice.isChecked)
            Toast.makeText(this, "Pronto! Suas regras foram salvas.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isAccessibilityEnabled()
        if (enabled) {
            statusText.text = "Leitura ATIVA"
            statusHelp.text = "Corrida Certa está pronto para analisar as ofertas."
            statusDot.setTextColor(Color.parseColor("#12B76A"))
            statusBox.setBackgroundColor(Color.parseColor("#ECFDF3"))
            accessibilityButton.text = "GERENCIAR ACESSIBILIDADE"
        } else {
            statusText.text = "Leitura DESATIVADA"
            statusHelp.text = "Ative a acessibilidade para começar a analisar as corridas."
            statusDot.setTextColor(Color.parseColor("#F79009"))
            statusBox.setBackgroundColor(Color.parseColor("#FFF4E5"))
            accessibilityButton.text = "ATIVAR LEITURA DAS CORRIDAS"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, RideAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun parse(value: String, fallback: Double): Double =
        value.replace(',', '.').toDoubleOrNull() ?: fallback

    private fun format(value: Double) = "%.2f".format(value).replace('.', ',')
}
