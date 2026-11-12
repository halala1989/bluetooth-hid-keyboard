package com.hidble.phonekeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LiteConnectionActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 3201
        private const val SCAN_DURATION_MS = 15_000L
    }

    private data class ScanDevice(
        val device: BluetoothDevice,
        var name: String?,
        var rssi: Int
    )

    private lateinit var statusDot: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var reconnectButton: Button
    private lateinit var scanPairButton: Button
    private lateinit var scanState: TextView
    private lateinit var emptyText: TextView
    private lateinit var deviceList: ListView

    private val handler = Handler(Looper.getMainLooper())
    private val devices = linkedMapOf<String, ScanDevice>()
    private lateinit var deviceAdapter: UnifiedDeviceAdapter
    private lateinit var boardManager: BoardBleManager
    private var scanning = false
    private var autoReconnectAttempted = false
    private var pendingComputerAddress: String? = null

    private val phoneStateListener: (KeyboardSnapshot) -> Unit = { snapshot ->
        runOnUiThread {
            if (snapshot.connected) {
                val address = KeyboardRuntime.lastComputerAddress()
                val remembered = LiteConnectionMemory.load(this)
                val name = snapshot.deviceName
                    ?: remembered?.takeIf { it.address == address }?.name
                    ?: "已配对电脑"
                if (!address.isNullOrBlank()) {
                    LiteConnectionMemory.save(
                        this,
                        LiteConnectionMemory.KIND_COMPUTER,
                        address,
                        name
                    )
                }
                refreshUnifiedStatus()
            } else if (LiteConnectionMemory.load(this)?.kind == LiteConnectionMemory.KIND_COMPUTER) {
                refreshUnifiedStatus()
            }
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.bluetoothDevice() ?: return
                    val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                    addDevice(device, name, rssi)
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.bluetoothDevice() ?: return
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    if (
                        state == BluetoothDevice.BOND_BONDED &&
                        pendingComputerAddress == device.address
                    ) {
                        pendingComputerAddress = null
                        connectComputer(device)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    updateScanText()
                }
            }
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "蓝牙已开启，请再次点击操作", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要开启蓝牙才能扫描和连接", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lite_connection)
        KeyboardRuntime.initialize(this)
        boardManager = BoardLink.get(this)

        statusDot = findViewById(R.id.liteUnifiedStatusDot)
        statusTitle = findViewById(R.id.liteUnifiedStatus)
        statusDetail = findViewById(R.id.liteUnifiedDetail)
        reconnectButton = findViewById(R.id.liteReconnectButton)
        scanPairButton = findViewById(R.id.liteScanPairButton)
        scanState = findViewById(R.id.liteUnifiedScanState)
        emptyText = findViewById(R.id.liteUnifiedEmptyText)
        deviceList = findViewById(R.id.liteUnifiedDeviceList)

        findViewById<Button>(R.id.liteConnectionBackButton).setOnClickListener { finish() }
        reconnectButton.setOnClickListener { reconnectRemembered(false) }
        scanPairButton.setOnClickListener { startUnifiedScan() }

        deviceAdapter = UnifiedDeviceAdapter()
        deviceList.adapter = deviceAdapter
        deviceList.setOnItemClickListener { _, _, position, _ ->
            val entry = devices.values.toList().getOrNull(position) ?: return@setOnItemClickListener
            stopUnifiedScan()
            connectSelectedDevice(entry)
        }
        setupBoardCallbacks()
        registerDiscoveryReceiver()
        requestBluetoothPermissions()
        refreshUnifiedStatus()
    }

    override fun onResume() {
        super.onResume()
        KeyboardRuntime.refreshConnection()
        KeyboardRuntime.addStateListener(phoneStateListener)
        refreshUnifiedStatus()
        if (!autoReconnectAttempted && LiteConnectionMemory.load(this) != null) {
            autoReconnectAttempted = true
            handler.postDelayed({ reconnectRemembered(true) }, 350)
        }
    }

    override fun onPause() {
        super.onPause()
        KeyboardRuntime.removeStateListener(phoneStateListener)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopUnifiedScan()
        runCatching { unregisterReceiver(discoveryReceiver) }
        boardManager.onDeviceFound = null
        boardManager.onScanStateChanged = null
        boardManager.onConnectionStateChanged = null
        boardManager.onError = null
        super.onDestroy()
    }

    private fun setupBoardCallbacks() {
        boardManager.onDeviceFound = { device, rssi, name ->
            runOnUiThread { addDevice(device, name, rssi) }
        }
        boardManager.onConnectionStateChanged = { connected ->
            runOnUiThread {
                if (connected) {
                    val address = BoardLink.connectedAddress().orEmpty()
                    val name = BoardLink.connectedName() ?: "已连接开发板"
                    if (address.isNotBlank()) {
                        LiteConnectionMemory.save(
                            this,
                            LiteConnectionMemory.KIND_BOARD,
                            address,
                            name
                        )
                        TerminalRegistry.select(
                            this,
                            TerminalRegistry.byId(TerminalRegistry.ESP32_S3)
                        )
                    }
                    Toast.makeText(this, "连接成功", Toast.LENGTH_SHORT).show()
                    refreshUnifiedStatus()
                } else {
                    refreshUnifiedStatus()
                }
            }
        }
        boardManager.onError = { message ->
            runOnUiThread {
                statusTitle.text = "连接失败"
                statusDetail.text = message
                statusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startUnifiedScan() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        if (scanning) return
        devices.clear()
        deviceAdapter.notifyDataSetChanged()
        deviceList.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = "正在扫描附近全部蓝牙设备…"
        scanState.text = "扫描中：电脑和开发板会出现在同一个列表"
        scanState.setTextColor(ContextCompat.getColor(this, R.color.accent))
        scanPairButton.isEnabled = false
        scanPairButton.text = "扫描中…"
        scanning = true

        addBondedDevices(adapter)
        runCatching { adapter.startDiscovery() }
        boardManager.startScan()
        handler.postDelayed({ stopUnifiedScan() }, SCAN_DURATION_MS)
    }

    private fun stopUnifiedScan() {
        if (!scanning) return
        scanning = false
        runCatching { bluetoothAdapter()?.cancelDiscovery() }
        boardManager.stopScan()
        scanPairButton.isEnabled = true
        scanPairButton.text = "扫描并配对"
        updateDeviceListVisibility()
        updateScanText()
    }

    private fun addBondedDevices(adapter: BluetoothAdapter) {
        try {
            adapter.bondedDevices.orEmpty().forEach { device ->
                addDevice(device, runCatching { device.name }.getOrNull(), 0)
            }
        } catch (_: SecurityException) {
            // The permission prompt handles missing scan/connect access.
        }
    }

    private fun addDevice(device: BluetoothDevice, discoveredName: String?, rssi: Int) {
        val existing = devices[device.address]
        val safeName = discoveredName?.takeIf { it.isNotBlank() }
            ?: runCatching { device.name }.getOrNull()
        if (existing == null) {
            devices[device.address] = ScanDevice(device, safeName, rssi)
        } else {
            if (safeName != null) existing.name = safeName
            if (rssi != 0) existing.rssi = rssi
        }
        deviceAdapter.notifyDataSetChanged()
        updateDeviceListVisibility()
    }

    private fun updateDeviceListVisibility() {
        val hasDevices = devices.isNotEmpty()
        deviceList.visibility = if (hasDevices) View.VISIBLE else View.GONE
        emptyText.visibility = if (hasDevices) View.GONE else View.VISIBLE
        if (!hasDevices) {
            emptyText.text = if (scanning) "正在扫描…" else "没有发现设备"
        }
    }

    private fun updateScanText() {
        if (!::scanState.isInitialized || scanning) return
        scanState.text = if (devices.isEmpty()) {
            "扫描结束：附近没有发现蓝牙设备"
        } else {
            "扫描结束：发现 ${devices.size} 个设备，点击设备名称连接"
        }
        scanState.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun connectSelectedDevice(entry: ScanDevice) {
        val name = displayName(entry)
        statusTitle.text = "正在连接 $name"
        statusDetail.text = "MAC：${entry.device.address}"
        statusDot.setTextColor(ContextCompat.getColor(this, R.color.accent))
        if (isBoardDevice(entry.device, entry.name)) {
            val terminal = TerminalRegistry.byId(TerminalRegistry.ESP32_S3)
            TerminalRegistry.select(this, terminal)
            OutputRouter.connectBoard(this, terminal, entry.device)
        } else {
            connectComputer(entry.device)
        }
    }

    private fun isBoardDevice(device: BluetoothDevice, name: String?): Boolean {
        val type = runCatching { device.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)
        if (type == BluetoothDevice.DEVICE_TYPE_LE) return true
        if (type == BluetoothDevice.DEVICE_TYPE_CLASSIC) return false
        val visible = name?.takeIf { it.isNotBlank() }
            ?: runCatching { device.name }.getOrNull()
            .orEmpty()
        return listOf("ESP32", "Pico", "键盘", "Keyboard")
            .any { visible.contains(it, ignoreCase = true) }
    }

    private fun connectComputer(device: BluetoothDevice) {
        val name = runCatching { device.name }.getOrNull() ?: "电脑"
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            pendingComputerAddress = device.address
            statusTitle.text = "正在与 $name 配对"
            statusDetail.text = "请在手机和电脑上确认配对；完成后会自动连接"
            val started = runCatching { device.createBond() }.getOrDefault(false)
            if (!started) {
                pendingComputerAddress = null
                Toast.makeText(this, "无法发起配对，请让电脑进入可发现状态", Toast.LENGTH_LONG).show()
            }
            return
        }
        KeyboardRuntime.enable()
        handler.postDelayed({
            KeyboardRuntime.connectComputer(device)
            LiteConnectionMemory.save(
                this,
                LiteConnectionMemory.KIND_COMPUTER,
                device.address,
                name
            )
            TerminalRegistry.select(this, TerminalRegistry.byId(TerminalRegistry.PHONE_HID))
            statusTitle.text = "正在主动连接 $name"
            statusDetail.text = "MAC：${device.address}"
        }, 650)
    }

    private fun reconnectRemembered(auto: Boolean) {
        val remembered = LiteConnectionMemory.load(this)
        if (remembered == null) {
            if (!auto) startUnifiedScan()
            return
        }
        if (
            remembered.kind == LiteConnectionMemory.KIND_BOARD &&
            BoardLink.isConnected()
        ) {
            refreshUnifiedStatus()
            return
        }
        if (
            remembered.kind == LiteConnectionMemory.KIND_COMPUTER &&
            KeyboardRuntime.snapshot().connected
        ) {
            refreshUnifiedStatus()
            return
        }
        if (!hasBluetoothPermissions()) {
            if (!auto) requestBluetoothPermissions()
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            if (!auto) enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        val device = runCatching { adapter.getRemoteDevice(remembered.address) }.getOrNull()
        if (device == null) {
            if (!auto) startUnifiedScan()
            return
        }
        statusTitle.text = "正在主动连接 ${remembered.name.ifBlank { "上次设备" }}"
        statusDetail.text = "MAC：${remembered.address}"
        statusDot.setTextColor(ContextCompat.getColor(this, R.color.accent))
        if (remembered.kind == LiteConnectionMemory.KIND_BOARD) {
            TerminalRegistry.select(this, TerminalRegistry.byId(TerminalRegistry.ESP32_S3))
            BoardLink.get(this).connect(device)
        } else {
            TerminalRegistry.select(this, TerminalRegistry.byId(TerminalRegistry.PHONE_HID))
            KeyboardRuntime.enable()
            handler.postDelayed({ KeyboardRuntime.connectComputer(device) }, 650)
        }
    }

    private fun refreshUnifiedStatus() {
        if (!::statusTitle.isInitialized) return
        val remembered = LiteConnectionMemory.load(this)
        reconnectButton.visibility = if (remembered == null) View.GONE else View.VISIBLE
        val computer = KeyboardRuntime.snapshot()
        val boardConnected = BoardLink.isConnected()
        when {
            computer.connected -> {
                val address = KeyboardRuntime.lastComputerAddress().orEmpty()
                statusDot.setTextColor(ContextCompat.getColor(this, R.color.connected))
                statusTitle.text = "● ${computer.deviceName ?: remembered?.name ?: "电脑"}"
                statusDetail.text = buildString {
                    append("连接成功")
                    if (address.isNotBlank()) append(" · MAC：$address")
                }
            }
            boardConnected -> {
                val address = BoardLink.connectedAddress().orEmpty()
                statusDot.setTextColor(ContextCompat.getColor(this, R.color.connected))
                statusTitle.text = "● ${BoardLink.connectedName() ?: remembered?.name ?: "开发板"}"
                statusDetail.text = buildString {
                    append("连接成功")
                    if (address.isNotBlank()) append(" · MAC：$address")
                }
            }
            remembered != null -> {
                statusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                statusTitle.text = "上次设备：${remembered.name.ifBlank { "未命名设备" }}"
                statusDetail.text = "MAC：${remembered.address} · 下次会自动主动连接"
            }
            else -> {
                statusDot.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                statusTitle.text = "尚未连接设备"
                statusDetail.text = "首次使用时点击“扫描并配对”"
            }
        }
    }

    private fun displayName(entry: ScanDevice): String =
        entry.name?.takeIf { it.isNotBlank() } ?: "未命名蓝牙设备"

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private fun registerDiscoveryReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            this,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

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

    private fun hasBluetoothPermissions(): Boolean {
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

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private inner class UnifiedDeviceAdapter : BaseAdapter() {
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
