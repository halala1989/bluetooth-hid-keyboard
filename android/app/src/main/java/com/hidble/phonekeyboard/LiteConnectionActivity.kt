package com.hidble.phonekeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LiteConnectionActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 3201
        private const val SCAN_DURATION_MS = 12_000L
    }

    private data class ScanDevice(
        val device: BluetoothDevice,
        var name: String?,
        var rssi: Int
    )

    private lateinit var phoneDot: TextView
    private lateinit var phoneStatus: TextView
    private lateinit var phoneDetail: TextView
    private lateinit var phoneButton: Button
    private lateinit var boardSpinner: Spinner
    private lateinit var boardStatus: TextView
    private lateinit var scanButton: Button
    private lateinit var emptyText: TextView
    private lateinit var deviceList: ListView

    private val handler = Handler(Looper.getMainLooper())
    private val boardOptions = TerminalRegistry.all.filter { it.isBoard }
    private val devices = linkedMapOf<String, ScanDevice>()
    private lateinit var deviceAdapter: ScanDeviceAdapter
    private lateinit var boardManager: BoardBleManager
    private var suppressBoardSelection = true
    private var scanning = false

    private val phoneStateListener: (KeyboardSnapshot) -> Unit = {
        runOnUiThread { refreshPhoneHid() }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && KeyboardRuntime.isBluetoothOn()) {
            KeyboardRuntime.enable()
            handler.postDelayed({ showPhonePairingChoices() }, 600)
        } else {
            Toast.makeText(this, "需要开启手机蓝牙", Toast.LENGTH_SHORT).show()
            refreshPhoneHid()
        }
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(
                this,
                "请在电脑蓝牙设置中搜索“${HidDeviceManager.KEYBOARD_NAME}”并完成配对",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "未开启可发现状态，电脑可能搜索不到手机", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lite_connection)
        KeyboardRuntime.initialize(this)
        boardManager = BoardLink.get(this)

        phoneDot = findViewById(R.id.litePhoneHidDot)
        phoneStatus = findViewById(R.id.litePhoneHidStatus)
        phoneDetail = findViewById(R.id.litePhoneHidDetail)
        phoneButton = findViewById(R.id.litePhoneHidButton)
        boardSpinner = findViewById(R.id.liteBoardSpinner)
        boardStatus = findViewById(R.id.liteBoardStatus)
        scanButton = findViewById(R.id.liteBoardScanButton)
        emptyText = findViewById(R.id.liteBoardEmptyText)
        deviceList = findViewById(R.id.liteBoardDeviceList)

        findViewById<Button>(R.id.liteConnectionBackButton).setOnClickListener { finish() }
        phoneButton.setOnClickListener { preparePhoneHidPairing() }
        scanButton.setOnClickListener { askToScanBoard() }
        setupBoardSelector()
        setupDeviceList()
        setupBoardCallbacks()
        requestBluetoothPermissions()
    }

    override fun onResume() {
        super.onResume()
        KeyboardRuntime.refreshConnection()
        KeyboardRuntime.addStateListener(phoneStateListener)
        refreshPhoneHid()
        refreshBoardStatus()
    }

    override fun onPause() {
        super.onPause()
        KeyboardRuntime.removeStateListener(phoneStateListener)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        boardManager.stopScan()
        boardManager.onDeviceFound = null
        boardManager.onScanStateChanged = null
        boardManager.onConnectionStateChanged = null
        boardManager.onError = null
        super.onDestroy()
    }

    private fun setupBoardSelector() {
        val labels = boardOptions.map { terminal ->
            "${terminal.displayName}\n${terminal.description}"
        }
        val adapter = ArrayAdapter(this, R.layout.item_spinner, R.id.spinnerText, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        boardSpinner.adapter = adapter
        val selected = TerminalRegistry.selected(this)
        val selectedIndex = boardOptions.indexOfFirst { it.id == selected.id }
            .takeIf { it >= 0 }
            ?: 0
        suppressBoardSelection = true
        boardSpinner.setSelection(selectedIndex, false)
        suppressBoardSelection = false
        boardSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressBoardSelection) return
                val terminal = boardOptions.getOrElse(position) { boardOptions.first() }
                OutputRouter.select(this@LiteConnectionActivity, terminal)
                refreshBoardStatus()
                askToScanBoard()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupDeviceList() {
        deviceAdapter = ScanDeviceAdapter()
        deviceList.adapter = deviceAdapter
        deviceList.setOnItemClickListener { _, _, position, _ ->
            val entry = devices.values.toList().getOrNull(position) ?: return@setOnItemClickListener
            val terminal = selectedBoard()
            OutputRouter.connectBoard(this, terminal, entry.device)
            boardStatus.text = "正在连接 ${displayName(entry)}\nMAC：${entry.device.address}"
            boardStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
            boardManager.stopScan()
        }
    }

    private fun setupBoardCallbacks() {
        boardManager.onDeviceFound = { device, rssi, name ->
            runOnUiThread {
                val existing = devices[device.address]
                if (existing == null) {
                    devices[device.address] = ScanDevice(device, name, rssi)
                } else {
                    if (!name.isNullOrBlank()) existing.name = name
                    existing.rssi = rssi
                }
                deviceAdapter.notifyDataSetChanged()
                updateDeviceListVisibility()
            }
        }
        boardManager.onScanStateChanged = { active ->
            runOnUiThread {
                scanning = active
                scanButton.isEnabled = !active
                scanButton.text = if (active) "扫描中…（显示全部蓝牙设备）" else "扫描附近全部蓝牙设备"
                if (!active) {
                    updateDeviceListVisibility()
                    if (devices.isEmpty()) {
                        boardStatus.text = "扫描结束：附近没有发现蓝牙设备"
                        boardStatus.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
                    } else {
                        boardStatus.text = "扫描结束：发现 ${devices.size} 个蓝牙设备"
                        boardStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    }
                }
            }
        }
        boardManager.onConnectionStateChanged = { connected ->
            runOnUiThread {
                if (connected) {
                    val name = BoardLink.connectedName() ?: selectedBoard().displayName
                    val address = BoardLink.connectedAddress().orEmpty()
                    boardStatus.text = "● 已连接：$name\nMAC：$address"
                    boardStatus.setTextColor(ContextCompat.getColor(this, R.color.connected))
                    scanButton.isEnabled = true
                    Toast.makeText(this, "开发板连接成功", Toast.LENGTH_SHORT).show()
                } else {
                    refreshBoardStatus()
                }
            }
        }
        boardManager.onError = { message ->
            runOnUiThread {
                boardStatus.text = "连接错误：$message"
                boardStatus.setTextColor(ContextCompat.getColor(this, R.color.clinical_danger))
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectedBoard(): TerminalDefinition =
        boardOptions.getOrElse(boardSpinner.selectedItemPosition) { boardOptions.first() }

    private fun askToScanBoard() {
        if (scanning) return
        val terminal = selectedBoard()
        if (boardSpinner.selectedItemPosition < 0) return
        AlertDialog.Builder(this)
            .setTitle("扫描附近蓝牙设备？")
            .setMessage(
                "将使用 ${terminal.displayName} 协议扫描周围全部 BLE 设备。" +
                    "扫描结果会显示设备的实际广播名称和 MAC 地址。"
            )
            .setPositiveButton("扫描") { _, _ -> startBoardScan() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startBoardScan() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissions()
            return
        }
        if (!boardManager.isBleAvailable()) {
            Toast.makeText(this, "请先在手机系统设置中开启蓝牙", Toast.LENGTH_SHORT).show()
            return
        }
        devices.clear()
        deviceAdapter.notifyDataSetChanged()
        deviceList.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = "正在扫描附近全部蓝牙设备…"
        boardStatus.text = "正在扫描，请保持开发板通电并处于广播状态"
        boardStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
        addBondedDevices()
        boardManager.startScan()
        handler.postDelayed({
            if (scanning) boardManager.stopScan()
        }, SCAN_DURATION_MS)
    }

    private fun addBondedDevices() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        try {
            adapter.bondedDevices.orEmpty().forEach { device ->
                if (!devices.containsKey(device.address)) {
                    devices[device.address] = ScanDevice(
                        device,
                        runCatching { device.name }.getOrNull(),
                        0
                    )
                }
            }
            deviceAdapter.notifyDataSetChanged()
            updateDeviceListVisibility()
        } catch (_: SecurityException) {
            // Permission result is handled by the scan permission prompt.
        }
    }

    private fun updateDeviceListVisibility() {
        val hasDevices = devices.isNotEmpty()
        deviceList.visibility = if (hasDevices) View.VISIBLE else View.GONE
        emptyText.visibility = if (hasDevices) View.GONE else View.VISIBLE
        if (!hasDevices) emptyText.text = if (scanning) "正在扫描…" else "没有发现蓝牙设备"
    }

    private fun preparePhoneHidPairing() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissions()
            return
        }
        if (!KeyboardRuntime.isBluetoothOn()) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        KeyboardRuntime.enable()
        handler.postDelayed({ showPhonePairingChoices() }, 500)
    }

    private fun showPhonePairingChoices() {
        val pairs = KeyboardRuntime.pairRequests().filterNot { (_, device) ->
            boardOptions.any { terminal -> TerminalRegistry.matches(terminal, device, null) }
        }
        if (pairs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("首次适配电脑")
                .setMessage(
                    "当前没有已配对电脑。开启手机可发现状态后，" +
                        "请到电脑蓝牙设置中搜索“${HidDeviceManager.KEYBOARD_NAME}”并完成配对。"
                )
                .setPositiveButton("让电脑发现手机") { _, _ -> makePhoneDiscoverable() }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val labels = pairs.map { (name, device) -> "$name\nMAC：${device.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("连接已配对电脑")
            .setMessage("选择电脑后，由手机主动发起蓝牙 HID 连接。")
            .setItems(labels) { _, index ->
                val device = pairs[index].second
                KeyboardRuntime.connectComputer(device)
                phoneDetail.text = "正在主动连接 ${pairs[index].first}"
            }
            .setNeutralButton("让电脑发现手机") { _, _ -> makePhoneDiscoverable() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun makePhoneDiscoverable() {
        KeyboardRuntime.enable()
        handler.postDelayed({
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

    private fun refreshPhoneHid() {
        if (!::phoneStatus.isInitialized) return
        val snapshot = KeyboardRuntime.snapshot()
        when {
            snapshot.connected -> {
                phoneDot.setTextColor(ContextCompat.getColor(this, R.color.connected))
                phoneStatus.text = "● 已连接电脑"
                phoneDetail.text = listOfNotNull(
                    snapshot.deviceName ?: "目标电脑",
                    KeyboardRuntime.lastComputerAddress()?.let { "MAC：$it" }
                ).joinToString("\n")
                phoneButton.text = "重新选择电脑 / 重新配对"
            }
            snapshot.registered -> {
                phoneDot.setTextColor(ContextCompat.getColor(this, R.color.accent))
                phoneStatus.text = "● 蓝牙键盘已启动"
                phoneDetail.text = "等待电脑连接，或从已配对电脑列表中主动连接"
                phoneButton.text = "连接已配对电脑"
            }
            else -> {
                phoneDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                phoneStatus.text = "蓝牙键盘未启动"
                phoneDetail.text = "点击下方按钮启动键盘并完成电脑适配"
                phoneButton.text = "启动键盘 / 配对电脑"
            }
        }
    }

    private fun refreshBoardStatus() {
        if (!::boardStatus.isInitialized) return
        val terminal = selectedBoard()
        if (OutputRouter.isConnected(this, terminal)) {
            val name = BoardLink.connectedName() ?: terminal.displayName
            val address = BoardLink.connectedAddress().orEmpty()
            boardStatus.text = "● 已连接：$name\nMAC：$address"
            boardStatus.setTextColor(ContextCompat.getColor(this, R.color.connected))
        } else {
            boardStatus.text = "已选择：${terminal.displayName}\n选择后可以扫描附近全部蓝牙设备"
            boardStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun displayName(entry: ScanDevice): String =
        entry.name?.takeIf { it.isNotBlank() } ?: "未命名蓝牙设备"

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                denied.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private inner class ScanDeviceAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@LiteConnectionActivity)

        override fun getCount(): Int = devices.size
        override fun getItem(position: Int): ScanDevice = devices.values.toList()[position]
        override fun getItemId(position: Int): Long = getItem(position).device.address.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(
                R.layout.item_lite_bluetooth_device,
                parent,
                false
            )
            val entry = getItem(position)
            view.findViewById<TextView>(R.id.liteDeviceName).text = displayName(entry)
            view.findViewById<TextView>(R.id.liteDeviceAddress).text = buildString {
                append("MAC：${entry.device.address}")
                if (entry.rssi != 0) append(" · ${entry.rssi} dBm")
            }
            return view
        }
    }
}
