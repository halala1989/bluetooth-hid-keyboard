package com.hidble.phonekeyboard

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 二级页面：连接管理（ESP32-S3 专用版）
 *
 * 只管理“外接键盘板”：
 *   - 扫描并连接 ESP32-S3 Keyboard
 *   - 显示板子连接状态（未连接 / 已连接）
 *   - 断开
 * 不再显示/管理“手机模拟蓝牙键盘”和“已配对电脑”，也不检查手机是否连接电脑。
 */
class ConnectionActivity : AppCompatActivity() {

    private lateinit var boardStatusText: TextView
    private lateinit var boardScanButton: Button
    private lateinit var boardDisconnectButton: Button
    private lateinit var boardManager: BoardBleManager

    private val boardHandler = Handler(Looper.getMainLooper())
    private val foundBoards = mutableListOf<Triple<BluetoothDevice, Int, String?>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        boardStatusText = findViewById(R.id.boardStatusText)
        boardScanButton = findViewById(R.id.boardScanButton)
        boardDisconnectButton = findViewById(R.id.boardDisconnectButton)
        boardManager = BoardLink.get(this)

        boardScanButton.setOnClickListener { startBoardScan() }
        boardDisconnectButton.setOnClickListener {
            boardManager.disconnect()
            LogStore.append("已断开外接键盘板")
            refreshBoardStatus(false)
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
        refreshBoardStatus(boardManager.isConnected())
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

    /** 刷新板子连接状态（MainActivity 也会调用） */
    fun refreshAll() {
        refreshBoardStatus(boardManager.isConnected())
    }

    private fun refreshBoardStatus(connected: Boolean) {
        if (!::boardStatusText.isInitialized) return
        if (connected) {
            boardStatusText.text = "已连接：ESP32-S3 Keyboard（“发送到键盘”会直接输入到电脑）"
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
        boardStatusText.setTextColor(ContextCompat.getColor(this, R.color.accent))
        boardScanButton.isEnabled = false
        boardManager.startScan()
        boardHandler.postDelayed({
            boardManager.stopScan()
            boardScanButton.isEnabled = true
            showBoardDeviceDialog()
        }, 8000)
    }

    private fun showBoardDeviceDialog() {
        // 扫描结果 + 手机系统蓝牙里已配对的板子（板子被系统连着时不广播，扫不到但可直接连）
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
}