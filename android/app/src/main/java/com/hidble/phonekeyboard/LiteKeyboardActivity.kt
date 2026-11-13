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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var clearButton: Button
    private lateinit var sendState: TextView
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedValue: TextView
    private lateinit var unicodeModeSpinner: Spinner

    private val unicodeModes = listOf(
        "Alt+X（推荐）" to TypingEngine.MODE_ALTX,
        "十六进制" to TypingEngine.MODE_HEX,
        "十进制" to TypingEngine.MODE_DECIMAL,
        "GBK（自动确保 NumLock）" to TypingEngine.MODE_GBK
    )
    private var suppressModeEvent = false
    private var outputControl: TextOutputControl? = null
    private var sendJob: Job? = null
    private var outputGeneration = 0
    private var outputPaused = false
    private var activeOutputIsBoard = false
    private var activeDeviceLabel = ""
    private var sentCharacters = 0
    private var totalCharacters = 0

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
        pauseButton = findViewById(R.id.litePauseButton)
        stopButton = findViewById(R.id.liteStopButton)
        clearButton = findViewById(R.id.liteClearButton)
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
        sendButton.setOnClickListener { handleSendButton() }
        pauseButton.setOnClickListener { pauseOutput() }
        stopButton.setOnClickListener { stopOutput() }
        clearButton.setOnClickListener { clearInput() }
        setupSpeed()
        setupUnicodeMode()
        refreshOutputControls()
        requestBluetoothPermissions()
    }

    override fun onResume() {
        super.onResume()
        KeyboardRuntime.refreshConnection()
        LiteConnectionMemory.load(this)?.let { LiteConnectionMemory.applyToRouter(this, it) }
        KeyboardRuntime.addStateListener(phoneStateListener)
        BoardLink.get(this).apply {
            onConnectionStateChanged = { runOnUiThread { refreshTerminalStatus() } }
            onDataReceived = { data ->
                if (data.startsWith("ERR")) {
                    runOnUiThread {
                        val message = if (data.contains("UNI_MODE_NOT_READY")) {
                            "当前开发板固件不支持此中文模式，请更新开发板固件"
                        } else {
                            "开发板返回错误：$data"
                        }
                        sendState.text = message
                        sendState.setTextColor(
                            ContextCompat.getColor(
                                this@LiteKeyboardActivity,
                                R.color.clinical_danger
                            )
                        )
                        Toast.makeText(
                            this@LiteKeyboardActivity,
                            message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
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
            onDataReceived = null
            onError = null
        }
    }

    override fun onDestroy() {
        if (outputControl != null) {
            outputGeneration++
            outputControl?.cancel()
            sendJob?.cancel()
            if (activeOutputIsBoard) BoardLink.stopOutput() else KeyboardRuntime.releaseAll()
            outputControl = null
            sendJob = null
        }
        super.onDestroy()
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

    private fun handleSendButton() {
        val control = outputControl
        if (control != null) {
            if (outputPaused) resumeOutput()
            return
        }
        startOutputFromBeginning()
    }

    private fun startOutputFromBeginning() {
        val text = textInput.text.toString()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        if (text.isBlank()) {
            Toast.makeText(this, "请先输入要发送的文字", Toast.LENGTH_SHORT).show()
            return
        }
        val terminal = OutputRouter.selected(this)
        if (!OutputRouter.isConnected(this, terminal)) {
            sendState.text = "当前设备尚未连接"
            sendState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            Toast.makeText(this, "请先连接设备或扫描新设备", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LiteConnectionActivity::class.java))
            return
        }

        val generation = ++outputGeneration
        val control = TextOutputControl()
        outputControl = control
        outputPaused = false
        activeOutputIsBoard = terminal.isBoard
        activeDeviceLabel = connectedDeviceName() ?: terminal.displayName
        sentCharacters = 0
        totalCharacters = text.codePointCount(0, text.length)
        control.onProgress = { sent, total ->
            runOnUiThread {
                if (generation == outputGeneration && outputControl === control) {
                    sentCharacters = sent
                    totalCharacters = total
                    if (!outputPaused) updateRunningState()
                }
            }
        }
        updateRunningState()
        refreshOutputControls()

        if (terminal.id == TerminalRegistry.PHONE_HID) {
            KeyboardRuntime.typeTextControlled(
                text = text,
                speed = prefs.getInt(KEY_SPEED_LEVEL, 5).coerceIn(1, 10),
                control = control
            ) { success, message ->
                runOnUiThread { finishOutput(generation, success, message) }
            }
        } else {
            sendJob = lifecycleScope.launch {
                try {
                    sendToBoardControlled(text, control)
                    finishOutput(generation, true, "发送完成")
                } catch (error: CancellationException) {
                    if (generation == outputGeneration && !control.cancelled) {
                        finishOutput(generation, false, "发送已中断")
                    }
                } catch (error: Exception) {
                    finishOutput(
                        generation,
                        false,
                        error.message ?: "发送失败"
                    )
                }
            }
        }
    }

    private suspend fun sendToBoardControlled(text: String, control: TextOutputControl) {
        val board = BoardLink.get(this)
        if (!board.isConnected()) error("当前设备尚未连接")
        val speedResponse = board.sendCommandAwait(
            "SPEED:${prefs.getInt(KEY_SPEED_LEVEL, 5).coerceIn(1, 10)}"
        )
        if (speedResponse.startsWith("ERR")) error(speedResponse)
        val modeResponse = board.sendCommandAwait(
            "UMOD:${prefs.getInt(KEY_UNICODE_MODE, TypingEngine.MODE_ALTX)}"
        )
        if (modeResponse.startsWith("ERR")) error(modeResponse)
        val codePoints = text.codePoints().toArray()
        for ((index, codePoint) in codePoints.withIndex()) {
            control.checkpoint()
            if (control.cancelled) throw CancellationException("Output stopped")
            val character = String(Character.toChars(codePoint))
            val response = board.sendTextAwait(character)
            if (response.startsWith("ERR")) error(response)
            control.onProgress?.invoke(index + 1, codePoints.size)
        }
    }

    private fun pauseOutput() {
        val control = outputControl ?: return
        if (outputPaused) return
        control.paused = true
        outputPaused = true
        sendState.text = "已暂停，点击“发送”从当前位置继续"
        sendState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
        refreshOutputControls()
    }

    private fun resumeOutput() {
        val control = outputControl ?: return
        if (!outputPaused) return
        control.paused = false
        outputPaused = false
        updateRunningState()
        refreshOutputControls()
    }

    private fun stopOutput(statusMessage: String = "已停止，再次发送将从头开始") {
        if (outputControl == null) return
        outputGeneration++
        outputControl?.cancel()
        sendJob?.cancel()
        sendJob = null
        if (activeOutputIsBoard) BoardLink.stopOutput() else KeyboardRuntime.releaseAll()
        outputControl = null
        outputPaused = false
        sendState.text = statusMessage
        sendState.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        refreshOutputControls()
    }

    private fun clearInput() {
        if (outputControl != null) stopOutput("输出已停止，输入内容已清空")
        textInput.setText("")
        textInput.requestFocus()
        if (outputControl == null) {
            sendState.text = "输入内容已清空"
            sendState.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun finishOutput(generation: Int, success: Boolean, message: String) {
        if (generation != outputGeneration) return
        outputControl = null
        sendJob = null
        outputPaused = false
        sendState.text = when {
            success -> "发送完成：${sentCharacters.coerceAtLeast(totalCharacters)} / $totalCharacters"
            else -> message
        }
        sendState.setTextColor(
            ContextCompat.getColor(
                this,
                if (success) R.color.connected else R.color.clinical_danger
            )
        )
        refreshOutputControls()
    }

    private fun updateRunningState() {
        sendState.text = "正在发送到 $activeDeviceLabel：$sentCharacters / $totalCharacters"
        sendState.setTextColor(ContextCompat.getColor(this, R.color.accent))
    }

    private fun refreshOutputControls() {
        val active = outputControl != null && !outputControl!!.cancelled
        sendButton.isEnabled = !active || outputPaused
        pauseButton.isEnabled = active && !outputPaused
        stopButton.isEnabled = active
        clearButton.isEnabled = true
        textInput.isEnabled = !active
        sendButton.text = "发送"
        pauseButton.text = if (outputPaused) "已暂停" else "暂停"
    }

    private fun refreshTerminalStatus() {
        if (!::terminalName.isInitialized) return
        val remembered = LiteConnectionMemory.load(this)
        val computerConnected = KeyboardRuntime.snapshot().connected
        val boardConnected = BoardLink.isConnected()
        val connected = computerConnected || boardConnected
        if (boardConnected) {
            TerminalRegistry.select(this, TerminalRegistry.byId(TerminalRegistry.ESP32_S3))
        } else if (computerConnected) {
            TerminalRegistry.select(this, TerminalRegistry.byId(TerminalRegistry.PHONE_HID))
        }
        terminalName.text = when {
            computerConnected -> KeyboardRuntime.snapshot().deviceName ?: "已连接电脑"
            boardConnected -> BoardLink.connectedName() ?: "已连接设备"
            remembered != null -> "上次设备：${remembered.name.ifBlank { "未命名设备" }}"
            else -> "尚未连接"
        }
        terminalDot.setTextColor(
            ContextCompat.getColor(this, if (connected) R.color.connected else R.color.disconnected)
        )
        terminalDetail.text = when {
            computerConnected -> "● 当前已连接 · MAC：${KeyboardRuntime.lastComputerAddress().orEmpty()}"
            boardConnected -> "● 当前已连接 · MAC：${BoardLink.connectedAddress().orEmpty()}"
            remembered != null -> "MAC：${remembered.address} · 点击连接可主动重连"
            else -> "点击“选择终端并连接”扫描并记住设备"
        }
        if (outputControl == null) {
            sendState.text = if (connected) "设备已连接，可以发送" else "连接设备后即可发送"
            sendState.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (connected) R.color.connected else R.color.text_hint
                )
            )
        }
    }

    private fun connectedDeviceName(): String? = when {
        KeyboardRuntime.snapshot().connected -> KeyboardRuntime.snapshot().deviceName
        BoardLink.isConnected() -> BoardLink.connectedName()
        else -> LiteConnectionMemory.load(this)?.name
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
