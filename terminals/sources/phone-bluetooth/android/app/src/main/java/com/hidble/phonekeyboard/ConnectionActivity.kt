package com.hidble.phonekeyboard

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

/**
 * 二级页面：连接管理（模拟蓝牙键盘开关 + 状态 + 已配对设备）。
 * 状态与操作都转发给 MainActivity（HID 引擎唯一实例在 MainActivity）。
 */
class ConnectionActivity : AppCompatActivity() {

    private lateinit var keyboardSwitch: SwitchCompat
    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var deviceList: ListView

    // 外接键盘板（ESP32-S3）
    private lateinit var boardStatusText: TextView
    private lateinit var boardScanButton: android.widget.Button
    private lateinit var boardDisconnectButton: android.widget.Button
    private lateinit var boardManager: BoardBleManager
    private val boardHandler = Handler(Looper.getMainLooper())
    private val foundBoards = mutableListOf<Triple<BluetoothDevice, Int, String?>>()

    private val deviceNames = mutableListOf<String>()
    private val bondedDevices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceListAdapter: ArrayAdapter<String>

    /** 抑制开关回调（刷新状态时避免触发 MainActivity 逻辑） */
    private var suppressSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        keyboardSwitch = findViewById(R.id.keyboardSwitch)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        deviceNameText = findViewById(R.id.deviceNameText)
        deviceList = findViewById(R.id.deviceList)

        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        deviceList.adapter = deviceListAdapter

        // 外接键盘板
        boardStatusText = findViewById(R.id.boardStatusText)
        boardScanButton = findViewById(R.id.boardScanButton)
        boardDisconnectButton = findViewById(R.id.boardDisconnectButton)
        boardManager = BoardLink.get(this)
        boardScanButton.setOnClickListener { startBoardScan() }
        boardDisconnectButton.setOnClickListener {
            boardManager.disconnect()
            LogStore.append("已断开外接键盘板")
        }

        keyboardSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitch) return@setOnCheckedChangeListener
            MainActivity.instance?.setKeyboardEnabled(checked)
        }

        deviceList.setOnItemClickListener { _, _, position, _ ->
            if (position < bondedDevices.size) {
                val device = bondedDevices[position]
                val name = deviceNames[position]
                AlertDialog.Builder(this)
                    .setTitle("连接设备")
                    .setMessage("确定要连接到 $name 吗？")
                    .setPositiveButton("连接") { _, _ ->
                        LogStore.append("正在连接 $name...")
                        val main = MainActivity.instance
                        if (main == null) {
                            LogStore.append("主界面未运行，请先回到主界面")
                            Toast.makeText(this, "主界面未运行，请先回到主界面", Toast.LENGTH_SHORT).show()
                        } else {
                            main.connectToDevice(device)
                            Toast.makeText(this, "正在连接 $name...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MainActivity.instance?.connectionActivity = this
        boardManager.onConnectionStateChanged = { connected -> refreshBoardStatus(connected) }
        boardManager.onDeviceFound = { device, rssi, name ->
            if (foundBoards.none { it.first.address == device.address }) {
                foundBoards.add(Triple(device, rssi, name))
            }
            boardStatusText.text = "扫描中…已发现 ${foundBoards.size} 个设备"
            boardStatusText.setTextColor(ContextCompat.getColor(this, R.color.accent))
        }
        boardManager.onDataReceived = { data ->
            if (data.startsWith("ERR") || data.startsWith("STATUS")) LogStore.append("外接板: $data")
        }
        boardManager.onError = { err ->
            LogStore.append("外接板错误: $err")
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
        }
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        if (MainActivity.instance?.connectionActivity === this) {
            MainActivity.instance?.connectionActivity = null
        }
        // 页面离开后清掉回调，避免持有 Activity
        boardManager.onConnectionStateChanged = null
        boardManager.onDeviceFound = null
        boardManager.onDataReceived = null
        boardManager.onError = null
    }

    // ===== 外接键盘板（ESP32-S3）=====

    private fun refreshBoardStatus(connected: Boolean) {
        if (!::boardStatusText.isInitialized) return
        if (connected) {
            // 连接后同步 App 当前的速度档与中文输入模式到板子
            val p = getSharedPreferences("hidble_prefs", MODE_PRIVATE)
            boardManager.setSpeed(p.getInt("speed_level", 5))
            boardManager.setUnicodeMode(p.getInt("unicode_mode", 3))
            boardStatusText.text = "已连接：ESP32-S3 Keyboard（“发送到键盘”将走板子输出）"
            boardStatusText.setTextColor(ContextCompat.getColor(this, R.color.connected))
            boardDisconnectButton.isEnabled = true
            boardScanButton.isEnabled = false
        } else {
            boardStatusText.text = "未连接：点下面按钮扫描并连接 ESP32-S3 Keyboard"
            boardStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            boardDisconnectButton.isEnabled = false
            boardScanButton.isEnabled = true
        }
    }

    private fun startBoardScan() {
        if (!boardManager.isBleAvailable()) {
            Toast.makeText(this, "蓝牙不可用或未开启", Toast.LENGTH_SHORT).show()
            return
        }
        foundBoards.clear()
        boardStatusText.text = "扫描中…"
        boardScanButton.isEnabled = false
        boardManager.startScan()
        boardHandler.postDelayed({
            boardManager.stopScan()
            boardScanButton.isEnabled = true
            showBoardDeviceDialog()
        }, 8000)
    }

    private fun showBoardDeviceDialog() {
        // 扫描结果 + 手机系统蓝牙里已配对的板子。
        // 板子若已被系统连着，就不再广播，扫描扫不到；但已配对设备可以直接连。
        val candidates = mutableListOf<Triple<BluetoothDevice, Int, String?>>()
        candidates.addAll(foundBoards)
        for (d in boardManager.bondedBoardDevices()) {
            if (candidates.none { it.first.address == d.address }) {
                val n = try { d.name } catch (_: SecurityException) { null }
                candidates.add(Triple(d, 0, n))
            }
        }
        if (candidates.isEmpty()) {
            Toast.makeText(
                this,
                "没扫到设备，也没找到已配对的板子。请检查：①App 的“附近设备”权限是否允许；②板子已上电且在广播；③若已在手机系统蓝牙里连过它，先在系统设置里取消连接再试",
                Toast.LENGTH_LONG
            ).show()
            refreshBoardStatus(boardManager.isConnected())
            return
        }
        val labels = candidates.map { (device, rssi, name) ->
            val tag = if (rssi != 0) "$rssi dBm" else "已配对/可直接连"
            "${name ?: "未知设备"}  ($tag)\n${device.address}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择外接键盘板")
            .setItems(labels) { _, which ->
                val (device, _, name) = candidates[which]
                LogStore.append("正在连接外接板：${name ?: device.address}…")
                boardManager.connect(device)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    /** 从 MainActivity 拉取最新状态刷新本页（含从连接页返回、主界面状态变化时） */
    fun refreshAll() {
        val main = MainActivity.instance ?: run {
            suppressSwitch = true
            keyboardSwitch.isChecked = false
            suppressSwitch = false
            statusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            statusText.text = "未启动"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
            deviceNameText.text = "主界面未运行，请返回主界面操作"
            deviceNames.clear()
            bondedDevices.clear()
            deviceListAdapter.notifyDataSetChanged()
            return
        }

        suppressSwitch = true
        keyboardSwitch.isChecked = main.isRegistered()
        suppressSwitch = false

        val connected = main.isConnected()
        when {
            connected -> {
                statusText.text = "已连接"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.connected))
                statusDot.setTextColor(ContextCompat.getColor(this, R.color.connected))
                deviceNameText.text = "已连接到电脑：${main.connectedDeviceName() ?: "电脑"}，可以直接输入文字发送；关闭开关可断开。"
            }
            main.isRegistered() -> {
                statusText.text = "键盘已启动"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.accent))
                statusDot.setTextColor(ContextCompat.getColor(this, R.color.accent))
                deviceNameText.text = "手机已模拟为蓝牙键盘：到电脑上 设置 → 蓝牙 → 添加设备，搜索“${HidDeviceManager.KEYBOARD_NAME}”并配对。"
            }
            else -> {
                statusText.text = "未启动"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                statusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                deviceNameText.text = "打开上方开关后，手机会自动开启蓝牙并模拟成蓝牙键盘，到电脑上搜索即可。"
            }
        }

        bondedDevices.clear()
        deviceNames.clear()
        main.getBondedDevices().forEach { (name, device) ->
            bondedDevices.add(device)
            deviceNames.add(name)
        }
        deviceListAdapter.notifyDataSetChanged()
        refreshBoardStatus(boardManager.isConnected())
    }
}
