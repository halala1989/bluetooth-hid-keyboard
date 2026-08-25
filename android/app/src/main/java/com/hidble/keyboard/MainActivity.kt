package com.hidble.keyboard

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 主界面 - BLE HID 键盘 Android 客户端
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    // UI 组件
    private lateinit var statusText: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var textInput: EditText
    private lateinit var sendButton: Button
    private lateinit var commandLog: TextView
    
    // 功能按钮
    private lateinit var btnEnter: Button
    private lateinit var btnBackspace: Button
    private lateinit var btnTab: Button
    private lateinit var btnEscape: Button
    private lateinit var btnDelete: Button
    
    // 光标控制按钮
    private lateinit var btnUp: Button
    private lateinit var btnDown: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnHome: Button
    private lateinit var btnEnd: Button
    
    // 组合键按钮
    private lateinit var btnCtrlC: Button
    private lateinit var btnCtrlV: Button
    private lateinit var btnCtrlX: Button
    private lateinit var btnCtrlA: Button
    private lateinit var btnCtrlZ: Button
    private lateinit var btnCtrlS: Button
    
    // BLE 管理器
    private lateinit var bleManager: BleManager
    private lateinit var hidProtocol: HidProtocol
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initBle()
        setupListeners()
        requestPermissions()
    }
    
    private fun initViews() {
        // 连接状态
        statusText = findViewById(R.id.statusText)
        deviceNameText = findViewById(R.id.deviceNameText)
        scanButton = findViewById(R.id.scanButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        
        // 文本输入
        textInput = findViewById(R.id.textInput)
        sendButton = findViewById(R.id.sendButton)
        
        // 命令日志
        commandLog = findViewById(R.id.commandLog)
        
        // 功能按钮
        btnEnter = findViewById(R.id.btnEnter)
        btnBackspace = findViewById(R.id.btnBackspace)
        btnTab = findViewById(R.id.btnTab)
        btnEscape = findViewById(R.id.btnEscape)
        btnDelete = findViewById(R.id.btnDelete)
        
        // 光标控制
        btnUp = findViewById(R.id.btnUp)
        btnDown = findViewById(R.id.btnDown)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        btnHome = findViewById(R.id.btnHome)
        btnEnd = findViewById(R.id.btnEnd)
        
        // 组合键
        btnCtrlC = findViewById(R.id.btnCtrlC)
        btnCtrlV = findViewById(R.id.btnCtrlV)
        btnCtrlX = findViewById(R.id.btnCtrlX)
        btnCtrlA = findViewById(R.id.btnCtrlA)
        btnCtrlZ = findViewById(R.id.btnCtrlZ)
        btnCtrlS = findViewById(R.id.btnCtrlS)
        
        // 初始状态
        updateConnectionState(false)
    }
    
    private fun initBle() {
        bleManager = BleManager(this)
        hidProtocol = HidProtocol(bleManager)
        
        // 设置 BLE 回调
        bleManager.onConnectionStateChanged = { connected ->
            runOnUiThread {
                updateConnectionState(connected)
            }
        }
        
        bleManager.onDeviceFound = { device, rssi ->
            runOnUiThread {
                showDeviceDialog(device, rssi)
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
        // 扫描按钮
        scanButton.setOnClickListener {
            if (!bleManager.isBleAvailable()) {
                Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            appendLog("开始扫描...")
            bleManager.startScan()
        }
        
        // 断开按钮
        disconnectButton.setOnClickListener {
            bleManager.disconnect()
            appendLog("已断开连接")
        }
        
        // 发送按钮
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
        
        // 文本输入框 - 实时发送（可选）
        textInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // 功能按钮
        btnEnter.setOnClickListener { lifecycleScope.launch { hidProtocol.enter(); appendLog("Enter") } }
        btnBackspace.setOnClickListener { lifecycleScope.launch { hidProtocol.backspace(); appendLog("Backspace") } }
        btnTab.setOnClickListener { lifecycleScope.launch { hidProtocol.tab(); appendLog("Tab") } }
        btnEscape.setOnClickListener { lifecycleScope.launch { hidProtocol.escape(); appendLog("Escape") } }
        btnDelete.setOnClickListener { lifecycleScope.launch { hidProtocol.delete(); appendLog("Delete") } }
        
        // 光标控制
        btnUp.setOnClickListener { lifecycleScope.launch { hidProtocol.arrowUp(); appendLog("↑") } }
        btnDown.setOnClickListener { lifecycleScope.launch { hidProtocol.arrowDown(); appendLog("↓") } }
        btnLeft.setOnClickListener { lifecycleScope.launch { hidProtocol.arrowLeft(); appendLog("←") } }
        btnRight.setOnClickListener { lifecycleScope.launch { hidProtocol.arrowRight(); appendLog("→") } }
        btnHome.setOnClickListener { lifecycleScope.launch { hidProtocol.home(); appendLog("Home") } }
        btnEnd.setOnClickListener { lifecycleScope.launch { hidProtocol.end(); appendLog("End") } }
        
        // 组合键
        btnCtrlC.setOnClickListener { lifecycleScope.launch { hidProtocol.copy(); appendLog("Ctrl+C") } }
        btnCtrlV.setOnClickListener { lifecycleScope.launch { hidProtocol.paste(); appendLog("Ctrl+V") } }
        btnCtrlX.setOnClickListener { lifecycleScope.launch { hidProtocol.cut(); appendLog("Ctrl+X") } }
        btnCtrlA.setOnClickListener { lifecycleScope.launch { hidProtocol.selectAll(); appendLog("Ctrl+A") } }
        btnCtrlZ.setOnClickListener { lifecycleScope.launch { hidProtocol.undo(); appendLog("Ctrl+Z") } }
        btnCtrlS.setOnClickListener { lifecycleScope.launch { hidProtocol.save(); appendLog("Ctrl+S") } }
    }
    
    private fun showDeviceDialog(device: BluetoothDevice, rssi: Int) {
        val name = device.name ?: "未知设备"
        val address = device.address
        
        AlertDialog.Builder(this)
            .setTitle("发现设备")
            .setMessage("设备名称: $name\nMAC 地址: $address\n信号强度: $rssi dBm\n\n是否连接？")
            .setPositiveButton("连接") { _, _ ->
                appendLog("正在连接到 $name...")
                bleManager.connect(device)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun updateConnectionState(connected: Boolean) {
        if (connected) {
            statusText.text = "已连接"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.connected))
            scanButton.isEnabled = false
            disconnectButton.isEnabled = true
            textInput.isEnabled = true
            sendButton.isEnabled = true
            deviceNameText.text = "Pico HID Keyboard"
        } else {
            statusText.text = "未连接"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            scanButton.isEnabled = true
            disconnectButton.isEnabled = false
            textInput.isEnabled = false
            sendButton.isEnabled = false
            deviceNameText.text = "请扫描并连接设备"
        }
    }
    
    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message\n"
        
        commandLog.append(logEntry)
        
        // 限制日志行数
        val lines = commandLog.text.lines()
        if (lines.size > 50) {
            commandLog.text = lines.takeLast(50).joinToString("\n")
        }
        
        // 滚动到底部
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
