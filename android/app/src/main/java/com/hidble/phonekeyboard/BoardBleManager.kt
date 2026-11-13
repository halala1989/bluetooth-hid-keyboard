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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

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
        private const val CONNECTION_TIMEOUT_MS = 15_000L

        @JvmField val SERVICE_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        @JvmField val CMD_CHAR_UUID: UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
        @JvmField val NOTIFY_CHAR_UUID: UUID = UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** 板子默认广播名（可同时用服务 UUID 匹配） */
        const val BOARD_NAME_HINT = "ESP32蓝牙键盘终端-0001"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val handler = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private var scanning = false
    private var ready = false
    private var connectedAddress: String? = null
    private var connectedDeviceName: String? = null
    private var pendingAddress: String? = null
    private var pendingCommandAck: CompletableDeferred<String>? = null
    private var mtu = 20
    private var writing = false
    private val writeQueue = ArrayDeque<ByteArray>()

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onScanStateChanged: ((Boolean) -> Unit)? = null
    var onDeviceFound: ((BluetoothDevice, Int, String?) -> Unit)? = null
    var onDataReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val connectionTimeout = Runnable {
        if (!ready && gatt != null) {
            failConnection("连接 ${pendingAddress ?: "外接板"} 超时，请确认板子已运行桥接固件")
        }
    }

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
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner == null) {
                scanning = false
                onScanStateChanged?.invoke(false)
                onError?.invoke("BLE 扫描器不可用，请重新开启蓝牙")
                return
            }
            scanner.startScan(null, settings, scanCallback)
            onScanStateChanged?.invoke(true)
        } catch (e: SecurityException) {
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
            scanning = false
            onScanStateChanged?.invoke(false)
            return
        } catch (e: Exception) {
            onError?.invoke("启动 BLE 扫描失败: ${e.message}")
            scanning = false
            onScanStateChanged?.invoke(false)
            return
        }
        handler.postDelayed({ stopScan() }, SCAN_TIMEOUT_MS)
        Log.d(TAG, "BLE scan started")
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "stopScan failed", e)
        }
        onScanStateChanged?.invoke(false)
        Log.d(TAG, "BLE scan stopped")
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        closeCurrentGatt()
        ready = false
        mtu = 20
        pendingAddress = device.address
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                failConnection("系统无法创建 BLE 连接")
                return
            }
            handler.removeCallbacks(connectionTimeout)
            handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MS)
            Log.d(TAG, "Connecting to ${device.address}")
        } catch (e: SecurityException) {
            pendingAddress = null
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
        } catch (e: Exception) {
            failConnection("发起连接失败: ${e.message}")
        }
    }

    fun disconnect() {
        handler.removeCallbacks(connectionTimeout)
        writeQueue.clear()
        writing = false
        ready = false
        pendingAddress = null
        connectedAddress = null
        connectedDeviceName = null
        pendingCommandAck?.cancel(CancellationException("Board disconnected"))
        pendingCommandAck = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "disconnect error", e)
        }
        gatt = null
        cmdCharacteristic = null
        notifyCharacteristic = null
    }

    fun isConnected(): Boolean = ready
    fun currentAddress(): String? = connectedAddress
    fun currentDeviceName(): String? = connectedDeviceName

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

    /**
     * STOP 必须插队：清除尚未发出的 TEXT，并先用换行结束当前分片，
     * 让板子能够立即识别 STOP 而不是排在全部文字后面。
     */
    @Synchronized
    fun stopOutput(): Boolean {
        val characteristic = cmdCharacteristic ?: run {
            onError?.invoke("外接键盘板未连接")
            return false
        }
        writeQueue.clear()
        val payload = "\nSTOP\n".toByteArray(Charsets.UTF_8)
        val chunkSize = (mtu - 3).coerceIn(20, 244)
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunkSize, payload.size)
            writeQueue.addLast(payload.copyOfRange(offset, end))
            offset = end
        }
        writeNext(characteristic)
        return true
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

    private fun markReady(connectedGatt: BluetoothGatt) {
        if (connectedGatt !== gatt) return
        handler.removeCallbacks(connectionTimeout)
        ready = true
        connectedAddress = connectedGatt.device.address
        connectedDeviceName = try {
            connectedGatt.device.name?.takeIf { it.isNotBlank() } ?: BOARD_NAME_HINT
        } catch (_: SecurityException) {
            BOARD_NAME_HINT
        }
        pendingAddress = null
        context.getSharedPreferences("hidble_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("board_address", connectedAddress)
            .apply()
        handler.post { onConnectionStateChanged?.invoke(true) }
    }

    private fun failConnection(message: String) {
        handler.removeCallbacks(connectionTimeout)
        ready = false
        connectedAddress = null
        connectedDeviceName = null
        pendingAddress = null
        pendingCommandAck?.cancel(CancellationException("Board connection failed"))
        pendingCommandAck = null
        writeQueue.clear()
        writing = false
        cmdCharacteristic = null
        notifyCharacteristic = null
        val failedGatt = gatt
        gatt = null
        try {
            failedGatt?.disconnect()
            failedGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "close failed GATT", e)
        }
        handler.post {
            onError?.invoke(message)
            onConnectionStateChanged?.invoke(false)
        }
    }

    private fun closeCurrentGatt() {
        handler.removeCallbacks(connectionTimeout)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "close previous GATT failed", e)
        }
        gatt = null
        cmdCharacteristic = null
        notifyCharacteristic = null
        ready = false
        connectedAddress = null
        connectedDeviceName = null
        pendingCommandAck?.cancel(CancellationException("Board connection closed"))
        pendingCommandAck = null
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

    suspend fun sendCommandAwait(command: String, timeoutMs: Long = 15_000): String =
        awaitCommandAck(timeoutMs) { sendCommand(command) }

    suspend fun sendTextAwait(text: String, timeoutMs: Long = 15_000): String =
        awaitCommandAck(timeoutMs) { sendText(text) }

    private suspend fun awaitCommandAck(
        timeoutMs: Long,
        sendAction: () -> Unit
    ): String {
        val ack = CompletableDeferred<String>()
        synchronized(this) {
            pendingCommandAck?.cancel(CancellationException("Superseded"))
            pendingCommandAck = ack
        }
        return try {
            sendAction()
            withTimeout(timeoutMs) { ack.await() }
        } finally {
            synchronized(this) {
                if (pendingCommandAck === ack) pendingCommandAck = null
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
            val name = result.scanRecord?.deviceName
                ?: try { device.name } catch (_: SecurityException) { null }
            handler.post { onDeviceFound?.invoke(device, result.rssi, name) }
        }

        override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
            results.forEach { onScanResult(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            handler.post {
                onScanStateChanged?.invoke(false)
                onError?.invoke("扫描失败，错误码: $errorCode")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            callbackGatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (callbackGatt !== gatt) {
                callbackGatt.close()
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS &&
                newState != BluetoothProfile.STATE_DISCONNECTED
            ) {
                failConnection("BLE 连接失败，GATT 状态码 $status")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected, requesting MTU")
                if (!callbackGatt.requestMtu(247)) callbackGatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.removeCallbacks(connectionTimeout)
                ready = false
                connectedAddress = null
                connectedDeviceName = null
                pendingAddress = null
                pendingCommandAck?.cancel(CancellationException("Board disconnected"))
                pendingCommandAck = null
                writeQueue.clear()
                writing = false
                this@BoardBleManager.gatt = null
                cmdCharacteristic = null
                notifyCharacteristic = null
                callbackGatt.close()
                handler.post {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        onError?.invoke("BLE 已断开，状态码 $status")
                    }
                    onConnectionStateChanged?.invoke(false)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            this@BoardBleManager.mtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 20
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== this@BoardBleManager.gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("板子服务发现失败: $status")
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                failConnection("未找到 ESP32-S3 桥接服务 1234，请确认烧录的是 Bridge 固件")
                return
            }
            cmdCharacteristic = service.getCharacteristic(CMD_CHAR_UUID)
            notifyCharacteristic = service.getCharacteristic(NOTIFY_CHAR_UUID)
            if (cmdCharacteristic == null) {
                failConnection("未找到 ESP32-S3 写入特征 1235")
                return
            }
            val notify = notifyCharacteristic
            if (notify == null) {
                markReady(gatt)
                return
            }
            if (!gatt.setCharacteristicNotification(notify, true)) {
                failConnection("无法启用板子状态通知")
                return
            }
            val descriptor = notify.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                markReady(gatt)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!gatt.writeDescriptor(descriptor)) {
                    failConnection("无法订阅板子状态通知")
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == NOTIFY_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val data = characteristic.value?.toString(Charsets.UTF_8).orEmpty()
                if (data.isNotEmpty()) {
                    handler.post { onDataReceived?.invoke(data) }
                    if (!data.startsWith("STATUS")) {
                        synchronized(this@BoardBleManager) {
                            pendingCommandAck?.also { ack ->
                                pendingCommandAck = null
                                ack.complete(data)
                            }
                        }
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writing = false
            if (status != BluetoothGatt.GATT_SUCCESS) handler.post { onError?.invoke("特征值写入失败: $status") }
            writeNextIfReady()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== this@BoardBleManager.gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markReady(gatt)
            } else {
                failConnection("启用板子状态通知失败: $status")
            }
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
    fun connectedAddress(): String? = manager?.currentAddress()
    fun connectedName(): String? = manager?.currentDeviceName()

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
    suspend fun sendTextAwait(text: String): String {
        val m = manager ?: error("外接键盘板未连接")
        if (!m.isConnected()) error("外接键盘板未连接")
        return m.sendTextAwait(text)
    }
    suspend fun sendCommandAwait(command: String): String {
        val m = manager ?: error("外接键盘板未连接")
        if (!m.isConnected()) error("外接键盘板未连接")
        return m.sendCommandAwait(command)
    }
    fun stopOutput(): Boolean = manager?.stopOutput() == true
    fun setSpeed(level: Int) { manager?.takeIf { it.isConnected() }?.setSpeed(level) }
    fun setUnicodeMode(mode: Int) { manager?.takeIf { it.isConnected() }?.setUnicodeMode(mode) }
}
