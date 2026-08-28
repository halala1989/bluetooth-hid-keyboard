package com.hidble.phonekeyboard

import android.bluetooth.BluetoothDevice
import android.os.Bundle
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
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        if (MainActivity.instance?.connectionActivity === this) {
            MainActivity.instance?.connectionActivity = null
        }
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
    }
}
