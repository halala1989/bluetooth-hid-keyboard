package com.hidble.keyboard

import android.Manifest
import android.bluetooth.BluetoothDevice
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

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    private lateinit var statusText: TextView
    private lateinit var statusDot: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var textInput: EditText
    private lateinit var sendButton: Button
    private lateinit var commandLog: TextView
    private lateinit var deviceList: ListView
    
    private lateinit var bleManager: BleManager
    private lateinit var hidProtocol: HidProtocol
    
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initBle()
        setupListeners()
        requestPermissions()
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        deviceNameText = findViewById(R.id.deviceNameText)
        scanButton = findViewById(R.id.scanButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        textInput = findViewById(R.id.textInput)
        sendButton = findViewById(R.id.sendButton)
        commandLog = findViewById(R.id.commandLog)
        
        // 设备列表
        deviceList = findViewById(R.id.deviceList)
        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        deviceList.adapter = deviceListAdapter
        
        updateConnectionState(false)
    }
    
    private fun initBle() {
        bleManager = BleManager(this)
        hidProtocol = HidProtocol(bleManager)
        
        bleManager.onConnectionStateChanged = { connected ->
            runOnUiThread {
                updateConnectionState(connected)
            }
        }
        
        bleManager.onDeviceFound = { device, rssi, name ->
            runOnUiThread {
                // 添加到设备列表
                if (!discoveredDevices.contains(device)) {
                    discoveredDevices.add(device)
                    val displayName = name ?: "未知设备"
                    val entry = "$displayName (${device.address}) RSSI: $rssi"
                    deviceNames.add(entry)
                    deviceListAdapter.notifyDataSetChanged()
                    appendLog("发现: $entry")
                }
            }
        }
        
        bleManager.onDataReceived = { data ->
            runOnUiThread {
                appendLog("收到: $data")
            }
        }
        
        bleManager.onError = { error ->
            runOnUiThread {
                appendLog("错误: $error")
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupListeners() {
        scanButton.setOnClickListener {
            if (!bleManager.isBleAvailable()) {
                Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 清空设备列表
            discoveredDevices.clear()
            deviceNames.clear()
            deviceListAdapter.notifyDataSetChanged()
            
            appendLog("开始扫描...")
            bleManager.startScan()
        }
        
        disconnectButton.setOnClickListener {
            bleManager.disconnect()
            appendLog("已断开连接")
        }
        
        sendButton.setOnClickListener {
            val text = textInput.text.toString()
            if (text.isNotEmpty()) {
                lifecycleScope.launch {
                    hidProtocol.typeText(text)
                    appendLog("发送文本: $text")
                    textInput.text.clear()
                }
            }
        }

        // 中文输入模式
        findViewById<Button>(R.id.btnModeAltX).setOnClickListener {
            lifecycleScope.launch { hidProtocol.setUnicodeMode(3) }
            appendLog("中文输入模式: Alt+X（默认，记事本/Word 等，无需注册表）")
        }
        findViewById<Button>(R.id.btnModeHex).setOnClickListener {
            lifecycleScope.launch { hidProtocol.setUnicodeMode(1) }
            appendLog("中文输入模式: 十六进制（Alt+Numpad+，需 EnableHexNumpad+NumLock，记事本无效）")
        }
        findViewById<Button>(R.id.btnModeDecimal).setOnClickListener {
            lifecycleScope.launch { hidProtocol.setUnicodeMode(0) }
            appendLog("中文输入模式: 十进制（Alt+0+码点，记事本/Word 等）")
        }
        findViewById<Button>(R.id.btnModeGbk).setOnClickListener {
            lifecycleScope.launch { hidProtocol.setUnicodeMode(2) }
            appendLog("中文输入模式: GBK 机内码（仅中文版 Windows）")
        }
        
        // 设备列表点击事件
        deviceList.setOnItemClickListener { _, _, position, _ ->
            if (position < discoveredDevices.size) {
                val device = discoveredDevices[position]
                val name = deviceNames[position]
                
                AlertDialog.Builder(this)
                    .setTitle("连接设备")
                    .setMessage("确定要连接到 $name 吗？")
                    .setPositiveButton("连接") { _, _ ->
                        appendLog("正在连接到 $name...")
                        bleManager.connect(device)
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
    
    private fun updateConnectionState(connected: Boolean) {
        if (connected) {
            statusText.text = "已连接"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.connected))
            statusDot.setTextColor(ContextCompat.getColor(this, R.color.connected))
            scanButton.isEnabled = false
            disconnectButton.isEnabled = true
            deviceNameText.text = "Pico HID Keyboard"
        } else {
            statusText.text = "未连接"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            statusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            scanButton.isEnabled = true
            disconnectButton.isEnabled = false
            deviceNameText.text = "请扫描并连接设备"
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
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
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
        bleManager.cleanup()
    }
}
