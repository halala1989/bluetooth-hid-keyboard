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
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
        private const val KEY_UNICODE_MODE = "unicode_mode"
        private const val DEFAULT_SPEED_LEVEL = 5
        private const val SPEED_MIN = 1
        private const val SPEED_MAX = 10

        /** 供二级页面（连接管理/更多按键）访问本 Activity 的 HID 引擎 */
        @Volatile
        var instance: MainActivity? = null
    }

    private lateinit var headerStatusDot: TextView
    private lateinit var headerStatusText: TextView
    private lateinit var textInput: EditText
    private lateinit var sendButton: Button
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedValueText: TextView
    private lateinit var phraseButton: Button

    private lateinit var llmInput: EditText
    private lateinit var llmOutput: EditText
    private lateinit var llmSendButton: Button
    private lateinit var llmSendToKeyboardButton: Button
    private lateinit var llmClearButton: Button
    private lateinit var llmIncludeMeCheck: CheckBox
    private lateinit var llmThinkingRow: android.view.View
    private lateinit var llmThinkingText: TextView
    private lateinit var llmSettingsButton: Button
    private lateinit var llmSettingsTopButton: Button
    private lateinit var unicodeModeSpinner: Spinner

    private lateinit var hidManager: HidDeviceManager
    private lateinit var hidProtocol: HidProtocol

    /** 连接管理二级页（未打开时为 null）；由 ConnectionActivity 在 onResume/onPause 时挂接 */
    var connectionActivity: ConnectionActivity? = null

    private var registered = false
    private var connected = false
    private var savedSpeedLevel = DEFAULT_SPEED_LEVEL
    private var savedUnicodeMode = TypingEngine.MODE_ALTX
    private val phrases = mutableListOf<String>()
    private lateinit var prefs: android.content.SharedPreferences

    // 大模型配置与对话
    private var llmProviderId = LlmProviders.list.first().id
    private var llmApiKey = ""
    private var llmModel = ""
    private val llmHistory = mutableListOf<Pair<String, String>>()
    private var llmBusy = false

    // 中文输入模式下拉（含绿色 √ 选中态）
    private lateinit var unicodeModeAdapter: UnicodeModeAdapter
    private val unicodeModeItems = listOf("Alt+X（默认）", "十六进制", "十进制", "GBK")
    private val unicodeModeValues = listOf(
        TypingEngine.MODE_ALTX,
        TypingEngine.MODE_HEX,
        TypingEngine.MODE_DECIMAL,
        TypingEngine.MODE_GBK
    )
    private var suppressModeEvent = false

    // AI 思考动画（类 ChatGPT 的“正在思考…”点号动画）
    private val thinkingHandler = Handler(Looper.getMainLooper())
    private var thinkingRunnable: Runnable? = null

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
            Toast.makeText(this, "未开启蓝牙，键盘未启动", Toast.LENGTH_SHORT).show()
            refreshAllState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
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
            refreshConnectionPage()
        }
        // 蓝牙被系统关闭时，复位键盘状态
        if (!hidManager.isBluetoothOn()) {
            if (registered || connected) {
                registered = false
                connected = false
                appendLog("蓝牙已被系统关闭，键盘已停止")
            }
        }
        refreshAllState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        hidManager.cleanup()
    }

    private fun initViews() {
        headerStatusDot = findViewById(R.id.headerStatusDot)
        headerStatusText = findViewById(R.id.headerStatusText)
        textInput = findViewById(R.id.textInput)
        sendButton = findViewById(R.id.sendButton)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        speedValueText = findViewById(R.id.speedValueText)
        phraseButton = findViewById(R.id.phraseButton)

        // 大模型对话
        llmInput = findViewById(R.id.llmInput)
        llmOutput = findViewById(R.id.llmOutput)
        llmSendButton = findViewById(R.id.llmSendButton)
        llmSendToKeyboardButton = findViewById(R.id.llmSendToKeyboardButton)
        llmClearButton = findViewById(R.id.llmClearButton)
        llmIncludeMeCheck = findViewById(R.id.llmIncludeMeCheck)
        llmThinkingRow = findViewById(R.id.llmThinkingRow)
        llmThinkingText = findViewById(R.id.llmThinkingText)
        llmSettingsButton = findViewById(R.id.llmSettingsButton)
        llmSettingsTopButton = findViewById(R.id.llmSettingsTopButton)
        unicodeModeSpinner = findViewById(R.id.unicodeModeSpinner)

        // SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 输入速度：滑块位置直接决定速度（1-10，默认 5）
        savedSpeedLevel = prefs.getInt(KEY_SPEED_LEVEL, DEFAULT_SPEED_LEVEL)
        speedSeekBar.progress = savedSpeedLevel
        speedValueText.text = savedSpeedLevel.toString()

        // 中文输入模式下拉
        savedUnicodeMode = prefs.getInt(KEY_UNICODE_MODE, TypingEngine.MODE_ALTX)
        // 设置 adapter 时可能同步触发 onItemSelected（此时 hidProtocol 尚未初始化），用抑制标志包住
        suppressModeEvent = true
        unicodeModeAdapter = UnicodeModeAdapter(this, unicodeModeItems)
        unicodeModeSpinner.adapter = unicodeModeAdapter
        val initIndex = unicodeModeValues.indexOf(savedUnicodeMode).coerceAtLeast(0)
        unicodeModeSpinner.setSelection(initIndex, false)
        unicodeModeAdapter.setSelected(initIndex)
        suppressModeEvent = false

        loadPhrases()
        loadLlmPrefs()

        // 对话输出框可编辑：改动自动保存 + 重新着色（“我”绿色 / AI 白色）
        llmOutput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString(LlmPrefs.KEY_OUTPUT, s?.toString() ?: "").apply()
                applyLlmColors()
            }
        })
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
        hidProtocol.setSpeed(savedSpeedLevel)
        hidProtocol.setUnicodeMode(savedUnicodeMode)

        hidManager.onAppStatusChanged = { reg ->
            runOnUiThread {
                registered = reg
                if (reg) {
                    appendLog("蓝牙键盘已启动：请确认“对附近设备可见”，然后到电脑上搜索并配对")
                    requestDiscoverable()
                } else {
                    appendLog("蓝牙键盘已停止")
                }
                refreshAllState()
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
                refreshAllState()
            }
        }

        hidManager.onError = { error ->
            runOnUiThread {
                appendLog("错误: $error")
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                // 注册失败时复位
                if (!hidManager.isRegistered()) {
                    registered = false
                    refreshAllState()
                }
            }
        }

        hidManager.onLedState = { led ->
            hidProtocol.onLedReport(led)
        }
    }

    private fun setupListeners() {
        sendButton.setOnClickListener { sendText() }

        // 输入框软键盘“发送”键
        textInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendText()
                true
            } else {
                false
            }
        }

        // 输入速度滑块：拖动即生效（无需“应用”按钮）
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val level = progress.coerceIn(SPEED_MIN, SPEED_MAX)
                speedValueText.text = level.toString()
                savedSpeedLevel = level
                prefs.edit().putInt(KEY_SPEED_LEVEL, level).apply()
                hidProtocol.setSpeed(level)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                appendLog("输入速度已设为 $savedSpeedLevel（1-10）")
            }
        })

        // 常用语
        phraseButton.setOnClickListener { showPhraseDialog() }

        // 中文输入模式下拉（选中项左侧绿色 √）
        unicodeModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                unicodeModeAdapter.setSelected(position)
                if (suppressModeEvent) return
                val mode = unicodeModeValues.getOrElse(position) { TypingEngine.MODE_ALTX }
                savedUnicodeMode = mode
                prefs.edit().putInt(KEY_UNICODE_MODE, mode).apply()
                hidProtocol.setUnicodeMode(mode)
                val desc = when (mode) {
                    TypingEngine.MODE_HEX -> "十六进制（Alt+Numpad+，需 EnableHexNumpad+NumLock，记事本无效）"
                    TypingEngine.MODE_DECIMAL -> "十进制（Alt+0+码点，记事本/Word 等）"
                    TypingEngine.MODE_GBK -> "GBK 机内码（仅中文版 Windows）"
                    else -> "Alt+X（默认，记事本/Word 等，无需注册表）"
                }
                appendLog("中文输入模式: $desc")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 二级页面入口
        findViewById<Button>(R.id.connectionButton).setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }
        findViewById<Button>(R.id.keysButton).setOnClickListener {
            startActivity(Intent(this, KeysActivity::class.java))
        }
        findViewById<Button>(R.id.logButton).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        // ===== 大模型对话 =====
        llmSettingsButton.setOnClickListener { openLlmSettings() }
        llmSettingsTopButton.setOnClickListener { openLlmSettings() }

        llmSendButton.setOnClickListener { sendToLlm() }

        llmSendToKeyboardButton.setOnClickListener { sendOutputToKeyboard() }
        llmClearButton.setOnClickListener { clearLlmConversation() }
    }

    // ===== 供二级页面调用的接口 =====

    fun isRegistered(): Boolean = registered
    fun isConnected(): Boolean = connected
    fun connectedDeviceName(): String? = hidManager.connectedDeviceName()

    /** 连接管理页：切换模拟蓝牙键盘开关 */
    fun setKeyboardEnabled(enabled: Boolean) {
        if (enabled) {
            if (!hidManager.isBluetoothOn()) {
                appendLog("正在请求开启蓝牙...")
                try {
                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (e: Exception) {
                    Toast.makeText(this, "无法自动开启蓝牙，请到系统设置中打开", Toast.LENGTH_LONG).show()
                    refreshAllState()
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
            refreshAllState()
        }
    }

    /** 连接管理页：点选已配对设备连接 */
    fun connectToDevice(device: BluetoothDevice) {
        hidManager.connect(device)
    }

    /** 已配对设备快照：(名称+地址, device) 列表 */
    fun getBondedDevices(): List<Pair<String, BluetoothDevice>> {
        val result = mutableListOf<Pair<String, BluetoothDevice>>()
        hidManager.bondedDevices()?.forEach { d ->
            val name = d.name ?: "未知设备"
            result.add("$name (${d.address})" to d)
        }
        return result
    }

    /** 更多按键页：在后台执行一个 HID 动作（发送期间保持屏幕常亮） */
    fun performHid(action: suspend (HidProtocol) -> Unit) {
        lifecycleScope.launch {
            setKeepScreenOn(true)
            try {
                action(hidProtocol)
            } finally {
                setKeepScreenOn(false)
            }
        }
    }

    // ===== 状态刷新 =====

    private fun refreshAllState() {
        refreshHeaderStatus()
        refreshConnectionPage()
    }

    private fun refreshHeaderStatus() {
        when {
            connected -> {
                headerStatusText.text = "已连接"
                headerStatusText.setTextColor(ContextCompat.getColor(this, R.color.connected))
                headerStatusDot.setTextColor(ContextCompat.getColor(this, R.color.connected))
            }
            registered -> {
                headerStatusText.text = "键盘已启动"
                headerStatusText.setTextColor(ContextCompat.getColor(this, R.color.accent))
                headerStatusDot.setTextColor(ContextCompat.getColor(this, R.color.accent))
            }
            else -> {
                headerStatusText.text = "未启动"
                headerStatusText.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                headerStatusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            }
        }
    }

    private fun refreshConnectionPage() {
        connectionActivity?.refreshAll()
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
            Toast.makeText(this, "请先开启手机蓝牙", Toast.LENGTH_SHORT).show()
            refreshAllState()
            return
        }
        if (hidManager.isRegistered()) return
        appendLog("正在启动蓝牙键盘...")
        hidManager.register()
    }

    // ===== 发送 HID（发送期间阻止锁屏） =====

    private fun setKeepScreenOn(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun sendText() {
        val text = textInput.text.toString()
        if (text.isNotEmpty()) {
            if (!connected) {
                Toast.makeText(this, "尚未连接到电脑", Toast.LENGTH_SHORT).show()
                return
            }
            setKeepScreenOn(true)
            lifecycleScope.launch {
                try {
                    if (text.length > TypingEngine.CHUNK_SIZE) {
                        appendLog("文本较长（${text.length} 字），已自动分段发送")
                    }
                    hidProtocol.typeText(text)
                    appendLog("发送文本: $text")
                    textInput.text.clear()
                } finally {
                    setKeepScreenOn(false)
                }
            }
        }
    }

    // ===== 大模型对话 =====

    private fun loadLlmPrefs() {
        llmProviderId = prefs.getString(LlmPrefs.KEY_PROVIDER, null) ?: LlmProviders.list.first().id
        llmApiKey = prefs.getString(LlmPrefs.KEY_API_KEY, "") ?: ""
        llmModel = prefs.getString(LlmPrefs.KEY_MODEL, "") ?: ""
        llmOutput.setText(prefs.getString(LlmPrefs.KEY_OUTPUT, "") ?: "")
        applyLlmColors()
    }

    private fun appendOutput(text: String) {
        val current = llmOutput.text.toString()
        llmOutput.setText(if (current.isBlank()) text else "$current\n$text")
        llmOutput.setSelection(llmOutput.text.length)
    }

    /**
     * 给对话输出框着色：“我：”开头为绿色，“AI：”开头为白色，其余用默认文字色。
     * 只改颜色 span，不改文本，可安全在 TextWatcher 中调用。
     */
    private fun applyLlmColors() {
        val editable = llmOutput.text
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }
        val text = editable.toString()
        if (text.isEmpty()) return
        val meColor = ContextCompat.getColor(this, R.color.llm_me)
        val aiColor = ContextCompat.getColor(this, R.color.llm_ai)
        val normalColor = ContextCompat.getColor(this, R.color.text_primary)
        var start = 0
        for (line in text.split("\n")) {
            val end = (start + line.length).coerceAtMost(editable.length)
            val color = when {
                line.startsWith("我：") -> meColor
                line.startsWith("AI：") -> aiColor
                else -> normalColor
            }
            if (start < end) {
                editable.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            start = end + 1
        }
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
        startThinking()
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
            stopThinking()
        }
    }

    /** 显示“AI 正在思考…”动画（类 ChatGPT），请求发出后开始，回复/失败后停止 */
    private fun startThinking() {
        llmThinkingRow.visibility = View.VISIBLE
        val runnable = object : Runnable {
            private var dots = 0
            override fun run() {
                dots = (dots % 3) + 1
                llmThinkingText.text = "AI 正在思考" + ".".repeat(dots)
                thinkingHandler.postDelayed(this, 400)
            }
        }
        thinkingRunnable = runnable
        runnable.run()
    }

    private fun stopThinking() {
        thinkingRunnable?.let { thinkingHandler.removeCallbacks(it) }
        thinkingRunnable = null
        llmThinkingRow.visibility = View.GONE
    }

    /**
     * 把对话输出框内容发送到蓝牙键盘。
     * 默认不发送“我：”的发言（只发 AI 回复）；勾选“包含我的发言”后才全部发送。
     */
    private fun sendOutputToKeyboard() {
        val full = llmOutput.text.toString().trim()
        if (full.isEmpty()) {
            Toast.makeText(this, "对话输出为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (!connected) {
            Toast.makeText(this, "尚未连接到电脑", Toast.LENGTH_SHORT).show()
            return
        }
        val text = if (llmIncludeMeCheck.isChecked) {
            full
        } else {
            full.lines().filterNot { it.trimStart().startsWith("我：") }
                .joinToString("\n")
                .trim()
        }
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可发送的内容（已默认排除“我”的发言，可勾选“包含我的发言”）", Toast.LENGTH_SHORT).show()
            return
        }
        setKeepScreenOn(true)
        lifecycleScope.launch {
            try {
                if (text.length > TypingEngine.CHUNK_SIZE) {
                    appendLog("对话内容较长（${text.length} 字），已自动分段发送")
                }
                hidProtocol.typeText(text)
                appendLog("已把对话内容发送到电脑")
            } finally {
                setKeepScreenOn(false)
            }
        }
    }

    private fun clearLlmConversation() {
        llmOutput.setText("")
        llmHistory.clear()
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
        val listView = dialogView.findViewById<android.widget.ListView>(R.id.phraseList)
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

        val container = android.widget.FrameLayout(this)
        container.setPadding(dp(20), dp(8), dp(20), 0)
        container.addView(input, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
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

    // ===== 日志 =====

    private fun appendLog(message: String) {
        LogStore.append(message)
    }

    // ===== 权限 =====

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

    // ===== 中文输入模式下拉适配（选中项左侧绿色 √） =====

    private inner class UnicodeModeAdapter(context: Context, items: List<String>) :
        ArrayAdapter<String>(context, R.layout.item_unicode_mode, R.id.modeLabel, items) {

        private var selected = 0

        fun setSelected(position: Int) {
            selected = position
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            bind(view, position)
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            bind(view, position)
            return view
        }

        private fun bind(view: View, position: Int) {
            val check = view.findViewById<TextView>(R.id.modeCheck)
            check.text = if (position == selected) "✓" else ""
            check.visibility = if (position == selected) View.VISIBLE else View.INVISIBLE
        }
    }
}




