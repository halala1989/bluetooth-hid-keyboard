package com.hidble.phonekeyboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var connectionStatus: TextView
    private lateinit var outputSummary: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        connectionStatus = findViewById(R.id.settingsConnectionStatus)
        outputSummary = findViewById(R.id.settingsOutputSummary)
        findViewById<Button>(R.id.settingsBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.settingsConnectionCard).setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }
        findViewById<View>(R.id.settingsOutputCard).setOnClickListener {
            startActivity(Intent(this, OutputSettingsActivity::class.java))
        }
        findViewById<View>(R.id.settingsLlmCard).setOnClickListener {
            startActivity(Intent(this, LlmSettingsActivity::class.java))
        }
        findViewById<View>(R.id.settingsSkillsCard).setOnClickListener {
            startActivity(Intent(this, WritingRulesActivity::class.java))
        }
        findViewById<View>(R.id.settingsAboutCard).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshConnectionStatus()
        refreshOutputSummary()
    }

    private fun refreshConnectionStatus() {
        val terminal = OutputRouter.selected(this)
        val connected = OutputRouter.isConnected(this, terminal)
        connectionStatus.text = if (connected) "已连接" else "未连接"
        connectionStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (connected) R.color.clinical_teal else R.color.clinical_danger
            )
        )
        connectionStatus.setBackgroundResource(
            if (connected) R.drawable.bg_status_chip else R.drawable.bg_danger_chip
        )
    }

    private fun refreshOutputSummary() {
        val settings = OutputPreferences.load(this)
        outputSummary.text =
            "速度 ${settings.speedLevel} 档 · ${settings.modeLabel} · 手机 HID / 开发板"
    }
}
