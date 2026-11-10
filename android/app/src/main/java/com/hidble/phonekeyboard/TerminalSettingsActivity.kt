package com.hidble.phonekeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalSettingsActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 2101
        private const val PREFS_NAME = "hidble_prefs"
        private const val KEY_SPEED_LEVEL = "speed_level"
        private const val KEY_UNICODE_MODE = "unicode_mode"
    }

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var boardManager: BoardBleManager
    private lateinit var terminalGroup: RadioGroup
    private lateinit var connectButton: Button
    private lateinit var connectionState: TextView
    private lateinit var connectionHint: TextView
    private lateinit var outputIndicatorDot: TextView
    private lateinit var outputIndicatorTitle: TextView
    private lateinit var outputIndicatorDetail: TextView
    private lateinit var deviceList: ListView
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedValue: TextView
    private lateinit var unicodeModeSpinner: Spinner
    private lateinit var modelProviderSpinner: Spinner
    private lateinit var modelName: TextView
    private lateinit var apiTokenInput: EditText
    private lateinit var showTokenCheck: CheckBox
    private lateinit var testModelButton: Button
    private lateinit var modelTestState: TextView
    private lateinit var boardFirmwareHelpButton: Button
    private lateinit var exportBoardFirmwareButton: Button

    private val radioByTerminal = mutableMapOf<String, Int>()
    private var currentTerminal: TerminalDefinition = TerminalRegistry.byId(TerminalRegistry.PHONE_HID)
    private var loading = false
    private var pendingBoardDevice: BluetoothDevice? = null
    private var attemptingSavedAddress = false
    private var scanInProgress = false
    private var pendingFirmwareAsset: String? = null

    private val phoneStateListener: (KeyboardSnapshot) -> Unit = { snapshot ->
        runOnUiThread {
            if (currentTerminal.id == TerminalRegistry.PHONE_HID) {
                if (snapshot.connected) connecting = false
                refreshConnectionUi(snapshot)
            }
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && KeyboardRuntime.isBluetoothOn()) {
            if (pendingPhoneConnect) {
                pendingPhoneConnect = false
                connectPhoneComputer()
            } else {
                beginPhoneHidRegistration()
            }
        } else {
            Toast.makeText(this, "需要开启手机蓝牙才能配对电脑", Toast.LENGTH_LONG).show()
        }
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            connectionState.text = "手机已对附近设备可见，请在电脑端添加“手机蓝牙键盘”"
            Toast.makeText(this, "请在电脑蓝牙设置中搜索“手机蓝牙键盘”", Toast.LENGTH_LONG).show()
        } else {
            connectionState.text = "未开启可见性，电脑可能无法发现手机"
        }
    }

    private val firmwareExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val asset = pendingFirmwareAsset
        pendingFirmwareAsset = null
        if (uri != null && asset != null) writeFirmwareAsset(uri, asset)
    }

    private val devices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var deviceAdapter: DeviceAdapter
    private var connecting = false
    private var pendingPhoneConnect = false

    private val unicodeModes = listOf(
        "Alt+X（推荐）",
        "十六进制",
        "十进制",
        "GBK"
    )
    private val unicodeValues = listOf(
        TypingEngine.MODE_ALTX,
        TypingEngine.MODE_HEX,
        TypingEngine.MODE_DECIMAL,
        TypingEngine.MODE_GBK
    )
    private val allowedProviders = LlmProviders.list.take(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal_settings)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        boardManager = BoardLink.get(this)
        bindViews()
        setupControls()
        KeyboardRuntime.addStateListener(phoneStateListener)
        requestBluetoothPermissions()
        loadSettings()
        refreshConnectionUi()
    }

    override fun onDestroy() {
        boardManager.stopScan()
        boardManager.onDeviceFound = null
        boardManager.onConnectionStateChanged = null
        boardManager.onScanStateChanged = null
        boardManager.onDataReceived = null
        boardManager.onError = null
        KeyboardRuntime.removeStateListener(phoneStateListener)
        super.onDestroy()
    }

    private fun bindViews() {
        terminalGroup = findViewById(R.id.terminalGroup)
        connectButton = findViewById(R.id.connectButton)
        connectionState = findViewById(R.id.connectionState)
        connectionHint = findViewById(R.id.connectionHint)
        outputIndicatorDot = findViewById(R.id.outputIndicatorDot)
        outputIndicatorTitle = findViewById(R.id.outputIndicatorTitle)
        outputIndicatorDetail = findViewById(R.id.outputIndicatorDetail)
        deviceList = findViewById(R.id.deviceList)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        speedValue = findViewById(R.id.speedValue)
        unicodeModeSpinner = findViewById(R.id.unicodeModeSpinner)
        modelProviderSpinner = findViewById(R.id.modelProviderSpinner)
        modelName = findViewById(R.id.modelName)
        apiTokenInput = findViewById(R.id.apiTokenInput)
        showTokenCheck = findViewById(R.id.showTokenCheck)
        testModelButton = findViewById(R.id.testModelButton)
        modelTestState = findViewById(R.id.modelTestState)
        boardFirmwareHelpButton = findViewById(R.id.boardFirmwareHelpButton)
        exportBoardFirmwareButton = findViewById(R.id.exportBoardFirmwareButton)

        radioByTerminal[TerminalRegistry.PHONE_HID] = R.id.terminalPhone
        radioByTerminal[TerminalRegistry.PICO_W] = R.id.terminalPico
        radioByTerminal[TerminalRegistry.ESP32_S3] = R.id.terminalEsp32
        radioByTerminal[TerminalRegistry.YD_ESP32_S3] = R.id.terminalYdEsp32
        radioByTerminal[TerminalRegistry.ESP32_LEGACY] = R.id.terminalEsp32Legacy

        deviceAdapter = DeviceAdapter()
        deviceList.adapter = deviceAdapter
    }

    private fun setupControls() {
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveSettings() }

        terminalGroup.setOnCheckedChangeListener { _, checkedId ->
            if (loading) return@setOnCheckedChangeListener
            currentTerminal = TerminalRegistry.byId(
                radioByTerminal.entries.firstOrNull { it.value == checkedId }?.key
            )
            devices.clear()
            deviceNames.clear()
            deviceAdapter.notifyDataSetChanged()
            deviceList.visibility = View.GONE
            if (currentTerminal.isBoard) {
                BoardLink.get(this).disconnect()
            }
            connecting = false
            pendingBoardDevice = null
            attemptingSavedAddress = false
            scanInProgress = false
            refreshConnectionUi()
        }

        connectButton.setOnClickListener { connectCurrentTerminal() }
        boardFirmwareHelpButton.setOnClickListener { showBoardFirmwareHelp() }
        exportBoardFirmwareButton.setOnClickListener { exportBoardFirmware() }

        val modeAdapter = ArrayAdapter(
            this,
            R.layout.item_spinner,
            R.id.spinnerText,
            unicodeModes
        )
        modeAdapter.setDropDownViewResource(R.layout.item_spinner)
        unicodeModeSpinner.adapter = modeAdapter

        val providerAdapter = ArrayAdapter(
            this,
            R.layout.item_spinner,
            R.id.spinnerText,
            allowedProviders.map { it.displayName }
        )
        providerAdapter.setDropDownViewResource(R.layout.item_spinner)
        modelProviderSpinner.adapter = providerAdapter
        modelProviderSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (loading) return
                val provider = allowedProviders.getOrElse(position) { allowedProviders[0] }
                modelName.text = "模型：${provider.defaultModel}"
                setModelTestState("未测试", R.color.text_secondary)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val level = progress.coerceIn(1, 10)
                speedValue.text = level.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        showTokenCheck.setOnCheckedChangeListener { _, checked ->
            apiTokenInput.inputType = if (checked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            apiTokenInput.setSelection(apiTokenInput.text.length)
        }

        apiTokenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!loading) setModelTestState("Token 已修改，尚未测试", R.color.clinical_warning)
            }
        })

        testModelButton.setOnClickListener { testModelConnection() }

        deviceList.setOnItemClickListener { _, _, position, _ ->
            val device = devices.getOrNull(position) ?: return@setOnItemClickListener
            pendingBoardDevice = device
            attemptingSavedAddress = false
            connecting = true
            connectButton.isEnabled = false
            connectButton.text = "连接中…"
            connectionState.text = "正在连接 ${deviceNames[position]}…"
            BoardLink.get(this).connect(device)
        }

        boardManager.onDeviceFound = { device, _, name ->
            runOnUiThread {
                if (!TerminalRegistry.matches(currentTerminal, device, name)) return@runOnUiThread
                if (devices.none { it.address == device.address }) {
                    devices += device
                    deviceNames += "${name ?: "外接键盘板"}\n${device.address}"
                    deviceAdapter.notifyDataSetChanged()
                    deviceList.visibility = View.VISIBLE
                    connectionState.text = "发现 ${devices.size} 个终端，点击选择连接"
                }
            }
        }
        boardManager.onConnectionStateChanged = { connected ->
            runOnUiThread {
                connecting = false
                scanInProgress = false
                refreshConnectionUi()
                if (connected) {
                    pendingBoardDevice?.let { device ->
                        TerminalRegistry.saveAddress(this, currentTerminal, device)
                    }
                    pendingBoardDevice = null
                    attemptingSavedAddress = false
                    OutputRouter.applySettingsToBoard(this)
                    Toast.makeText(this, "${currentTerminal.displayName} 已连接", Toast.LENGTH_SHORT).show()
                }
            }
        }
        boardManager.onScanStateChanged = { active ->
            runOnUiThread {
                scanInProgress = active
                if (active) {
                    connecting = true
                } else if (connecting && !boardManager.isConnected()) {
                    connecting = false
                }
                refreshConnectionUi()
                if (!active && !boardManager.isConnected()) {
                    connectionState.text = if (devices.isEmpty()) {
                        "未发现可连接的 ESP32-S3，请检查板子电源、固件和蓝牙权限"
                    } else {
                        "发现 ${devices.size} 个终端，点击下方设备连接"
                    }
                }
            }
        }
        boardManager.onDataReceived = { data ->
            LogStore.append("${currentTerminal.displayName}: $data")
        }
        boardManager.onError = { message ->
            runOnUiThread {
                val retryWithScan = attemptingSavedAddress
                connecting = false
                connectionState.text = message
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                if (retryWithScan) {
                    attemptingSavedAddress = false
                    pendingBoardDevice = null
                    TerminalRegistry.clearAddress(this, currentTerminal)
                    beginScan()
                    return@runOnUiThread
                }
                refreshConnectionUi()
            }
        }
    }

    private fun loadSettings() {
        loading = true
        currentTerminal = TerminalRegistry.selected(this)
        terminalGroup.check(radioByTerminal[currentTerminal.id] ?: R.id.terminalPhone)

        val speed = prefs.getInt(KEY_SPEED_LEVEL, 5).coerceIn(1, 10)
        speedSeekBar.progress = speed
        speedValue.text = speed.toString()

        val mode = prefs.getInt(KEY_UNICODE_MODE, TypingEngine.MODE_ALTX)
        unicodeModeSpinner.setSelection(unicodeValues.indexOf(mode).coerceAtLeast(0), false)

        val storedProviderId = prefs.getString(LlmPrefs.KEY_PROVIDER, "deepseek")
        val providerIndex = allowedProviders.indexOfFirst { it.id == storedProviderId }.coerceAtLeast(0)
        modelProviderSpinner.setSelection(providerIndex, false)
        val provider = allowedProviders[providerIndex]
        val savedModel = prefs.getString(LlmPrefs.KEY_MODEL, provider.defaultModel)
        modelName.text = "模型：${if (storedProviderId in allowedProviders.map { it.id }) savedModel else provider.defaultModel}"
        apiTokenInput.setText(prefs.getString(LlmPrefs.KEY_API_KEY, ""))
        showTokenCheck.isChecked = false
        setModelTestState(
            if (apiTokenInput.text.isNullOrBlank()) "未配置 API Token" else "已保存 Token，尚未重新测试",
            if (apiTokenInput.text.isNullOrBlank()) R.color.text_secondary else R.color.clinical_warning
        )
        loading = false
    }

    private fun saveSettings() {
        val terminal = currentTerminal
        val speed = speedSeekBar.progress.coerceIn(1, 10)
        val mode = unicodeValues.getOrElse(unicodeModeSpinner.selectedItemPosition) {
            TypingEngine.MODE_ALTX
        }
        val provider = allowedProviders.getOrElse(modelProviderSpinner.selectedItemPosition) {
            allowedProviders[0]
        }
        val token = apiTokenInput.text.toString().trim()
        prefs.edit()
            .putInt(KEY_SPEED_LEVEL, speed)
            .putInt(KEY_UNICODE_MODE, mode)
            .apply()
        TerminalRegistry.select(this, terminal)
        OutputRouter.select(this, terminal)
        KeyboardRuntime.hidProtocol()?.apply {
            setSpeed(speed)
            setUnicodeMode(mode)
        }
        if (token.isBlank()) {
            prefs.edit()
                .putString(LlmPrefs.KEY_PROVIDER, provider.id)
                .putString(LlmPrefs.KEY_MODEL, provider.defaultModel)
                .putString(LlmPrefs.KEY_API_KEY, "")
                .apply()
            setModelTestState("未配置 API Token，仅保存了键盘设置", R.color.clinical_warning)
            Toast.makeText(this, "键盘设置已保存；模型 Token 尚未配置", Toast.LENGTH_LONG).show()
            return
        }
        testModelConnection {
            prefs.edit()
                .putString(LlmPrefs.KEY_PROVIDER, provider.id)
                .putString(LlmPrefs.KEY_MODEL, provider.defaultModel)
                .putString(LlmPrefs.KEY_API_KEY, token)
                .apply()
            Toast.makeText(this, "模型连接验证成功，设置已保存", Toast.LENGTH_LONG).show()
        }
    }

    private fun testModelConnection(onSuccess: (() -> Unit)? = null) {
        val provider = allowedProviders.getOrElse(modelProviderSpinner.selectedItemPosition) {
            allowedProviders[0]
        }
        val token = apiTokenInput.text.toString().trim()
        if (token.isBlank()) {
            setModelTestState("请先填写 API Token", R.color.disconnected)
            Toast.makeText(this, "请先填写 API Token", Toast.LENGTH_SHORT).show()
            return
        }
        testModelButton.isEnabled = false
        testModelButton.text = "测试中…"
        setModelTestState("正在连接 ${provider.displayName} / ${provider.defaultModel}…", R.color.accent)
        lifecycleScope.launch {
            val result = runCatching {
                LlmClient.chat(
                    provider = provider,
                    apiKey = token,
                    model = provider.defaultModel,
                    messages = listOf(
                        "user" to "这是接口连通性测试。请只回复：连接成功"
                    )
                )
            }
            testModelButton.isEnabled = true
            testModelButton.text = "测试模型连接"
            result.onSuccess {
                setModelTestState(
                    "连接成功：${provider.displayName} / ${provider.defaultModel}",
                    R.color.connected
                )
                onSuccess?.invoke()
            }.onFailure { error ->
                setModelTestState(
                    "连接失败：${error.message ?: "请检查 Token、模型或网络"}",
                    R.color.disconnected
                )
                Toast.makeText(
                    this@TerminalSettingsActivity,
                    "模型连接失败，Token 未保存",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setModelTestState(message: String, colorRes: Int) {
        modelTestState.text = message
        modelTestState.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun beginScan() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissions()
            return
        }
        if (!boardManager.isBleAvailable()) {
            Toast.makeText(this, "请先开启手机蓝牙", Toast.LENGTH_SHORT).show()
            return
        }
        devices.clear()
        deviceNames.clear()
        deviceAdapter.notifyDataSetChanged()
        deviceList.visibility = View.VISIBLE
        connecting = true
        scanInProgress = true
        connectButton.isEnabled = false
        connectButton.text = "扫描中…"
        connectionState.text = "正在扫描 ${currentTerminal.displayName}…"
        connectionHint.text = "请让外接板通电并运行对应固件。已配对设备也会在扫描结果中出现。"
        boardManager.startScan()
        boardManager.bondedBoardDevices().forEach { device ->
            val name = runCatching { device.name }.getOrNull()
            if (TerminalRegistry.matches(currentTerminal, device, name)) {
                devices += device
                deviceNames += "${name ?: "已配对终端"}\n${device.address}"
            }
        }
        deviceAdapter.notifyDataSetChanged()
    }

    private fun refreshConnectionUi(snapshot: KeyboardSnapshot = KeyboardRuntime.snapshot()) {
        val terminal = currentTerminal
        if (terminal.isBoard) {
            val connected = OutputRouter.isConnected(this, terminal)
            connectionState.text = if (connected) "● 已连接" else "○ 未连接"
            connectionState.setTextColor(
                ContextCompat.getColor(this, if (connected) R.color.connected else R.color.clinical_warning)
            )
            connectionHint.text = if (connected) {
                "${terminal.displayName} 已就绪，保存设置后病例会通过该终端输出。"
            } else {
                "点击右侧“连接”，自动连接已保存的 ${terminal.displayName}；首次使用会扫描设备。"
            }
            connectButton.isEnabled = !connected && !connecting && !scanInProgress
            connectButton.text = when {
                connected -> "已连接"
                scanInProgress -> "扫描中…"
                connecting -> "连接中…"
                else -> "连接"
            }
            updateConnectionIndicator(connected, terminal, "")
        } else {
            val connected = snapshot.connected
            connectionState.text = when {
                connected -> "● 电脑已连接"
                connecting -> "正在主动连接电脑…"
                else -> "○ 键盘未连接电脑"
            }
            connectionState.setTextColor(
                ContextCompat.getColor(this, if (connected) R.color.connected else R.color.clinical_warning)
            )
            connectionHint.text = when {
                connected -> "已连接 ${snapshot.deviceName ?: "目标电脑"}，可以直接输出。"
                connecting -> "手机正在主动发起蓝牙 HID 连接，无需在电脑端重复点击连接。"
                else -> "点击“连接电脑”后，手机会主动连接已配对电脑。"
            }
            connectButton.isEnabled = !connected && !connecting
            connectButton.text = when {
                connected -> "已连接"
                connecting -> "连接中…"
                else -> "主动连接"
            }
            updateConnectionIndicator(connected, terminal, snapshot.deviceName.orEmpty())
        }
    }

    private fun connectCurrentTerminal() {
        if (!currentTerminal.isBoard) {
            connectPhoneComputer()
            return
        }
        if (OutputRouter.isConnected(this, currentTerminal)) {
            refreshConnectionUi()
            return
        }
        val savedAddress = TerminalRegistry.address(this, currentTerminal)
        if (savedAddress != null) {
            connecting = true
            attemptingSavedAddress = true
            pendingBoardDevice = null
            connectButton.isEnabled = false
            connectButton.text = "连接中…"
            connectionState.text = "正在连接已保存的 ${currentTerminal.displayName}…"
            if (!OutputRouter.connectSavedBoard(this, currentTerminal)) {
                connecting = false
                attemptingSavedAddress = false
                TerminalRegistry.clearAddress(this, currentTerminal)
                Toast.makeText(this, "无法使用已保存地址，开始扫描", Toast.LENGTH_SHORT).show()
                beginScan()
            }
        } else {
            beginScan()
        }
    }

    private fun showBoardFirmwareHelp() {
        val message = """
            ESP32-S3 不是直接与电脑蓝牙配对，而是使用：
            手机 → BLE 数据通道 → ESP32-S3 → USB HID → 电脑

            当前 App 已内置两种 ESP32-S3 Bridge 固件，可在选择板型后导出。

            设备需烧录 Bridge 固件，BLE 广播名通常为：
            · ESP32蓝牙键盘终端-0001

            0001 是该设备的唯一序列号，也会写入 USB 设备序列号。

            检查顺序：
            1. 板子已上电，未停留在下载模式；
            2. 原生 USB 口连接目标电脑，电脑识别为 HID 键盘；
            3. 手机已授予“附近的设备/蓝牙扫描与连接”权限；
            4. 在下方选择正确板型后点击“连接”；
            5. 如果系统蓝牙里绑定过旧设备，先取消绑定再重新扫描。

            注意：旧 BLE HID 固件与 Bridge 固件不是同一协议，旧 HID 版本不能用于当前 USB HID 桥接输出。
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("ESP32-S3 连接与固件说明")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun exportBoardFirmware() {
        val (asset, fileName) = when (currentTerminal.id) {
            TerminalRegistry.ESP32_S3 ->
                "firmware/ESP32-S3-SuperMini-0001-bridge-merged.bin" to
                    "ESP32-S3-SuperMini-0001-bridge-merged.bin"
            TerminalRegistry.YD_ESP32_S3 ->
                "firmware/YD-ESP32-S3-N16R8-0001-bridge-merged.bin" to
                    "YD-ESP32-S3-N16R8-0001-bridge-merged.bin"
            TerminalRegistry.ESP32_LEGACY -> {
                Toast.makeText(
                    this,
                    "旧 BLE HID 固件不能用于 USB HID 桥接，请选择 Super Mini 或 YD-N16R8",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            else -> {
                Toast.makeText(
                    this,
                    "请先选择 ESP32-S3 Super Mini 或 YD-ESP32-S3 N16R8",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        pendingFirmwareAsset = asset
        firmwareExporter.launch(fileName)
    }

    private fun writeFirmwareAsset(uri: android.net.Uri, asset: String) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    assets.open(asset).use { input ->
                        contentResolver.openOutputStream(uri)?.use { output ->
                            input.copyTo(output)
                        } ?: error("无法创建固件文件")
                    }
                }
            }.onSuccess {
                Toast.makeText(this@TerminalSettingsActivity, "ESP32-S3 固件已导出", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@TerminalSettingsActivity,
                    "固件导出失败：${error.message ?: "请检查存储权限"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun connectPhoneComputer() {
        KeyboardRuntime.initialize(this)
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissions()
            return
        }
        if (!KeyboardRuntime.isBluetoothOn()) {
            pendingPhoneConnect = true
            runCatching {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }.onFailure {
                pendingPhoneConnect = false
                Toast.makeText(this, "请到系统设置中开启蓝牙", Toast.LENGTH_LONG).show()
            }
            return
        }

        val pairs = KeyboardRuntime.pairRequests()
        if (pairs.isEmpty()) {
            showFirstPairingDialog()
            return
        }

        val lastAddress = KeyboardRuntime.lastComputerAddress()
        val lastComputer = pairs.firstOrNull { it.second.address == lastAddress }
        val target = lastComputer ?: pairs.singleOrNull()
        if (target != null) {
            beginActiveComputerConnect(target)
        } else {
            showPairedComputerDialog(pairs)
        }
    }

    private fun beginActiveComputerConnect(pair: Pair<String, BluetoothDevice>) {
        connecting = true
        connectButton.isEnabled = false
        connectButton.text = "连接中…"
        connectionState.text = "手机正在主动连接 ${pair.first}…"
        KeyboardRuntime.connectComputer(pair.second)
        window.decorView.postDelayed({
            if (connecting &&
                currentTerminal.id == TerminalRegistry.PHONE_HID &&
                !KeyboardRuntime.snapshot().connected
            ) {
                connecting = false
                refreshConnectionUi()
            }
        }, 8000)
    }

    private fun showPairedComputerDialog(pairs: List<Pair<String, BluetoothDevice>>) {
        val names = pairs.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("连接已配对电脑")
            .setMessage("选择电脑后，由手机主动发起蓝牙 HID 连接。")
            .setItems(names) { _, index ->
                pairs.getOrNull(index)?.let { beginActiveComputerConnect(it) }
            }
            .setNeutralButton("首次配对") { _, _ -> showFirstPairingDialog() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFirstPairingDialog() {
        AlertDialog.Builder(this)
            .setTitle("首次配对电脑")
            .setMessage(
                "当前没有已配对电脑。开启后，手机会作为“手机蓝牙键盘”保持可见；请在这台电脑的蓝牙设置中添加手机。配对完成后再点“连接电脑”，以后由手机主动连接。"
            )
            .setPositiveButton("让电脑发现手机") { _, _ -> startPhoneHidPairing() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateConnectionIndicator(
        connected: Boolean,
        terminal: TerminalDefinition,
        connectedName: String
    ) {
        val color = if (connected) R.color.connected else R.color.disconnected
        outputIndicatorDot.setTextColor(ContextCompat.getColor(this, color))
        outputIndicatorTitle.text = if (connected) {
            "${terminal.displayName} · 连接成功"
        } else {
            "${terminal.displayName} · 等待连接"
        }
        outputIndicatorDetail.text = when {
            connected && connectedName.isNotBlank() -> "已连接：$connectedName"
            connected -> "终端已就绪，可以输出病例"
            terminal.id == TerminalRegistry.PHONE_HID -> "选择电脑并完成蓝牙配对后变为绿色"
            else -> "扫描外接板并连接后变为绿色"
        }
    }

    private fun startPhoneHidPairing() {
        KeyboardRuntime.initialize(this)
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissions()
            return
        }
        if (!KeyboardRuntime.isBluetoothOn()) {
            runCatching {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }.onFailure {
                Toast.makeText(this, "请到系统设置中开启蓝牙", Toast.LENGTH_LONG).show()
            }
            return
        }
        beginPhoneHidRegistration()
    }

    private fun beginPhoneHidRegistration() {
        KeyboardRuntime.enable()
        window.decorView.postDelayed({
            runCatching {
                discoverableLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    }
                )
            }.onFailure {
                Toast.makeText(this, "请手动开启“对附近设备可见”", Toast.LENGTH_LONG).show()
            }
        }, 500)
    }

    private fun requestBluetoothPermissions() {
        if (hasBluetoothPermission()) return
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    private fun hasBluetoothPermission(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults.any { it != PackageManager.PERMISSION_GRANTED }
        ) {
            Toast.makeText(this, "需要蓝牙权限才能扫描外接终端", Toast.LENGTH_LONG).show()
        }
    }

    private inner class DeviceAdapter : BaseAdapter() {
        override fun getCount(): Int = devices.size
        override fun getItem(position: Int): BluetoothDevice = devices[position]
        override fun getItemId(position: Int): Long = devices[position].address.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@TerminalSettingsActivity)
                .inflate(R.layout.item_terminal_device, parent, false)
            val device = devices[position]
            val display = deviceNames[position]
            view.findViewById<TextView>(R.id.deviceName).text = display.substringBefore("\n")
            view.findViewById<TextView>(R.id.deviceAddress).text = display.substringAfter("\n", device.address)
            return view
        }
    }
}
