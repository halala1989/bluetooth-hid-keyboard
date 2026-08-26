package com.hidble.keyboard

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
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
        private const val DEFAULT_SPEED_LEVEL = 5
        private const val SPEED_MIN = 1
        private const val SPEED_MAX = 10
    }

    private lateinit var statusText: TextView
    private lateinit var statusDot: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var registerButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var textInput: EditText
    private lateinit var sendButton: Button
    private lateinit var speedInput: EditText
    private lateinit var applySpeedButton: Button
    private lateinit var phraseButton: Button
    private lateinit var commandLog: TextView
    private lateinit var deviceList: ListView

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
        // 蓝牙可能在此前未开启，重新获取 HID profile
        hidManager.init()
        if (registered) {
            loadBondedDevices()
        }
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        deviceNameText = findViewById(R.id.deviceNameText)
        registerButton = findViewById(R.id.registerButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        textInput = findViewById(R.id.textInput)
        sendButton = findViewById(R.id.sendButton)
        speedInput = findViewById(R.id.speedInput)
        applySpeedButton = findViewById(R.id.applySpeedButton)
        phraseButton = findViewById(R.id.phraseButton)
        commandLog = findViewById(R.id.commandLog)

        // 已配对设备列表
        deviceList = findViewById(R.id.deviceList)
        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        deviceList.adapter = deviceListAdapter

        // 输入速度 / 常用语（SharedPreferences）
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        savedSpeedLevel = prefs.getInt(KEY_SPEED_LEVEL, DEFAULT_SPEED_LEVEL)
        speedInput.setText(savedSpeedLevel.toString())
        loadPhrases()

        updateConnectionState()
    }

    private fun initHid() {
        hidManager = HidDeviceManager(this)
        hidManager.init()
        hidProtocol = HidProtocol(TypingEngine { data -> hidManager.sendReport(data) })

        hidManager.onAppStatusChanged = { reg ->
            runOnUiThread {
                registered = reg
                if (reg) {
                    appendLog("蓝牙键盘已启动：请到电脑蓝牙中添加“${HidDeviceManager.KEYBOARD_NAME}”并配对")
                    loadBondedDevices()
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
            }
        }

        hidManager.onLedState = { led ->
            hidProtocol.onLedReport(led)
        }
    }

    private fun setupListeners() {
        registerButton.setOnClickListener {
            if (!hidManager.isBluetoothOn()) {
                Toast.makeText(this, "请先开启手机蓝牙", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (registered) {
                hidManager.unregister()
                connected = false
                updateConnectionState()
            } else {
                appendLog("正在启动蓝牙键盘...")
                hidManager.register()
            }
        }

        disconnectButton.setOnClickListener {
            hidManager.disconnect()
            appendLog("已断开连接")
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

        // 已配对设备点击连接
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
    }

    private fun sendText() {
        val text = textInput.text.toString()
        if (text.isNotEmpty()) {
            if (!connected) {
                Toast.makeText(this, "尚未连接到电脑", Toast.LENGTH_SHORT).show()
                return
            }
            lifecycleScope.launch {
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
            registerButton.isEnabled = false
            disconnectButton.isEnabled = true
            deviceNameText.text = hidManager.connectedDeviceName() ?: "已连接到电脑"
        } else if (registered) {
            statusText.text = "键盘已启动"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.accent))
            statusDot.setTextColor(ContextCompat.getColor(this, R.color.accent))
            registerButton.text = "停止键盘"
            registerButton.isEnabled = true
            disconnectButton.isEnabled = false
            deviceNameText.text = "在电脑蓝牙中添加“${HidDeviceManager.KEYBOARD_NAME}”并配对，然后点下方已配对设备连接"
        } else {
            statusText.text = "未启动"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            statusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            registerButton.text = "启动键盘"
            registerButton.isEnabled = true
            disconnectButton.isEnabled = false
            deviceNameText.text = "点“启动键盘”把手机变成蓝牙键盘，再到电脑上添加并配对"
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
