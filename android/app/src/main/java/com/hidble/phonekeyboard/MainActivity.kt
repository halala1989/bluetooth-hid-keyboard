package com.hidble.phonekeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        private const val PROMPT_PRESET_VERSION = 2
        private const val MAX_ATTACH_MB = 12
        private const val MAX_ATTACH_BYTES = MAX_ATTACH_MB * 1024 * 1024
        private const val MAX_ATTACH_COUNT = 4
        private const val MAX_CONVERSATIONS = 50

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
    private lateinit var llmNewConversationButton: Button
    private lateinit var llmSendToKeyboardButton: Button
    private lateinit var llmClearButton: Button
    private lateinit var llmIncludeMeCheck: CheckBox
    private lateinit var llmThinkingRow: android.view.View
    private lateinit var llmThinkingText: TextView
    private lateinit var llmSettingsButton: Button
    private lateinit var llmSettingsTopButton: Button
    private lateinit var unicodeModeSpinner: Spinner
    private lateinit var llmPromptSpinner: Spinner
    private lateinit var llmHistorySpinner: Spinner
    private lateinit var llmAttachmentInfo: TextView
    private lateinit var llmAttachButton: Button

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

    // ===== 大模型对话数据结构 =====
    // 附件（“＋”添加的本地图片/音频；base64 仅保留在内存，持久化只存名称，重启后不重传原文件）
    private data class LlmFilePart(
        val kind: String, // "image" | "audio"
        val name: String,
        val mime: String,
        val base64: String
    )

    // 对话消息：正文 + 本条消息随附的文件
    private class LlmHistoryMsg(
        val role: String,
        val text: String,
        val files: List<LlmFilePart> = emptyList()
    )

    // 历史对话快照（点“新对话”/载入旧对话前自动存档，供历史下拉查看与恢复）
    private data class LlmConversationSnapshot(
        val id: String,
        val title: String,
        val time: Long,
        val messages: List<LlmHistoryMsg>,
        val output: String
    )

    private val llmHistory = mutableListOf<LlmHistoryMsg>()
    private val llmConversations = mutableListOf<LlmConversationSnapshot>()
    private val pendingFiles = mutableListOf<LlmFilePart>()
    private var llmBusy = false
    /** 流式输出中：跳过逐字着色/保存，结束时统一处理 */
    private var llmStreaming = false

    // 提示词预设（名称 + 内容，发送前自动拼到用户输入前面）
    private data class LlmPrompt(val name: String, val content: String)
    private val llmPrompts = mutableListOf<LlmPrompt>()
    private val llmPromptItems = mutableListOf<String>()
    private var selectedPromptName: String? = null
    private var suppressPromptEvent = false
    private lateinit var llmPromptAdapter: ArrayAdapter<String>
    private lateinit var llmHistoryAdapter: ArrayAdapter<String>
    private val llmHistoryItems = mutableListOf<String>()
    private var suppressHistoryEvent = false

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

    // 正在发送的协程（用于“停止”中止）
    private var sendJob: Job? = null
    private var llmSendJob: Job? = null

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

    // 大模型“＋”附件：系统文件选择器（图片/音频，可多选）
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val uris = mutableListOf<Uri>()
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        uris.add(clip.getItemAt(i).uri)
                    }
                }
                if (uris.isEmpty()) {
                    data.data?.let { uris.add(it) }
                }
                uris.forEach { addAttachment(it) }
            }
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
        // 键盘已注册但连接掉了：回到前台时尝试恢复
        hidManager.reconnectIfNeeded()
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
        if (!registered) stopHidService()
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
        llmPromptSpinner = findViewById(R.id.llmPromptSpinner)
        llmHistorySpinner = findViewById(R.id.llmHistorySpinner)
        llmAttachmentInfo = findViewById(R.id.llmAttachmentInfo)
        llmAttachButton = findViewById(R.id.llmAttachButton)
        llmNewConversationButton = findViewById(R.id.llmNewConversationButton)

        // 历史对话下拉（与提示词下拉同一样式：白字单行）
        llmHistoryAdapter = ArrayAdapter(this, R.layout.item_llm_prompt, R.id.promptLabel, llmHistoryItems)
        llmHistorySpinner.adapter = llmHistoryAdapter
        refreshAttachmentUi()

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
                if (!llmStreaming) applyLlmColors()
            }
        })
    }

    private fun initHid() {
        hidManager = HidDeviceManager(this)
        hidManager.init()
        val engine = TypingEngine(
            send = { data -> hidManager.sendReport(data) },
            onSendInterrupted = { sent ->
                appendLog("发送中断：蓝牙连接可能已断开（已发送 $sent 字），请检查连接后重试")
            }
        )
        engine.onGbkNumLockWarning = {
            appendLog("提示：目标电脑 NumLock 未开启，GBK 输入会变成方向键并可能误选/误删文本；请先在电脑上按一次 NumLock 再发送")
        }
        hidProtocol = HidProtocol(
            engine
        )
        hidProtocol.setSpeed(savedSpeedLevel)
        hidProtocol.setUnicodeMode(savedUnicodeMode)

        hidManager.onAppStatusChanged = { reg ->
            runOnUiThread {
                registered = reg
                if (reg) {
                    startHidService()
                    appendLog("蓝牙键盘已启动：请确认“对附近设备可见”，然后到电脑上搜索并配对")
                    requestDiscoverable()
                } else {
                    stopHidService()
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
                    TypingEngine.MODE_GBK -> "GBK 机内码（仅中文版 Windows，需 NumLock）"
                    else -> "Alt+X（默认，记事本/Word 等，无需注册表）"
                }
                appendLog("中文输入模式: $desc")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 二级页面入口
        // 顶部状态条：点击（含红色“未启动”文字）直达连接管理
        findViewById<android.view.View>(R.id.statusCard).setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }
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
        llmNewConversationButton.setOnClickListener { startNewConversation() }
        llmAttachButton.setOnClickListener { launchFilePicker() }
        llmAttachButton.setOnLongClickListener {
            if (pendingFiles.isEmpty()) {
                Toast.makeText(this, "当前没有待发送的附件", Toast.LENGTH_SHORT).show()
            } else {
                pendingFiles.clear()
                refreshAttachmentUi()
                appendLog("已清空待发送附件")
            }
            true
        }
        setupPromptSpinner()
        setupHistorySpinner()

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
            stopHidService()
            appendLog("蓝牙键盘已关闭")
            refreshAllState()
        }
    }

    /** 键盘注册期间启动前台服务：防止系统因“App 不在前台”注销 HID 导致断连 */
    private fun startHidService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, HidKeyboardService::class.java))
        } catch (e: Exception) {
            appendLog("无法启动后台保活服务：${e.message}")
        }
    }

    private fun stopHidService() {
        try {
            stopService(Intent(this, HidKeyboardService::class.java))
        } catch (e: Exception) {
            // 忽略：服务可能未启动
        }
    }

    /** 连接管理页：点选已配对设备连接 */
    fun connectToDevice(device: BluetoothDevice) {
        if (!hidManager.isRegistered()) {
            appendLog("键盘未启动，先启动蓝牙键盘再连接...")
            registerKeyboard()
        }
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
                hidProtocol.releaseAll()
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
        // 正在发送：再次点击 = 中止发送
        if (sendJob?.isActive == true) {
            sendJob?.cancel()
            sendJob = null
            sendButton.text = "发送到键盘"
            appendLog("已停止发送文本")
            return
        }
        val text = textInput.text.toString()
        if (text.isNotEmpty()) {
            if (!connected) {
                Toast.makeText(this, "尚未连接到电脑", Toast.LENGTH_SHORT).show()
                return
            }
            setKeepScreenOn(true)
            sendButton.text = "停止"
            sendJob = lifecycleScope.launch {
                try {
                    if (text.length > TypingEngine.CHUNK_SIZE) {
                        appendLog("文本较长（${text.length} 字），已自动分段发送")
                    }
                    hidProtocol.typeText(text)
                    appendLog("发送文本: $text")
                    textInput.text.clear()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    appendLog("文本发送已中止")
                    throw e
                } finally {
                    hidProtocol.releaseAll()
                    setKeepScreenOn(false)
                    sendJob = null
                    sendButton.text = "发送到键盘"
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
        loadLlmHistory()
        applyLlmColors()
    }

    /**
     * 恢复当前对话（KEY_HISTORY，附件只留名称/类型，不保留原文件字节）
     * 和历史存档列表（KEY_CONVERSATIONS）。
     */
    private fun loadLlmHistory() {
        llmHistory.clear()
        try {
            val raw = prefs.getString(LlmPrefs.KEY_HISTORY, null)
            if (!raw.isNullOrBlank()) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    parseHistoryMessage(arr.getJSONObject(i))?.let { llmHistory.add(it) }
                }
            }
        } catch (e: Exception) {
            llmHistory.clear()
        }

        llmConversations.clear()
        try {
            val raw = prefs.getString(LlmPrefs.KEY_CONVERSATIONS, null)
            if (!raw.isNullOrBlank()) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    parseConversation(arr.getJSONObject(i))?.let { llmConversations.add(it) }
                }
            }
        } catch (e: Exception) {
            llmConversations.clear()
        }
        refreshHistorySpinner()
    }

    /** 保存当前对话上下文（重启后保留多轮文字；附件只保留名称，不存原文件） */
    private fun saveLlmHistory() {
        // 只保留最近 40 条，避免请求体无限增长
        val trimmed = llmHistory.takeLast(40)
        val arr = JSONArray()
        trimmed.forEach { arr.put(historyMessageToJson(it, withBase64 = false)) }
        prefs.edit().putString(LlmPrefs.KEY_HISTORY, arr.toString()).apply()
    }

    // ===== 消息 / 历史存档的 JSON 序列化 =====

    private fun historyMessageToJson(msg: LlmHistoryMsg, withBase64: Boolean): JSONObject {
        val obj = JSONObject().put("role", msg.role).put("content", msg.text)
        if (msg.files.isNotEmpty()) {
            val arr = JSONArray()
            msg.files.forEach { f ->
                val fo = JSONObject()
                    .put("kind", f.kind)
                    .put("name", f.name)
                    .put("mime", f.mime)
                if (withBase64) fo.put("base64", f.base64)
                arr.put(fo)
            }
            obj.put("files", arr)
        }
        return obj
    }

    private fun parseHistoryMessage(obj: JSONObject): LlmHistoryMsg? {
        val role = obj.optString("role")
        val content = obj.optString("content")
        if ((role != "user" && role != "assistant") || content.isBlank()) return null
        val files = mutableListOf<LlmFilePart>()
        val filesArr = obj.optJSONArray("files")
        if (filesArr != null) {
            for (i in 0 until filesArr.length()) {
                val fo = filesArr.optJSONObject(i) ?: continue
                val kind = fo.optString("kind")
                val name = fo.optString("name")
                if ((kind == "image" || kind == "audio") && name.isNotEmpty()) {
                    files.add(LlmFilePart(kind, name, fo.optString("mime"), fo.optString("base64")))
                }
            }
        }
        return LlmHistoryMsg(role, content, files)
    }

    private fun parseConversation(obj: JSONObject): LlmConversationSnapshot? {
        return try {
            val messages = mutableListOf<LlmHistoryMsg>()
            val arr = obj.optJSONArray("messages")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parseHistoryMessage(arr.optJSONObject(i) ?: continue)?.let { messages.add(it) }
                }
            }
            LlmConversationSnapshot(
                id = obj.optString("id", System.currentTimeMillis().toString()),
                title = obj.optString("title", "历史对话"),
                time = obj.optLong("time", 0L),
                messages = messages,
                output = obj.optString("output", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun saveConversations() {
        val arr = JSONArray()
        llmConversations.forEach { snap ->
            val msgs = JSONArray()
            snap.messages.forEach { msgs.put(historyMessageToJson(it, withBase64 = false)) }
            arr.put(
                JSONObject()
                    .put("id", snap.id)
                    .put("title", snap.title)
                    .put("time", snap.time)
                    .put("messages", msgs)
                    .put("output", snap.output)
            )
        }
        prefs.edit().putString(LlmPrefs.KEY_CONVERSATIONS, arr.toString()).apply()
    }

    // ===== 历史对话下拉：当前对话 + 已存档会话 =====

    private fun setupHistorySpinner() {
        llmHistorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressHistoryEvent) return
                if (position == 0 || llmConversations.isEmpty() || (position - 1) !in llmConversations.indices) {
                    resetHistorySelection()
                    return
                }
                showHistoryConversationDialog(position - 1)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun refreshHistorySpinner() {
        llmHistoryItems.clear()
        llmHistoryItems.add("◉ 当前对话（正在编辑）")
        llmConversations.forEach { c ->
            val timeLabel = formatTime(c.time)
            llmHistoryItems.add("🕘 ${c.title}" + if (timeLabel.isEmpty()) "" else " · $timeLabel")
        }
        if (llmConversations.isEmpty()) {
            llmHistoryItems.add("（暂无已存档的历史对话）")
        }
        llmHistoryAdapter.notifyDataSetChanged()
        resetHistorySelection()
    }

    private fun resetHistorySelection() {
        suppressHistoryEvent = true
        llmHistorySpinner.setSelection(0, false)
        suppressHistoryEvent = false
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return ""
        return SimpleDateFormat("M-d HH:mm", Locale.getDefault()).format(Date(millis))
    }

    /** 把当前对话（若有内容）存档到历史列表头部 */
    private fun archiveCurrentConversation(): String? {
        if (llmHistory.isEmpty() && llmOutput.text.isBlank()) return null
        val firstUserLine = llmHistory.firstOrNull { it.role == "user" }
            ?.text?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        val now = System.currentTimeMillis()
        val title = if (firstUserLine.isNotEmpty()) firstUserLine.take(16) else "对话 ${formatTime(now)}"
        llmConversations.add(
            0,
            LlmConversationSnapshot(
                id = "c${now}_${llmHistory.size}",
                title = title,
                time = now,
                // 存档时只保留文件名称/类型（丢弃 base64），避免多个大附件长期占用内存；
                // 如需继续追问旧对话的图片/音频，重新用“＋”添加即可。
                messages = llmHistory.map { msg ->
                    LlmHistoryMsg(
                        msg.role,
                        msg.text,
                        msg.files.map { f -> f.copy(base64 = "") }
                    )
                },
                output = llmOutput.text.toString()
            )
        )
        while (llmConversations.size > MAX_CONVERSATIONS) {
            llmConversations.removeAt(llmConversations.lastIndex)
        }
        saveConversations()
        return title
    }

    /** “新对话”：先自动存档旧对话，再清空当前上下文与输出框 */
    private fun startNewConversation() {
        if (llmBusy) {
            Toast.makeText(this, "正在等待模型回复，请稍候再开新对话", Toast.LENGTH_SHORT).show()
            return
        }
        val archived = archiveCurrentConversation()
        llmHistory.clear()
        llmOutput.setText("")
        pendingFiles.clear()
        refreshAttachmentUi()
        prefs.edit()
            .remove(LlmPrefs.KEY_HISTORY)
            .putString(LlmPrefs.KEY_OUTPUT, "")
            .apply()
        refreshHistorySpinner()
        appendLog(
            if (archived != null) "已开始新对话（旧对话已存入下方历史下拉：「$archived」）"
            else "已开始新对话"
        )
    }

    private fun showHistoryConversationDialog(index: Int) {
        val snap = llmConversations.getOrNull(index) ?: run {
            resetHistorySelection()
            return
        }
        val outputText = snap.output.ifBlank { "（该对话没有输出内容）" }
        val content = TextView(this).apply {
            text = outputText
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setLineSpacing(dp(2).toFloat(), 1f)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(dp(20), dp(6), dp(20), 0)
            addView(
                scroll,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(320)
                )
            )
        }
        val msgCount = snap.messages.count { it.role == "user" || it.role == "assistant" }
        val dialog = AlertDialog.Builder(this)
            .setTitle("历史对话：${snap.title}（${msgCount} 条消息）")
            .setView(container)
            .setPositiveButton("载入为当前对话") { _, _ -> loadHistoryConversation(snap.id) }
            .setNeutralButton("删除") { _, _ -> confirmDeleteConversation(snap.id) }
            .setNegativeButton("关闭", null)
            .create()
        dialog.setOnDismissListener { resetHistorySelection() }
        dialog.show()
    }

    private fun loadHistoryConversation(id: String) {
        val snap = llmConversations.firstOrNull { it.id == id } ?: run {
            resetHistorySelection()
            return
        }
        // 当前正在编辑的内容先存档，避免切换历史时丢失
        archiveCurrentConversation()
        llmConversations.removeAll { it.id == id }
        llmHistory.clear()
        llmHistory.addAll(snap.messages)
        llmOutput.setText(snap.output)
        applyLlmColors()
        prefs.edit().putString(LlmPrefs.KEY_OUTPUT, snap.output).apply()
        saveLlmHistory()
        saveConversations()
        refreshHistorySpinner()
        appendLog("已载入历史对话：${snap.title}")
    }

    private fun confirmDeleteConversation(id: String) {
        val snap = llmConversations.firstOrNull { it.id == id } ?: run {
            resetHistorySelection()
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("删除历史对话")
            .setMessage("确定删除「${snap.title}」吗？删除后不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                llmConversations.removeAll { it.id == id }
                saveConversations()
                refreshHistorySpinner()
                appendLog("已删除历史对话：${snap.title}")
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnDismissListener { resetHistorySelection() }
        dialog.show()
    }

    // ===== “＋”本地文件（图片/音频）→ 多模态消息 =====

    private fun refreshAttachmentUi() {
        if (pendingFiles.isEmpty()) {
            llmAttachmentInfo.text = "可点“＋”添加本地图片/音频，随下一条消息发给模型（多模态）"
        } else {
            val names = pendingFiles.joinToString("、") {
                (if (it.kind == "image") "图片" else "音频") + "「" + it.name + "」"
            }
            llmAttachmentInfo.text = "已附加 ${pendingFiles.size} 个文件：$names（点＋继续加，长按＋清空）"
        }
    }

    private fun launchFilePicker() {
        if (llmBusy) {
            Toast.makeText(this, "正在等待模型回复，请稍候再添加", Toast.LENGTH_SHORT).show()
            return
        }
        if (pendingFiles.size >= MAX_ATTACH_COUNT) {
            Toast.makeText(this, "一次最多附带 $MAX_ATTACH_COUNT 个文件", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "audio/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addAttachment(uri: Uri) {
        if (pendingFiles.size >= MAX_ATTACH_COUNT) {
            Toast.makeText(this, "一次最多附带 $MAX_ATTACH_COUNT 个文件", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val mime = contentResolver.getType(uri) ?: guessMimeFromName(uri.lastPathSegment)
            val kind = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("audio/") -> "audio"
                else -> {
                    Toast.makeText(this, "暂只支持图片或音频文件", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            val bytes = readUriBytes(uri)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val name = queryDisplayName(uri) ?: (uri.lastPathSegment ?: "file_${System.currentTimeMillis()}")
            pendingFiles.add(LlmFilePart(kind, name, mime, base64))
            refreshAttachmentUi()
            appendLog("已添加附件：$name（${if (kind == "image") "图片" else "音频"}，${bytes.size / 1024} KB）")
        } catch (e: Exception) {
            Toast.makeText(this, "添加附件失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun readUriBytes(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri)
            ?: throw RuntimeException("无法读取所选文件")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n <= 0) break
                total += n
                if (total > MAX_ATTACH_BYTES) {
                    throw RuntimeException("文件超过 $MAX_ATTACH_MB MB 上限")
                }
                out.write(buffer, 0, n)
            }
            if (total == 0) throw RuntimeException("文件为空")
            return out.toByteArray()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun guessMimeFromName(name: String?): String {
        val n = name?.lowercase(Locale.getDefault()).orEmpty()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".bmp") -> "image/bmp"
            n.endsWith(".heic") || n.endsWith(".heif") -> "image/heic"
            n.endsWith(".mp3") -> "audio/mpeg"
            n.endsWith(".wav") -> "audio/wav"
            n.endsWith(".flac") -> "audio/flac"
            n.endsWith(".m4a") -> "audio/mp4"
            n.endsWith(".aac") -> "audio/aac"
            n.endsWith(".ogg") || n.endsWith(".oga") -> "audio/ogg"
            n.endsWith(".amr") -> "audio/amr"
            else -> "application/octet-stream"
        }
    }

    /** 历史消息 → API 消息：附件有真实字节就转成 image/audio 部件，否则转成文字说明 */
    private fun LlmHistoryMsg.toApiMessage(): LlmMessage {
        val realFiles = files.filter { it.base64.isNotBlank() }
        val lostFiles = files.filter { it.base64.isBlank() }
        var text = this.text
        if (lostFiles.isNotEmpty()) {
            val note = lostFiles.joinToString("、") { it.name }
            text = (text + "\n\n[以下附件在重启后无法再次上传，仅保留名称：$note]").trim()
        }
        if (realFiles.isEmpty()) return LlmMessage.text(role, text)
        val parts = mutableListOf<LlmPart>()
        parts.add(LlmPart(type = "text", text = text.ifBlank { "请分析我发送的图片/音频。" }))
        realFiles.forEach { f ->
            parts.add(LlmPart(type = f.kind, mime = f.mime, base64 = f.base64))
        }
        return LlmMessage(role, parts)
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

    // ===== 提示词预设（下拉菜单：无提示词 / 已存预设 / 新建 / 删除） =====

    /** 内置提示词预设：通用书面化 + 医疗场景模板（遵循中国《病历书写基本规范》） */
    private fun builtinPrompts(): List<LlmPrompt> = listOf(
        LlmPrompt(
            "书面化整理",
            "这是一段与患者的交谈，请用正式、书面的语言重写下面的内容：" +
                "去除口语化表达、语气词、重复内容和多余的符号/制表符，使整段话专业、正式、通顺。"
        ),
        LlmPrompt(
            "门诊病历草稿",
            """
            你是医院的门诊病历助手。请把下面这段“医生与患者的对话”整理成一份规范的门诊病历草稿，遵循中国《病历书写基本规范》：

            【主诉】患者最核心的不适＋持续时间，一句话，尽量20字以内
            【现病史】按时间顺序写：起病诱因、症状特点（部位/性质/程度/持续时间/缓解与加重因素）、伴随症状、发病以来的诊治经过、一般情况（精神/食欲/睡眠/大小便/体重变化）
            【既往史】既往疾病、手术外伤、过敏、长期用药、烟酒史（对话中提到的才写，没提到写“未提及”）
            【体格检查】对话中提到的查体所见；未提到的项目写“待补充”
            【辅助检查】对话中提到的检验/检查结果；没有的写“待补充”
            【初步诊断】根据已有信息给出初步考虑，标注“待确认”
            【处理意见】按对话中医生给出的建议整理

            要求：
            1. 只整理对话中出现的信息，禁止编造，信息不足写“待补充/未提及”
            2. 医学术语规范、语句简洁书面
            3. 保留关键时间点和数字（体温、血压、疼痛评分等）
            4. 开头注明“AI草稿，需医生审核后使用”
            """.trimIndent()
        ),
        LlmPrompt(
            "入院记录草稿",
            """
            你是医院病历助手。请把下面这段“医生与患者的对话”整理成一份《入院记录》草稿，遵循中国《病历书写基本规范》：

            【一般项目】姓名、性别、年龄、民族、婚否、职业、入院时间、病史陈述者（对话中有才写，缺的写“待补充”）
            【主诉】核心症状＋持续时间，一句话
            【现病史】发病情况与诱因、主要症状特点（部位/性质/程度/持续时间/缓解加重因素）、伴随症状、发病以来诊治经过、一般情况
            【既往史】既往疾病、手术外伤、过敏、输血、预防接种、传染病史
            【个人史/婚育史/家族史】对话中提到的才写
            【体格检查】按对话中查体信息整理，未提到的写“待补充”
            【辅助检查】对话中提到的检查结果
            【初步诊断】根据信息给出，标注“待确认”

            要求：只整理对话中出现的信息，禁止编造，不足写“待补充/未提及”；医学术语规范、书面表达；开头注明“AI草稿，需医生审核”。
            """.trimIndent()
        ),
        LlmPrompt(
            "SOAP病历",
            """
            你是临床病历助手。请把下面的“医生与患者对话”按 SOAP 格式整理成病历草稿：

            【S 主观资料】患者主诉与现病史（用患者陈述整理，突出时间/部位/性质/程度/诱因/缓解因素）
            【O 客观资料】查体所见、生命体征、辅助检查结果（对话中提到的）
            【A 评估】病情分析与初步诊断/鉴别诊断（基于已有信息，标注“待确认”）
            【P 计划】进一步检查、治疗方案、用药、随访建议（按对话中医生意见整理）

            要求：只写对话中出现的信息，禁止编造，不足写“待补充”；开头注明“AI草稿，需医生审核”。
            """.trimIndent()
        ),
        LlmPrompt(
            "病历规范书面化",
            """
            你是病历文书助手。请把下面这段口语化、杂乱的内容，改写为规范、书面、符合中国《病历书写基本规范》的病历语言：

            - 去除语气词、口头禅、重复、口语化表达
            - 使用规范医学术语（如“拉肚子”→“腹泻”，“喘不上气”→“呼吸困难”）
            - 保留全部医学关键信息：时间、部位、性质、程度、诱因、缓解/加重因素、伴随症状、具体数字
            - 不增删医学事实，不编造
            - 输出为连贯的书面段落，可适当分条
            - 开头注明“AI润色草稿，需医生核对”
            """.trimIndent()
        ),
        LlmPrompt(
            "患者沟通解释",
            """
            你是医患沟通助手。请把下面这段专业内容（诊断/检查/治疗方案/注意事项）用通俗易懂的中文向患者解释：

            - 用日常语言和形象比喻，避免生僻医学术语；必须用术语时附一句简单解释
            - 讲清楚“是什么、为什么、下一步怎么做、要注意什么”
            - 语气亲切、耐心，让患者安心
            - 不夸大疗效、不保证结果
            - 输出3-6条简短要点，适合口头向患者说明
            """.trimIndent()
        ),
        LlmPrompt(
            "随访记录",
            """
            你是随访记录助手。请把下面这段“随访通话/复诊对话”整理成一份随访记录：

            【随访时间与方式】对话中提到的日期和方式
            【患者目前情况】症状变化、用药依从性、不良反应、生活方式、复查结果
            【本次沟通要点】患者反馈的问题与诉求
            【下一步计划】按对话中医生的意见整理（复诊时间、用药调整、注意事项）

            要求：只整理对话中出现的信息，禁止编造，不足写“待补充/未提及”；书面规范；开头注明“AI草稿，需医生审核”。
            """.trimIndent()
        )
    )

    private fun loadLlmPrompts() {
        llmPrompts.clear()
        val raw = prefs.getString(LlmPrefs.KEY_PROMPTS, null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name").trim()
                    val content = obj.optString("content").trim()
                    if (name.isNotEmpty() && content.isNotEmpty()) {
                        llmPrompts.add(LlmPrompt(name, content))
                    }
                }
            } catch (e: Exception) {
                // 数据损坏时忽略，走默认预设
            }
        }
        // 内置预设按版本增量补齐：不覆盖用户已有的自定义提示词，只补缺的名称
        val presetVersion = prefs.getInt(LlmPrefs.KEY_PROMPT_PRESET_VERSION, 0)
        if (presetVersion < PROMPT_PRESET_VERSION) {
            var changed = false
            for (p in builtinPrompts()) {
                if (llmPrompts.none { it.name == p.name }) {
                    llmPrompts.add(p)
                    changed = true
                }
            }
            if (changed) saveLlmPrompts()
            prefs.edit().putInt(LlmPrefs.KEY_PROMPT_PRESET_VERSION, PROMPT_PRESET_VERSION).apply()
        }
        selectedPromptName = prefs.getString(LlmPrefs.KEY_SELECTED_PROMPT, null)
        if (selectedPromptName != null && llmPrompts.none { it.name == selectedPromptName }) {
            selectedPromptName = null
        }
        refreshPromptSpinner()
    }

    private fun saveLlmPrompts() {
        val arr = JSONArray()
        llmPrompts.forEach { p ->
            arr.put(JSONObject().put("name", p.name).put("content", p.content))
        }
        prefs.edit().putString(LlmPrefs.KEY_PROMPTS, arr.toString()).apply()
    }

    private fun currentPromptPosition(): Int {
        val idx = llmPrompts.indexOfFirst { it.name == selectedPromptName }
        return if (idx >= 0) idx + 1 else 0
    }

    private fun refreshPromptSpinner() {
        llmPromptItems.clear()
        llmPromptItems.add("无提示词（直接发送）")
        llmPrompts.forEach { llmPromptItems.add(it.name) }
        llmPromptItems.add("＋ 新建提示词…")
        llmPromptItems.add("✎ 管理提示词…")
        llmPromptAdapter.notifyDataSetChanged()
        suppressPromptEvent = true
        llmPromptSpinner.setSelection(currentPromptPosition())
        suppressPromptEvent = false
    }

    private fun setupPromptSpinner() {
        llmPromptAdapter = ArrayAdapter(this, R.layout.item_llm_prompt, R.id.promptLabel, llmPromptItems)
        llmPromptSpinner.adapter = llmPromptAdapter
        llmPromptSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressPromptEvent) return
                val actionBase = llmPrompts.size + 1
                when {
                    position == 0 -> {
                        selectedPromptName = null
                        prefs.edit().remove(LlmPrefs.KEY_SELECTED_PROMPT).apply()
                    }
                    position in 1..llmPrompts.size -> {
                        selectedPromptName = llmPrompts[position - 1].name
                        prefs.edit().putString(LlmPrefs.KEY_SELECTED_PROMPT, selectedPromptName).apply()
                    }
                    position == actionBase -> {
                        resetPromptSelectionSilently()
                        showPromptEditDialog(null)
                    }
                    position == actionBase + 1 -> {
                        resetPromptSelectionSilently()
                        showPromptManageDialog()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        loadLlmPrompts()
    }

    private fun resetPromptSelectionSilently() {
        suppressPromptEvent = true
        llmPromptSpinner.setSelection(currentPromptPosition())
        suppressPromptEvent = false
    }

    /** 新建/编辑提示词（existing==null 为新建，否则为编辑并原位更新） */
    private fun showPromptEditDialog(existing: LlmPrompt?) {
        val nameInput = EditText(this)
        nameInput.hint = "提示词名称（如：书面化整理）"
        nameInput.setText(existing?.name ?: "")
        nameInput.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        nameInput.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint))
        nameInput.setBackgroundResource(R.drawable.bg_input)
        nameInput.setPadding(dp(14), dp(10), dp(14), dp(10))

        val contentInput = EditText(this)
        contentInput.hint = "提示词内容（发送前自动拼到输入前面）"
        contentInput.setText(existing?.content ?: "")
        contentInput.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        contentInput.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint))
        contentInput.setBackgroundResource(R.drawable.bg_input)
        contentInput.setPadding(dp(14), dp(10), dp(14), dp(10))
        contentInput.minLines = 3
        contentInput.gravity = android.view.Gravity.TOP or android.view.Gravity.START

        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding(dp(20), dp(8), dp(20), 0)
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(10)
        container.addView(nameInput, lp)
        container.addView(contentInput)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "新建提示词" else "编辑提示词")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                if (name.isEmpty() || content.isEmpty()) {
                    Toast.makeText(this, "名称和内容都不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing == null) {
                    llmPrompts.add(LlmPrompt(name, content))
                    selectedPromptName = name
                    prefs.edit().putString(LlmPrefs.KEY_SELECTED_PROMPT, name).apply()
                    appendLog("已新建提示词「$name」并选中")
                } else {
                    val idx = llmPrompts.indexOfFirst { it.name == existing.name }
                    if (idx >= 0) {
                        llmPrompts[idx] = LlmPrompt(name, content)
                        if (selectedPromptName == existing.name) {
                            selectedPromptName = name
                            prefs.edit().putString(LlmPrefs.KEY_SELECTED_PROMPT, name).apply()
                        }
                    }
                    appendLog("已编辑提示词「$name」")
                }
                saveLlmPrompts()
                refreshPromptSpinner()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 管理提示词：点按=删除（二次确认），长按=编辑 */
    private fun showPromptManageDialog() {
        if (llmPrompts.isEmpty()) {
            Toast.makeText(this, "还没有提示词，可在下拉里“＋ 新建提示词”", Toast.LENGTH_SHORT).show()
            return
        }
        val names = llmPrompts.map { it.name }.toTypedArray()

        val listView = ListView(this)
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.background = getDrawable(R.drawable.bg_list)
        listView.setPadding(dp(4), dp(4), dp(4), dp(4))
        listView.divider = getDrawable(R.color.card_stroke)
        listView.dividerHeight = 1

        val dialog = AlertDialog.Builder(this)
            .setTitle("管理提示词（点按删除 · 长按编辑）")
            .setView(listView)
            .setNegativeButton("关闭", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position !in llmPrompts.indices) return@setOnItemClickListener
            val target = llmPrompts[position]
            AlertDialog.Builder(this)
                .setTitle("删除提示词")
                .setMessage("确定删除「${target.name}」吗？")
                .setPositiveButton("删除") { _, _ ->
                    llmPrompts.removeAt(position)
                    saveLlmPrompts()
                    if (selectedPromptName == target.name) {
                        selectedPromptName = null
                        prefs.edit().remove(LlmPrefs.KEY_SELECTED_PROMPT).apply()
                    }
                    refreshPromptSpinner()
                    dialog.dismiss()
                    appendLog("已删除提示词「${target.name}」")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (position in llmPrompts.indices) {
                showPromptEditDialog(llmPrompts[position])
            }
            true
        }

        dialog.show()
    }

    /** 进入模型设置二级页面（选择提供方自动填预设模型，只填 Token 即可） */
    private fun openLlmSettings() {
        startActivity(Intent(this, LlmSettingsActivity::class.java))
    }

    private fun sendToLlm() {
        val userText = llmInput.text.toString().trim()
        val attachments = pendingFiles.toList()
        if (userText.isEmpty() && attachments.isEmpty()) return
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

        val prompt = llmPrompts.firstOrNull { it.name == selectedPromptName }
        val rawText = when {
            prompt != null && userText.isNotEmpty() -> "${prompt.content.trim()}\n\n$userText"
            prompt != null -> prompt.content.trim()
            else -> userText
        }
        // 纯附件（无文字）时给模型一句引导，避免空正文
        val text = rawText.ifBlank { "请查看我发送的图片/音频，并给出分析或回复。" }

        llmBusy = true
        llmSendButton.isEnabled = false
        startThinking()
        if (userText.isNotEmpty()) appendOutput("我：$userText")
        if (attachments.isNotEmpty()) {
            val desc = attachments.joinToString("、") {
                (if (it.kind == "image") "图片" else "音频") + "「" + it.name + "」"
            }
            appendOutput("📎 本次已附带：$desc")
        } else if (userText.isEmpty()) {
            appendOutput("我：$text")
        }
        llmHistory.add(LlmHistoryMsg("user", text, attachments))
        saveLlmHistory()
        llmInput.text.clear()
        pendingFiles.clear()
        refreshAttachmentUi()
        if (prompt != null) appendLog("已应用提示词「${prompt.name}」")
        if (attachments.isNotEmpty()) appendLog("本条消息附带 ${attachments.size} 个文件（多模态格式：图片/音频）")
        appendLog("已发送给模型（${provider.displayName} / $model），正在流式回复...")

        lifecycleScope.launch {
            var started = false
            val apiMessages = mutableListOf(LlmMessage.text("system", "你是简洁的助手。"))
            llmHistory.forEach { apiMessages.add(it.toApiMessage()) }
            val reply = try {
                LlmClient.chatStream(
                    provider,
                    llmApiKey,
                    model,
                    apiMessages
                ) { delta ->
                    if (!started) {
                        started = true
                        runOnUiThread {
                            stopThinking()
                            llmStreaming = true
                            appendOutput("AI：")
                        }
                    }
                    runOnUiThread {
                        llmOutput.append(delta)
                        llmOutput.setSelection(llmOutput.text.length)
                    }
                }
            } catch (e: Exception) {
                appendLog("模型调用失败：${e.message}")
                null
            }
            if (reply == null) {
                Toast.makeText(
                    this@MainActivity,
                    if (started) "模型回复中断，详情见日志" else "模型调用失败，详情见日志",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                llmHistory.add(LlmHistoryMsg("assistant", reply))
                saveLlmHistory()
                appendLog("模型已回复")
            }
            llmStreaming = false
            applyLlmColors()
            prefs.edit().putString(LlmPrefs.KEY_OUTPUT, llmOutput.text.toString()).apply()
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
        // 正在发送：再次点击 = 中止发送
        if (llmSendJob?.isActive == true) {
            llmSendJob?.cancel()
            llmSendJob = null
            llmSendToKeyboardButton.text = "发送到键盘"
            appendLog("已停止发送对话内容")
            return
        }
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
        llmSendToKeyboardButton.text = "停止"
        llmSendJob = lifecycleScope.launch {
            try {
                if (text.length > TypingEngine.CHUNK_SIZE) {
                    appendLog("对话内容较长（${text.length} 字），已自动分段发送")
                }
                hidProtocol.typeText(text)
                appendLog("已把对话内容发送到电脑")
            } catch (e: kotlinx.coroutines.CancellationException) {
                appendLog("对话发送已中止")
                throw e
            } finally {
                hidProtocol.releaseAll()
                setKeepScreenOn(false)
                llmSendJob = null
                llmSendToKeyboardButton.text = "发送到键盘"
            }
        }
    }

    private fun clearLlmConversation() {
        llmOutput.setText("")
        llmHistory.clear()
        prefs.edit().remove(LlmPrefs.KEY_HISTORY).apply()
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




