package com.hidble.phonekeyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

class LiteKeyboardActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "hidble_prefs"
        private const val KEY_SPEED_LEVEL = "speed_level"
        private const val KEY_UNICODE_MODE = "unicode_mode"
        private const val KEY_PHRASES = "phrases"
        private const val PERMISSION_REQUEST_CODE = 3101
    }

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var terminalDot: TextView
    private lateinit var terminalName: TextView
    private lateinit var terminalDetail: TextView
    private lateinit var textInput: EditText
    private lateinit var sendButton: Button
    private lateinit var sendState: TextView
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedValue: TextView
    private lateinit var unicodeModeSpinner: Spinner

    private val unicodeModes = listOf(
        "Alt+X（推荐）" to TypingEngine.MODE_ALTX,
        "十六进制" to TypingEngine.MODE_HEX,
        "十进制" to TypingEngine.MODE_DECIMAL,
        "GBK（需 NumLock）" to TypingEngine.MODE_GBK
    )
    private var suppressModeEvent = false
    private var sending = false

    private val phoneStateListener: (KeyboardSnapshot) -> Unit = {
        runOnUiThread { refreshTerminalStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lite_keyboard)
        KeyboardRuntime.initialize(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        terminalDot = findViewById(R.id.liteConnectionDot)
        terminalName = findViewById(R.id.liteTerminalName)
        terminalDetail = findViewById(R.id.liteTerminalDetail)
        textInput = findViewById(R.id.liteTextInput)
        sendButton = findViewById(R.id.liteSendButton)
        sendState = findViewById(R.id.liteSendState)
        speedSeekBar = findViewById(R.id.liteSpeedSeekBar)
        speedValue = findViewById(R.id.liteSpeedValue)
        unicodeModeSpinner = findViewById(R.id.liteUnicodeModeSpinner)

        findViewById<Button>(R.id.liteManageConnectionButton).setOnClickListener {
            startActivity(Intent(this, LiteConnectionActivity::class.java))
        }
        findViewById<Button>(R.id.litePhraseButton).setOnClickListener {
            showPhrasesDialog()
        }
        sendButton.setOnClickListener { sendText() }
        setupSpeed()
        setupUnicodeMode()
        requestBluetoothPermissions()
    }

    override fun onResume() {
        super.onResume()
        KeyboardRuntime.refreshConnection()
        KeyboardRuntime.addStateListener(phoneStateListener)
        BoardLink.get(this).apply {
            onConnectionStateChanged = { runOnUiThread { refreshTerminalStatus() } }
            onError = { message ->
                runOnUiThread {
                    sendState.text = "连接错误：$message"
                    sendState.setTextColor(ContextCompat.getColor(this@LiteKeyboardActivity, R.color.clinical_danger))
                }
            }
        }
        OutputRouter.applySettingsToBoard(this)
        refreshTerminalStatus()
    }

    override fun onPause() {
        super.onPause()
        KeyboardRuntime.removeStateListener(phoneStateListener)
        BoardLink.get(this).apply {
            onConnectionStateChanged = null
            onError = null
        }
    }

    private fun setupSpeed() {
        val saved = prefs.getInt(KEY_SPEED_LEVEL, 5).coerceIn(1, 10)
        speedSeekBar.progress = saved
        speedValue.text = saved.toString()
        applySpeed(saved)
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val level = progress.coerceIn(1, 10)
                speedValue.text = level.toString()
                applySpeed(level)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun applySpeed(level: Int) {
        prefs.edit().putInt(KEY_SPEED_LEVEL, level).apply()
        KeyboardRuntime.hidProtocol()?.setSpeed(level)
        BoardLink.setSpeed(level)
    }

    private fun setupUnicodeMode() {
        val labels = unicodeModes.map { it.first }
        val adapter = ArrayAdapter(this, R.layout.item_spinner, R.id.spinnerText, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        unicodeModeSpinner.adapter = adapter
        val saved = prefs.getInt(KEY_UNICODE_MODE, TypingEngine.MODE_ALTX)
        val index = unicodeModes.indexOfFirst { it.second == saved }.coerceAtLeast(0)
        suppressModeEvent = true
        unicodeModeSpinner.setSelection(index, false)
        suppressModeEvent = false
        applyUnicodeMode(saved)
        unicodeModeSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressModeEvent) return
                val mode = unicodeModes.getOrElse(position) { unicodeModes.first() }.second
                applyUnicodeMode(mode)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })
    }

    private fun applyUnicodeMode(mode: Int) {
        prefs.edit().putInt(KEY_UNICODE_MODE, mode).apply()
        KeyboardRuntime.hidProtocol()?.setUnicodeMode(mode)
        BoardLink.setUnicodeMode(mode)
    }

    private fun sendText() {
        if (sending) return
        val text = textInput.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "请先输入要发送的文字", Toast.LENGTH_SHORT).show()
            return
        }
        val terminal = OutputRouter.selected(this)
        if (!OutputRouter.isConnected(this, terminal)) {
            sendState.text = "${terminal.displayName} 尚未连接"
            sendState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            Toast.makeText(this, "请先选择终端并完成连接", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LiteConnectionActivity::class.java))
            return
        }

        sending = true
        sendButton.isEnabled = false
        sendButton.text = "发送中…"
        sendState.text = "正在通过 ${terminal.displayName} 输出…"
        sendState.setTextColor(ContextCompat.getColor(this, R.color.accent))
        OutputRouter.sendText(this, text) { success, message ->
            runOnUiThread {
                sending = false
                sendButton.isEnabled = true
                sendButton.text = "发送到当前终端"
                sendState.text = message
                sendState.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (success) R.color.connected else R.color.clinical_danger
                    )
                )
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshTerminalStatus() {
        if (!::terminalName.isInitialized) return
        val terminal = OutputRouter.selected(this)
        val connected = OutputRouter.isConnected(this, terminal)
        terminalName.text = terminal.displayName
        terminalDot.setTextColor(
            ContextCompat.getColor(this, if (connected) R.color.connected else R.color.disconnected)
        )
        terminalDetail.text = when {
            connected && terminal.id == TerminalRegistry.PHONE_HID ->
                "已连接：${KeyboardRuntime.snapshot().deviceName ?: "目标电脑"}"
            connected -> "已连接：${BoardLink.connectedAddress() ?: "外接板"}"
            else -> terminal.description
        }
        if (!sending) {
            sendState.text = if (connected) "终端已连接，可以发送" else "连接终端后即可发送"
            sendState.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (connected) R.color.connected else R.color.text_hint
                )
            )
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                denied.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showPhrasesDialog() {
        val phrases = loadPhrases().toMutableList()
        if (phrases.isEmpty()) {
            showPhraseEditor(-1)
            return
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, phrases)
        val listView = ListView(this).apply {
            this.adapter = adapter
            setOnItemClickListener { _, _, position, _ ->
                insertPhrase(phrases[position])
            }
            setOnItemLongClickListener { _, _, position, _ ->
                showPhraseActions(position)
                true
            }
        }
        AlertDialog.Builder(this)
            .setTitle("常用语")
            .setView(listView)
            .setPositiveButton("新增") { _, _ -> showPhraseEditor(-1) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showPhraseActions(index: Int) {
        val phrases = loadPhrases().toMutableList()
        val phrase = phrases.getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle(phrase)
            .setPositiveButton("修改") { _, _ -> showPhraseEditor(index) }
            .setNeutralButton("删除") { _, _ ->
                phrases.removeAt(index)
                savePhrases(phrases)
                Toast.makeText(this, "常用语已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPhraseEditor(index: Int) {
        val phrases = loadPhrases().toMutableList()
        val input = EditText(this).apply {
            setText(phrases.getOrNull(index).orEmpty())
            hint = "输入常用语"
            setSingleLine(false)
            minLines = 2
        }
        AlertDialog.Builder(this)
            .setTitle(if (index >= 0) "修改常用语" else "新增常用语")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    if (index >= 0) phrases[index] = value else phrases.add(value)
                    savePhrases(phrases)
                    Toast.makeText(this, "常用语已保存", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadPhrases(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PHRASES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }.getOrDefault(emptyList())

    private fun savePhrases(phrases: List<String>) {
        val array = JSONArray()
        phrases.forEach(array::put)
        prefs.edit().putString(KEY_PHRASES, array.toString()).apply()
    }

    private fun insertPhrase(phrase: String) {
        val editable = textInput.text
        val start = textInput.selectionStart.coerceAtLeast(0)
        val end = textInput.selectionEnd.coerceAtLeast(0)
        val from = minOf(start, end)
        val to = maxOf(start, end)
        val prefix = if (from > 0 && editable[from - 1] != ' ' && editable[from - 1] != '\n') " " else ""
        val suffix = if (to < editable.length && editable[to] != ' ' && editable[to] != '\n') " " else ""
        editable.replace(from, to, prefix + phrase + suffix)
        textInput.setSelection(from + prefix.length + phrase.length)
        textInput.requestFocus()
    }
}
