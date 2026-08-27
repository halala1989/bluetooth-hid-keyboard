package com.hidble.phonekeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "hidble_prefs"
        private const val KEY_SPEED_LEVEL = "speed_level"
        private const val KEY_PHRASES = "phrases"
        private const val DEFAULT_SPEED_LEVEL = 5
        private const val SPEED_MIN = 1
        private const val SPEED_MAX = 10
    }

    private lateinit var statusText: TextView
    private lateinit var statusDot: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var keyboardSwitch: SwitchCompat
    private lateinit var textInput: EditText
    private lateinit var sendButton: Button
    private lateinit var speedInput: EditText
    private lateinit var applySpeedButton: Button
    private lateinit var phraseButton: Button
    private lateinit var commandLog: TextView
    private lateinit var deviceList: ListView

    private lateinit var llmInput: EditText
    private lateinit var llmOutput: EditText
    private lateinit var llmSendButton: Button
    private lateinit var llmSendToKeyboardButton: Button
    private lateinit var llmClearButton: Button
    private lateinit var llmSettingsButton: Button
    private lateinit var llmSettingsTopButton: Button

    private lateinit var hidManager: HidDeviceManager
    private lateinit var hidProtocol: HidProtocol

    private val bondedDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var deviceListAdapter: ArrayAdapter<String>

    private var registered = false
    private var connected = false
    private var savedSpeedLevel = DEFAULT_SPEED_LEVEL
    private val phrases = mutableListOf<String>()
    private lateinit var prefs: android.content.SharedPreferences

    // 大模型配置与对话
    private var llmProviderId = LlmProviders.list.first().id
    private var llmApiKey = ""
    private var llmModel = ""
    private val llmHistory = mutableListOf<Pair<String, String>>()
    private var llmBusy = false

    // 开关状态由回调刷新时抑制监听器，避免死循环
    private var suppressSwitchEvent = false

    // 请求“对附近设备可见”（ACTION_REQUEST_DISCOVERABLE），否则电脑搜不到模拟的键盘
    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            appendLog("手机已对附近设备可见：请到电脑上 设置 → 蓝牙 → 添加设备，搜索“${HidDeviceManager.KEYBOARD_NAME}”并配对")
        } else {
            appendLog("未开启可见性：电脑可能搜不到手机，请在系统蓝牙设置中开启“对附近设备可见”后再试")
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && hidManager.isBluetoothOn()) {
            appendLog("蓝牙已开启，正在启动蓝牙键盘...")
            registerKeyboard()
        } else {
            suppressSwitchEvent = true
            keyboardSwitch.isChecked = false
            suppressSwitchEvent = false
            Toast.makeText(this, "未开启蓝牙，键盘未启动", Toast.LENGTH_SHORT).show()
            updateConnectionState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initHid()
        setupListeners()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 从模型设置页返回后重新读取配置（Token/模型/提供方）
        loadLlmPrefs()
        // 蓝牙可能在此前未开启，重新获取 HID profile
        hidManager.init()
        if (registered) {
            loadBondedDevices()
        }
        // 蓝牙被系统关闭时，把开关复位
        if (!hidManager.isBluetoothOn() && keyboardSwitch.isChecked) {
            suppressSwitchEvent = true
            keyboardSwitch.isChecked = false
            suppressSwitchEvent = false
            connected = false
            registered = false
            updateConnectionState()
        }
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        deviceNameText = findViewById(R.id.deviceNameText)
        keyboardSwitch = findViewById(R.id.keyboardSwitch)
        textInput = findViewById(R.id.textInput)
        sendButton = findViewById(R.id.sendButton)
        speedInput = findViewById(R.id.speedInput)
        applySpeedButton = findViewById(R.id.applySpeedButton)
        phraseButton = findViewById(R.id.phraseButton)
        commandLog = findViewById(R.id.commandLog)

        // 大模型对话
        llmInput = findViewById(R.id.llmInput)
        llmOutput = findViewById(R.id.llmOutput)
        llmSendButton = findViewById(R.id.llmSendButton)
        llmSendToKeyboardButton = findViewById(R.id.llmSendToKeyboardButton)
        llmClearButton = findViewById(R.id.llmClearButton)
        llmSettingsButton = findViewById(R.id.llmSettingsButton)
        llmSettingsTopButton = findViewById(R.id.llmSettingsTopButton)

        // 已配对设备列表
        deviceList = findViewById(R.id.deviceList)
        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        deviceList.adapter = deviceListAdapter

        // 输入速度 / 常用语（SharedPreferences）
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        savedSpeedLevel = prefs.getInt(KEY_SPEED_LEVEL, DEFAULT_SPEED_LEVEL)
        speedInput.setText(savedSpeedLevel.toString())
        loadPhrases()
        loadLlmPrefs()

        // 对话输出框可编辑，改动自动保存（重启 App 后仍在）
        llmOutput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString(LlmPrefs.KEY_OUTPUT, s?.toString() ?: "").apply()
            }
        })

        updateConnectionState()
    }

    private fun initHid() {
        hidManager = HidDeviceManager(this)
        hidManager.init()
        hidProtocol = HidProtocol(
            TypingEngine(
                send = { data -> hidManager.sendReport(data) },
                onSendInterrupted = { sent ->
                    appendLog("发送中断：蓝牙连接可能已断开（已发送 $sent 字），请检查连接后重试")
                }
            )
        )

        hidManager.onAppStatusChanged = { reg ->
            runOnUiThread {
                registered = reg
                suppressSwitchEvent = true
                keyboardSwitch.isChecked = reg
                suppressSwitchEvent = false
                if (reg) {
                    appendLog("蓝牙键盘已启动：请确认“对附近设备可见”，然后到电脑上搜索并配对")
                    loadBondedDevices()
                    requestDiscoverable()
                } else {
                    appendLog("蓝牙键盘已停止")
                }
                updateConnectionState()
            }
        }

        hidManager.onConnectionStateChanged = { device, state ->
            runOnUiThread {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connected = true
                        hidProtocol.setSpeed(savedSpeedLevel)
                        appendLog("已连接：${device?.name ?: "电脑"}（输入速度 $savedSpeedLevel）")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connected = false
                        appendLog("连接已断开")
                    }
                }
                updateConnectionState()
            }
        }

        hidManager.onError = { error ->
            runOnUiThread {
                appendLog("错误: $error")
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                // 注册失败时把开关复位
                if (!hidManager.isRegistered()) {
                    suppressSwitchEvent = true
                    keyboardSwitch.isChecked = false
                    suppressSwitchEvent = false
                    updateConnectionState()
                }
            }
        }

        hidManager.onLedState = { led ->
            hidProtocol.onLedReport(led)
        }
    }

    private fun setupListeners() {
        // 底部开关：打开 = 自动开蓝牙 + 模拟蓝牙键盘；关闭 = 停止
        keyboardSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitchEvent) return@setOnCheckedChangeListener
            if (checked) {
                if (!hidManager.isBluetoothOn()) {
                    appendLog("正在请求开启蓝牙...")
                    try {
                        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } catch (e: Exception) {
                        suppressSwitchEvent = true
                        keyboardSwitch.isChecked = false
                        suppressSwitchEvent = false
                        Toast.makeText(this, "无法自动开启蓝牙，请到系统设置中打开", Toast.LENGTH_LONG).show()
                    }
                } else {
                    registerKeyboard()
                }
            } else {
                hidManager.unregister()
                hidManager.disconnect()
                connected = false
                registered = false
                appendLog("蓝牙键盘已关闭")
                updateConnectionState()
            }
        }

        sendButton.setOnClickListener { sendText() }

        // 输入框软键盘“发送”键
        textInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendText()
                true
            } else {
                false
            }
        }

        // 输入速度
        applySpeedButton.setOnClickListener { applySpeedFromInput() }

        // 常用语
        phraseButton.setOnClickListener { showPhraseDialog() }

        // 中文输入模式
        findViewById<Button>(R.id.btnModeAltX).setOnClickListener {
            hidProtocol.setUnicodeMode(TypingEngine.MODE_ALTX)
            appendLog("中文输入模式: Alt+X（默认，记事本/Word 等，无需注册表）")
        }
        findViewById<Button>(R.id.btnModeHex).setOnClickListener {
            hidProtocol.setUnicodeMode(TypingEngine.MODE_HEX)
            appendLog("中文输入模式: 十六进制（Alt+Numpad+，需 EnableHexNumpad+NumLock，记事本无效）")
        }
        findViewById<Button>(R.id.btnModeDecimal).setOnClickListener {
            hidProtocol.setUnicodeMode(TypingEngine.MODE_DECIMAL)
            appendLog("中文输入模式: 十进制（Alt+0+码点，记事本/Word 等）")
        }
        findViewById<Button>(R.id.btnModeGbk).setOnClickListener {
            hidProtocol.setUnicodeMode(TypingEngine.MODE_GBK)
            appendLog("中文输入模式: GBK 机内码（仅中文版 Windows）")
        }

        // 已配对设备点击连接（首次在电脑上配对后，若未自动连接可点这里）
        deviceList.setOnItemClickListener { _, _, position, _ ->
            if (position < bondedDevices.size) {
                val device = bondedDevices[position]
                val name = deviceNames[position]

                AlertDialog.Builder(this)
                    .setTitle("连接设备")
                    .setMessage("确定要连接到 $name 吗？")
                    .setPositiveButton("连接") { _, _ ->
                        appendLog("正在连接 $name...")
                        hidManager.connect(device)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        // 功能按钮
        findViewById<Button>(R.id.btnEnter).setOnClickListener {
            lifecycleScope.launch { hidProtocol.enter(); appendLog("Enter") }
        }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            lifecycleScope.launch { hidProtocol.backspace(); appendLog("Backspace") }
        }
        findViewById<Button>(R.id.btnTab).setOnClickListener {
            lifecycleScope.launch { hidProtocol.tab(); appendLog("Tab") }
        }
        findViewById<Button>(R.id.btnEscape).setOnClickListener {
            lifecycleScope.launch { hidProtocol.escape(); appendLog("Escape") }
        }
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            lifecycleScope.launch { hidProtocol.delete(); appendLog("Delete") }
        }

        // 光标控制
        findViewById<Button>(R.id.btnUp).setOnClickListener {
            lifecycleScope.launch { hidProtocol.arrowUp(); appendLog("↑") }
        }
        findViewById<Button>(R.id.btnDown).setOnClickListener {
            lifecycleScope.launch { hidProtocol.arrowDown(); appendLog("↓") }
        }
        findViewById<Button>(R.id.btnLeft).setOnClickListener {
            lifecycleScope.launch { hidProtocol.arrowLeft(); appendLog("←") }
        }
        findViewById<Button>(R.id.btnRight).setOnClickListener {
            lifecycleScope.launch { hidProtocol.arrowRight(); appendLog("→") }
        }
        findViewById<Button>(R.id.btnHome).setOnClickListener {
            lifecycleScope.launch { hidProtocol.home(); appendLog("Home") }
        }
        findViewById<Button>(R.id.btnEnd).setOnClickListener {
            lifecycleScope.launch { hidProtocol.end(); appendLog("End") }
        }

        // 组合键
        findViewById<Button>(R.id.btnCtrlC).setOnClickListener {
            lifecycleScope.launch { hidProtocol.copy(); appendLog("Ctrl+C") }
        }
        findViewById<Button>(R.id.btnCtrlV).setOnClickListener {
            lifecycleScope.launch { hidProtocol.paste(); appendLog("Ctrl+V") }
        }
        findViewById<Button>(R.id.btnCtrlX).setOnClickListener {
            lifecycleScope.launch { hidProtocol.cut(); appendLog("Ctrl+X") }
        }
        findViewById<Button>(R.id.btnCtrlA).setOnClickListener {
            lifecycleScope.launch { hidProtocol.selectAll(); appendLog("Ctrl+A") }
        }
        findViewById<Button>(R.id.btnCtrlZ).setOnClickListener {
            lifecycleScope.launch { hidProtocol.undo(); appendLog("Ctrl+Z") }
        }
        findViewById<Button>(R.id.btnCtrlS).setOnClickListener {
            lifecycleScope.launch { hidProtocol.save(); appendLog("Ctrl+S") }
        }

        // ===== 大模型对话 =====
        llmSettingsButton.setOnClickListener { openLlmSettings() }
        llmSettingsTopButton.setOnClickListener { openLlmSettings() }

        llmSendButton.setOnClickListener { sendToLlm() }

        // 输入框软键盘“发送”键
        llmInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendToLlm()
                true
            } else {
                false
            }
        }

        llmSendToKeyboardButton.setOnClickListener { sendOutputToKeyboard() }
        llmClearButton.setOnClickListener { clearLlmConversation() }
    }

    /** 请求系统开启“对附近设备可见”，电脑才能搜索到本机 */
    private fun requestDiscoverable() {
        try {
            discoverableLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
            )
        } catch (e: Exception) {
            appendLog("无法弹出可见性设置，请到系统蓝牙设置中开启“对附近设备可见”")
        }
    }

    private fun registerKeyboard() {
        if (!hidManager.isBluetoothOn()) {
            suppressSwitchEvent = true
            keyboardSwitch.isChecked = false
            suppressSwitchEvent = false
            Toast.makeText(this, "请先开启手机蓝牙", Toast.LENGTH_SHORT).show()
            return
        }
        if (hidManager.isRegistered()) return
        appendLog("正在启动蓝牙键盘...")
        hidManager.register()
    }

    private fun sendText() {
        val text = textInput.text.toString()
        if (text.isNotEmpty()) {
            if (!connected) {
                Toast.makeText(this, "尚未连接到电脑", Toast.LENGTH_SHORT).show()
                return
            }
            lifecycleScope.launch {
                if (text.length > TypingEngine.CHUNK_SIZE) {
                    appendLog("文本较长（${text.length} 字），已自动分段发送")
                }
                hidProtocol.typeText(text)
                appendLog("发送文本: $text")
                textInput.text.clear()
            }
        }
    }

    // ===== 输入速度 =====

    private fun applySpeedFromInput() {
        val text = speedInput.text.toString().trim()
        val level = text.toIntOrNull()
        if (level == null || level < SPEED_MIN || level > SPEED_MAX) {
            Toast.makeText(this, "请输入 $SPEED_MIN-$SPEED_MAX 之间的数字", Toast.LENGTH_SHORT).show()
            speedInput.setText(savedSpeedLevel.toString())
            return
        }
        savedSpeedLevel = level
        prefs.edit().putInt(KEY_SPEED_LEVEL, level).apply()
        hidProtocol.setSpeed(level)
        appendLog("输入速度已设为 $level（1-10）")
    }

    // ===== 大模型对话 =====

    private fun loadLlmPrefs() {
        llmProviderId = prefs.getString(LlmPrefs.KEY_PROVIDER, null) ?: LlmProviders.list.first().id
        llmApiKey = prefs.getString(LlmPrefs.KEY_API_KEY, "") ?: ""
        llmModel = prefs.getString(LlmPrefs.KEY_MODEL, "") ?: ""
        llmOutput.setText(prefs.getString(LlmPrefs.KEY_OUTPUT, "") ?: "")
    }

    private fun saveLlmOutput() {
        prefs.edit().putString(LlmPrefs.KEY_OUTPUT, llmOutput.text.toString()).apply()
    }

    private fun appendOutput(text: String) {
        val current = llmOutput.text.toString()
        llmOutput.setText(if (current.isBlank()) text else "$current\n$text")
        llmOutput.setSelection(llmOutput.text.length)
    }

    /** 进入模型设置二级页面（选择提供方自动填预设模型，只填 Token 即可） */
    private fun openLlmSettings() {
        startActivity(Intent(this, LlmSettingsActivity::class.java))
    }

    private fun sendToLlm() {
        val text = llmInput.text.toString().trim()
        if (text.isEmpty()) return
        val provider = LlmProviders.byId(llmProviderId)
        val model = llmModel.ifBlank { provider.defaultModel }
        if (provider.needsKey && llmApiKey.isBlank()) {
            Toast.makeText(this, "请先在“模型设置”里填写 API Token", Toast.LENGTH_SHORT).show()
            return
        }
        if (model.isBlank()) {
            Toast.makeText(this, "请先在“模型设置”里填写模型名", Toast.LENGTH_SHORT).show()
            return
        }
        if (llmBusy) {
            Toast.makeText(this, "正在等待模型回复，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        llmBusy = true
        llmSendButton.isEnabled = false
        appendOutput("我：$text")
        llmHistory.add("user" to text)
        llmInput.text.clear()
        appendLog("已发送给模型（${provider.displayName} / $model），等待回复...")

        lifecycleScope.launch {
            val reply = try {
                LlmClient.chat(
                    provider,
                    llmApiKey,
                    model,
                    listOf("system" to "你是简洁的助手。") + llmHistory
                )
            } catch (e: Exception) {
                appendLog("模型调用失败：${e.message}")
                null
            }
            if (reply == null) {
                Toast.makeText(this@MainActivity, "模型调用失败，详情见日志", Toast.LENGTH_SHORT).show()
            } else {
                llmHistory.add("assistant" to reply)
                appendOutput("AI：$reply")
                appendLog("模型已回复")
            }
            llmBusy = false
            llmSendButton.isEnabled = true
        }
    }

    /** 把对话输出框（可编辑）的内容整体发送到蓝牙键盘 */
    private fun sendOutputToKeyboard() {
        val text = llmOutput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "对话输出为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (!connected) {
            Toast.makeText(this, "尚未连接到电脑", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            if (text.length > TypingEngine.CHUNK_SIZE) {
                appendLog("对话内容较长（${text.length} 字），已自动分段发送")
            }
            hidProtocol.typeText(text)
            appendLog("已把对话内容发送到电脑")
        }
    }

    private fun clearLlmConversation() {
        llmOutput.setText("")
        llmHistory.clear()
        saveLlmOutput()
        appendLog("对话已清空")
    }

    // ===== 常用语句 =====

    private fun loadPhrases() {
        phrases.clear()
        val raw = prefs.getString(KEY_PHRASES, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                phrases.add(arr.getString(i))
            }
        } catch (e: Exception) {
            // 数据损坏时忽略，重新开始
        }
    }

    private fun savePhrases() {
        val arr = JSONArray()
        phrases.forEach { arr.put(it) }
        prefs.edit().putString(KEY_PHRASES, arr.toString()).apply()
    }

    private fun showPhraseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_phrases, null)
        val listView = dialogView.findViewById<ListView>(R.id.phraseList)
        val addButton = dialogView.findViewById<Button>(R.id.phraseAddButton)
        val closeButton = dialogView.findViewById<Button>(R.id.phraseCloseButton)

        val dialog = AlertDialog.Builder(this)
            .setTitle("常用语句")
            .setView(dialogView)
            .create()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, phrases)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in phrases.indices) {
                insertPhrase(phrases[position])
                dialog.dismiss()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (position in phrases.indices) {
                showPhraseActions(phrases[position], position) {
                    adapter.notifyDataSetChanged()
                }
            }
            true
        }

        addButton.setOnClickListener {
            showPhraseEditDialog(null) { newPhrase ->
                phrases.add(newPhrase)
                savePhrases()
                adapter.notifyDataSetChanged()
            }
        }

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPhraseActions(phrase: String, index: Int, onChanged: () -> Unit) {
        val options = arrayOf("编辑", "删除")
        AlertDialog.Builder(this)
            .setTitle("常用语句操作")
            .setMessage(phrase)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPhraseEditDialog(phrase) { newPhrase ->
                        phrases[index] = newPhrase
                        savePhrases()
                        onChanged()
                    }
                    1 -> {
                        phrases.removeAt(index)
                        savePhrases()
                        onChanged()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPhraseEditDialog(initial: String?, onSaved: (String) -> Unit) {
        val input = EditText(this)
        input.hint = "输入常用语句"
        input.setText(initial ?: "")
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint))
        input.setBackgroundResource(R.drawable.bg_input)
        input.setPadding(dp(14), dp(10), dp(14), dp(10))

        val container = FrameLayout(this)
        container.setPadding(dp(20), dp(8), dp(20), 0)
        container.addView(input, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        AlertDialog.Builder(this)
            .setTitle(if (initial == null) "添加常用语句" else "编辑常用语句")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    onSaved(text)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun insertPhrase(phrase: String) {
        val editable = textInput.text
        val start = textInput.selectionStart.coerceAtLeast(0)
        val end = textInput.selectionEnd.coerceAtLeast(start)
        val prefix = if (start > 0 && editable[start - 1] != ' ' && editable[start - 1] != '\n') " " else ""
        val suffix = if (end < editable.length && editable[end] != ' ' && editable[end] != '\n') " " else ""
        editable.replace(start, end, prefix + phrase + suffix)
        textInput.setSelection(start + prefix.length + phrase.length)
        textInput.requestFocus()
        appendLog("插入常用语: $phrase")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun loadBondedDevices() {
        bondedDevices.clear()
        deviceNames.clear()
        hidManager.bondedDevices()?.forEach { d ->
            bondedDevices.add(d)
            val name = d.name ?: "未知设备"
            deviceNames.add("$name (${d.address})")
        }
        deviceListAdapter.notifyDataSetChanged()
        if (deviceNames.isEmpty()) {
            appendLog("暂无已配对设备，请先在电脑蓝牙中添加“${HidDeviceManager.KEYBOARD_NAME}”并配对")
        }
    }

    private fun updateConnectionState() {
        if (connected) {
            statusText.text = "已连接"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.connected))
            statusDot.setTextColor(ContextCompat.getColor(this, R.color.connected))
            deviceNameText.text = "已连接到电脑：${hidManager.connectedDeviceName() ?: "电脑"}，可以直接输入文字发送；关闭开关可断开。"
        } else if (registered) {
            statusText.text = "键盘已启动"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.accent))
            statusDot.setTextColor(ContextCompat.getColor(this, R.color.accent))
            deviceNameText.text = "手机已模拟为蓝牙键盘：到电脑上 设置 → 蓝牙 → 添加设备，搜索“${HidDeviceManager.KEYBOARD_NAME}”并配对。"
        } else {
            statusText.text = "未启动"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            statusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            deviceNameText.text = "打开下方开关后，手机会自动开启蓝牙并模拟成蓝牙键盘，到电脑上搜索即可。"
        }
    }

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message\n"

        commandLog.append(logEntry)

        val lines = commandLog.text.lines()
        if (lines.size > 50) {
            commandLog.text = lines.takeLast(50).joinToString("\n")
        }

        val scrollView = commandLog.parent as? ScrollView
        scrollView?.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val deniedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "需要蓝牙权限才能使用此应用", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hidManager.cleanup()
    }
}
