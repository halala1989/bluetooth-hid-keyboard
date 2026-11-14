package com.hidble.phonekeyboard

import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** 集中管理虚拟键盘的输出速度与中文输出方式。 */
class OutputSettingsActivity : AppCompatActivity() {

    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedValue: TextView
    private lateinit var modeGroup: RadioGroup
    private lateinit var modeHint: TextView
    private lateinit var settingsState: TextView
    private var loading = true

    private val modeByRadio = mapOf(
        R.id.outputModeAltX to TypingEngine.MODE_ALTX,
        R.id.outputModeHex to TypingEngine.MODE_HEX,
        R.id.outputModeDecimal to TypingEngine.MODE_DECIMAL,
        R.id.outputModeGbk to TypingEngine.MODE_GBK
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_output_settings)
        KeyboardRuntime.initialize(this)

        speedSeekBar = findViewById(R.id.outputSpeedSeekBar)
        speedValue = findViewById(R.id.outputSpeedValue)
        modeGroup = findViewById(R.id.outputModeGroup)
        modeHint = findViewById(R.id.outputModeHint)
        settingsState = findViewById(R.id.outputSettingsState)

        findViewById<Button>(R.id.outputSettingsBackButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.outputSettingsSaveButton).setOnClickListener { saveSettings() }

        val settings = OutputPreferences.load(this)
        speedSeekBar.progress = settings.speedLevel
        speedValue.text = settings.speedLevel.toString()
        modeGroup.check(radioForMode(settings.unicodeMode))
        modeHint.text = OutputPreferences.modeDescription(settings.unicodeMode)
        loading = false

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val level = progress.coerceIn(1, 10)
                speedValue.text = level.toString()
                if (fromUser) markChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (loading) return@setOnCheckedChangeListener
            modeHint.text = OutputPreferences.modeDescription(modeByRadio[checkedId] ?: TypingEngine.MODE_ALTX)
            markChanged()
        }
    }

    private fun radioForMode(mode: Int): Int =
        modeByRadio.entries.firstOrNull { it.value == mode }?.key ?: R.id.outputModeAltX

    private fun markChanged() {
        settingsState.text = "有未保存的修改"
        settingsState.setTextColor(getColor(R.color.clinical_warning))
    }

    private fun saveSettings() {
        val mode = modeByRadio[modeGroup.checkedRadioButtonId] ?: TypingEngine.MODE_ALTX
        val settings = OutputSettings(
            speedLevel = speedSeekBar.progress.coerceIn(1, 10),
            unicodeMode = mode
        )
        OutputPreferences.save(this, settings)
        Toast.makeText(
            this,
            "已保存：速度 ${settings.speedLevel} · ${settings.modeLabel}",
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }
}
