package com.hidble.phonekeyboard

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.delay

/**
 * 外接键盘板（ESP32-S3）BLE 客户端。
 *
 * 板子对外提供与 Pico 方案一致的协议：
 *   Service  00001234-0000-1000-8000-00805f9b34fb
 *   写特征   00001235-...（App -> 板子，命令以 \n 结束）
 *   通知特征 00001236-...（板子 -> App，OK / ERR:xxx / STATUS:READY）
 *
 * 板子内部有 512KB PSRAM 缓冲，App 可以高速整段发送文本，由板子按速度控速输出成 HID 键盘报文。
 */
@SuppressLint("MissingPermission")
class BoardBleManager(private val context: Context) {

    companion object {
        private const val TAG = "BoardBleManager"
        private const val SCAN_TIMEOUT_MS = 30_000L

        @JvmField val SERVICE_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        @JvmField val CMD_CHAR_UUID: UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
        @JvmField val NOTIFY_CHAR_UUID: UUID = UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** 板子默认广播名（可同时用服务 UUID 匹配） */
        const val BOARD_NAME_HINT = "ESP32-S3 Keyboard"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private var scanning = false
    private var ready = false
    private var mtu = 20
    private var writing = false
    private val writeQueue = ArrayDeque<ByteArray>()

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onDeviceFound: ((BluetoothDevice, Int, String?) -> Unit)? = null
    var onDataReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun isBleAvailable(): Boolean = bluetoothAdapter != null && bluetoothAdapter.isEnabled

    fun startScan() {
        if (!isBleAvailable()) { onError?.invoke("蓝牙不可用或未开启"); return }
        if (scanning) return
        scanning = true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        try {
            bleScanner?.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
            scanning = false
            return
        }
        handler.postDelayed({ stopScan() }, SCAN_TIMEOUT_MS)
        Log.d(TAG, "BLE scan started")
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        try { bleScanner?.stopScan(scanCallback) } catch (e: Exception) { Log.e(TAG, "stopScan failed", e) }
        Log.d(TAG, "BLE scan stopped")
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        ready = false
        mtu = 20
        try {
            context.getSharedPreferences("hidble_prefs", Context.MODE_PRIVATE)
                .edit().putString("board_address", device.address).apply()
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            Log.d(TAG, "Connecting to ${device.address}")
        } catch (e: SecurityException) {
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
        }
    }

    fun disconnect() {
        writeQueue.clear()
        writing = false
        ready = false
        try { gatt?.disconnect(); gatt?.close() } catch (e: Exception) { Log.e(TAG, "disconnect error", e) }
        gatt = null
        cmdCharacteristic = null
        notifyCharacteristic = null
    }

    fun isConnected(): Boolean = ready

    /** 发送一条协议命令（自动按 MTU 分包，行尾补 \n） */
    @Synchronized
    fun sendCommand(command: String) {
        val characteristic = cmdCharacteristic
        if (characteristic == null) { onError?.invoke("外接键盘板未连接"); return }
        val payload = (command + "\n").toByteArray(Charsets.UTF_8)
        val chunkSize = (mtu - 3).coerceIn(20, 244)
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunkSize, payload.size)
            writeQueue.addLast(payload.copyOfRange(offset, end))
            offset = end
        }
        writeNext(characteristic)
    }

    @Synchronized
    private fun writeNext(characteristic: BluetoothGattCharacteristic) {
        if (writing || writeQueue.isEmpty()) return
        val chunk = writeQueue.removeFirst()
        writing = true
        try {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = chunk
            val ok = gatt?.writeCharacteristic(characteristic) == true
            if (!ok) { writing = false; onError?.invoke("发送命令失败") }
        } catch (e: Exception) {
            writing = false
            onError?.invoke("发送命令出错: ${e.message}")
        }
    }

    private fun writeNextIfReady() {
        cmdCharacteristic?.let { writeNext(it) }
    }

    /**
     * 发送文本（协议用 \n 作为命令结束符，所以文本里的换行必须拆开处理）：
     *  - 按行拆分：每行发一条 TEXT；行与行之间发 KEY:ENTER
     *  - 每行再按 UTF-8 字节数切块（中文 1 字 = 3 字节），避免超过固件 1200 字节的行缓冲
     */
    fun sendText(text: String) {
        val maxBytes = 800   // 单条 TEXT 的 UTF-8 字节上限（留足前缀/结尾余量）
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        for ((idx, line) in lines.withIndex()) {
            var remaining = line
            while (remaining.isNotEmpty()) {
                val chunk = takeUtf8Bytes(remaining, maxBytes)
                if (chunk.isEmpty()) break
                sendCommand("TEXT:$chunk")
                remaining = remaining.substring(chunk.length)
            }
            if (idx != lines.lastIndex) {
                sendCommand("KEY:ENTER")   // 原来的换行
            }
        }
    }

    /** 取前若干个字符，使其 UTF-8 字节数不超过 maxBytes（不拆开一个字符） */
    private fun takeUtf8Bytes(s: String, maxBytes: Int): String {
        var bytes = 0
        val sb = StringBuilder()
        for (ch in s) {
            val b = ch.toString().toByteArray(Charsets.UTF_8).size
            if (bytes + b > maxBytes) break
            sb.append(ch)
            bytes += b
        }
        return sb.toString()
    }
    fun setSpeed(level: Int) = sendCommand("SPEED:$level")
    fun setUnicodeMode(mode: Int) = sendCommand("UMOD:$mode")
    fun sendKey(key: String) = sendCommand("KEY:$key")

    /** 手机系统蓝牙里已配对的“外接键盘板”设备（扫不到广播时的兜底：直接连） */
    fun bondedBoardDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices.orEmpty().filter { d ->
                val n = try { d.name } catch (_: SecurityException) { null } ?: ""
                n.contains("ESP32", true) || n.contains("Keyboard", true)
            }
        } catch (e: SecurityException) { emptyList() }
    }

    /** 上次成功连接过的板子地址（保存在 hidble_prefs.board_address） */
    fun lastBoardAddress(): String? =
        context.getSharedPreferences("hidble_prefs", Context.MODE_PRIVATE).getString("board_address", null)

    fun cleanup() { stopScan(); disconnect() }

    private val scanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val device = result.device
            val name = try { device.name } catch (_: SecurityException) { null }
            handler.post { onDeviceFound?.invoke(device, result.rssi, name) }
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            handler.post { onError?.invoke("扫描失败，错误码: $errorCode") }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected, requesting MTU")
                if (!gatt.requestMtu(247)) gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ready = false
                writeQueue.clear()
                writing = false
                this@BoardBleManager.gatt = null
                cmdCharacteristic = null
                notifyCharacteristic = null
                handler.post { onConnectionStateChanged?.invoke(false) }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            this@BoardBleManager.mtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 20
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { onError?.invoke("服务发现失败: $status") }
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) { handler.post { onError?.invoke("未找到外接板服务 1234") }; return }
            cmdCharacteristic = service.getCharacteristic(CMD_CHAR_UUID)
            notifyCharacteristic = service.getCharacteristic(NOTIFY_CHAR_UUID)
            if (cmdCharacteristic == null) { handler.post { onError?.invoke("未找到写入特征 1235") }; return }
            val notify = notifyCharacteristic
            if (notify == null) {
                ready = true
                handler.post { onConnectionStateChanged?.invoke(true) }
                return
            }
            gatt.setCharacteristicNotification(notify, true)
            val descriptor = notify.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                ready = true
                handler.post { onConnectionStateChanged?.invoke(true) }
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == NOTIFY_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val data = characteristic.value?.toString(Charsets.UTF_8).orEmpty()
                if (data.isNotEmpty()) handler.post { onDataReceived?.invoke(data) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writing = false
            if (status != BluetoothGatt.GATT_SUCCESS) handler.post { onError?.invoke("特征值写入失败: $status") }
            writeNextIfReady()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            ready = true
            handler.post { onConnectionStateChanged?.invoke(true) }
        }
    }
}

/**
 * 全局外接板连接（连接管理页负责连接，主界面负责发送，两边共用同一个 GATT 连接）。
 */
object BoardLink {
    @Volatile private var manager: BoardBleManager? = null

    fun get(context: Context): BoardBleManager {
        manager?.let { return it }
        synchronized(this) {
            manager?.let { return it }
            val m = BoardBleManager(context.applicationContext)
            manager = m
            return m
        }
    }

    fun isConnected(): Boolean = manager?.isConnected() == true

    fun lastAddress(context: Context): String? = manager?.lastBoardAddress() ?: get(context).lastBoardAddress()

    /** 已知的板子地址：优先上次连接保存的，其次手机系统蓝牙里已配对的板子 */
    fun knownBoardAddress(context: Context): String? {
        val m = get(context)
        m.lastBoardAddress()?.let { return it }
        return m.bondedBoardDevices().firstOrNull()?.address
    }

    /** 主界面发送前调用：若当前没连上，但之前连过板子，就自动按地址重连并等待就绪 */
    suspend fun reconnectAndWait(context: Context, timeoutMs: Long = 6000): Boolean {
        val m = get(context)
        if (m.isConnected()) return true
        val addr = m.lastBoardAddress() ?: m.bondedBoardDevices().firstOrNull()?.address ?: return false
        return try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter ?: return false
            val device = adapter.getRemoteDevice(addr)
            m.connect(device)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (m.isConnected()) return true
                delay(250)
            }
            false
        } catch (e: Exception) {
            false
        }
    }
    fun sendText(text: String): Boolean {
        val m = manager ?: return false
        if (!m.isConnected()) return false
        m.sendText(text)
        return true
    }
    fun setSpeed(level: Int) { manager?.takeIf { it.isConnected() }?.setSpeed(level) }
    fun setUnicodeMode(mode: Int) { manager?.takeIf { it.isConnected() }?.setUnicodeMode(mode) }
}