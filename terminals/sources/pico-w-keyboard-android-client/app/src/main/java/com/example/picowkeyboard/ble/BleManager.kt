package com.example.picowkeyboard.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Wraps the Android BLE GATT client: scanning, connecting, MTU negotiation,
 * and a strictly serialized write queue. One packet is written only after the
 * previous onCharacteristicWrite() callback has completed (reliable ordering).
 */
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"

        /** Service / characteristic fixed by the protocol spec. */
        val SERVICE_UUID: UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
        val TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")

        /** Device name filter used for the default "only PICO" mode. */
        const val DEVICE_NAME_FILTER = "PICO-W-KEYBOARD"

        private const val SCAN_DURATION_MS = 8_000L

        /** Desired ATT MTU to negotiate; Android caps this per-device. */
        private const val DESIRED_MTU = 247

        /** Default ATT MTU (23) minus ATT header (3) minus our frame header (5). */
        private const val FALLBACK_MAX_PAYLOAD = 15
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var scanner: BluetoothLeScanner? = null
    private var scanJob: Job? = null
    private var gatt: BluetoothGatt? = null

    /** Device currently connected. */
    private var connectedDevice: BleDevice? = null

    /** Negotiated MTU in bytes; 23 is the BLE default. */
    private var negotiatedMtu: Int = 23

    /** Serialized outbound queue: front of the queue is the in-flight packet. */
    private val writeQueue = ArrayDeque<BlePacketizer.Packet>()

    /** Current message id, incremented per user send. */
    private var messageIdCounter: Int = 0

    /** Set when a CANCEL was requested; the in-flight write still finishes. */
    private var cancelRequested: Boolean = false

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()

    private var serviceDiscovered = false

    // ---------------------------------------------------------------------
    // Permissions / adapter helpers
    // ---------------------------------------------------------------------

    private fun hasScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        }
        // Below Android 12 BLUETOOTH / BLUETOOTH_ADMIN are normal permissions
        // granted at install time; no location permission is declared per spec.
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    // ---------------------------------------------------------------------
    // Scanning
    // ---------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun startScan(showAllDevices: Boolean) {
        if (!hasScanPermission() || !isBluetoothEnabled()) return
        if (_isScanning.value) return

        val adapter = bluetoothAdapter ?: return
        scanner = adapter.bluetoothLeScanner ?: return

        _scanResults.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters: List<ScanFilter>? = if (showAllDevices) {
            null
        } else {
            listOf(
                ScanFilter.Builder()
                    .setDeviceName(DEVICE_NAME_FILTER)
                    .build()
            )
        }

        try {
            scanner?.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "startScan security exception", e)
            _isScanning.value = false
            return
        }

        scanJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "stopScan security exception", e)
        }
        _isScanning.value = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: (result.scanRecord?.deviceName)
            val rssi = result.rssi
            val entry = BleDevice(device.address, name, rssi)
            val current = _scanResults.value.toMutableList()
            val index = current.indexOfFirst { it.address == entry.address }
            if (index >= 0) {
                current[index] = entry
            } else {
                current.add(entry)
            }
            _scanResults.value = current
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "scan failed code=$errorCode")
            _isScanning.value = false
        }
    }

    // ---------------------------------------------------------------------
    // Connection
    // ---------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        if (!hasConnectPermission() || !isBluetoothEnabled()) return
        if (_connectionState.value is BleConnectionState.Connecting ||
            _connectionState.value is BleConnectionState.Connected
        ) {
            return
        }

        disconnectInternal()
        _connectionState.value = BleConnectionState.Connecting
        connectedDevice = device
        serviceDiscovered = false
        negotiatedMtu = 23

        val bluetoothDevice: BluetoothDevice = bluetoothAdapter
            ?.getRemoteDevice(device.address)
            ?: return

        val transport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BluetoothDevice.TRANSPORT_LE
        } else {
            BluetoothDevice.DEVICE_TYPE_LE
        }

        try {
            gatt = bluetoothDevice.connectGatt(context, false, gattCallback, transport)
        } catch (e: SecurityException) {
            Log.e(TAG, "connectGatt security exception", e)
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (_connectionState.value is BleConnectionState.Connected ||
            _connectionState.value is BleConnectionState.Connecting ||
            _connectionState.value is BleConnectionState.Disconnecting
        ) {
            _connectionState.value = BleConnectionState.Disconnecting
            disconnectInternal()
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal() {
        stopScan()
        writeQueue.clear()
        try {
            gatt?.disconnect()
        } catch (e: SecurityException) {
            Log.e(TAG, "disconnect security exception", e)
        }
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        serviceDiscovered = false
        if (_connectionState.value is BleConnectionState.Connected ||
            _connectionState.value is BleConnectionState.Connecting ||
            _connectionState.value is BleConnectionState.Disconnecting
        ) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "connected but status=$status")
                        _connectionState.value = BleConnectionState.Disconnected
                        return
                    }
                    Log.i(TAG, "connected to ${connectedDevice?.displayName}")
                    _connectionState.value = BleConnectionState.Connected(
                        connectedDevice ?: BleDevice("", null, 0)
                    )
                    // Ask for a larger MTU so bigger DATA payloads fit.
                    try {
                        gatt.requestMtu(DESIRED_MTU)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "requestMtu security exception", e)
                    }
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "discoverServices security exception", e)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "disconnected")
                    writeQueue.clear()
                    connectedDevice = null
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            Log.i(TAG, "MTU negotiated: $negotiatedMtu (status=$status)")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "services discovery failed status=$status")
                _connectionState.value = BleConnectionState.Disconnected
                return
            }
            serviceDiscovered = true
            val svc = gatt.getService(SERVICE_UUID)
            if (svc == null) {
                Log.e(TAG, "service not found: $SERVICE_UUID")
                _sendState.value = SendState.Failed("未找到目标 Service")
                return
            }
            val tx = svc.getCharacteristic(TX_CHARACTERISTIC_UUID)
            if (tx == null) {
                Log.e(TAG, "characteristic not found: $TX_CHARACTERISTIC_UUID")
                _sendState.value = SendState.Failed("未找到 TX Characteristic")
                return
            }
            Log.i(TAG, "target service + characteristic ready")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "write failed status=$status")
                val failed = writeQueue.firstOrNull()
                writeQueue.clear()
                _sendState.value = SendState.Failed(
                    "写入失败 status=$status (${failed?.describe() ?: "unknown"})"
                )
                return
            }

            // A cancel was requested: drain whatever is left, never report success.
            if (cancelRequested) {
                val head = writeQueue.firstOrNull()
                if (head?.type == BlePacketizer.TYPE_CANCEL) {
                    writeQueue.removeFirst()
                    cancelRequested = false
                }
                return
            }

            val finished = writeQueue.removeFirstOrNull()
            if (finished == null) {
                // Nothing was in flight; this should not happen.
                _sendState.value = SendState.Failed("意外的写入回调")
                return
            }
            packetsSent++

            if (writeQueue.isEmpty()) {
                // Entire message (…including END) flushed.
                _sendState.value = SendState.Success(totalBytes, packetsSent)
                return
            }

            _sendState.value = SendState.Sending(
                sentPackets = packetsSent,
                totalPackets = packetsSent + writeQueue.size,
            )
            writeNext()
        }
    }

    // ---------------------------------------------------------------------
    // Serialized sending
    // ---------------------------------------------------------------------

    private var totalBytes: Int = 0
    private var packetsSent: Int = 0

    /** Largest DATA payload that fits the current MTU (frame header = 5 bytes). */
    fun currentMaxPayload(): Int {
        val attPayload = negotiatedMtu - 3 // subtract ATT header
        return (attPayload - BlePacketizer.HEADER_SIZE)
            .coerceAtMost(240)
            .coerceAtLeast(1)
    }

    /**
     * Sends [text] as a reliable message: START -> DATA* -> END, one write at
     * a time, waiting for each onCharacteristicWrite() before the next.
     */
    @Synchronized
    fun sendText(text: String): Boolean {
        val state = _connectionState.value
        if (state !is BleConnectionState.Connected) {
            _sendState.value = SendState.Failed("未连接")
            return false
        }
        if (!serviceDiscovered) {
            _sendState.value = SendState.Failed("服务尚未就绪")
            return false
        }
        if (_sendState.value is SendState.Sending) {
            _sendState.value = SendState.Failed("上一消息仍在发送")
            return false
        }
        if (text.isEmpty()) {
            _sendState.value = SendState.Failed("文本为空")
            return false
        }

        messageIdCounter = (messageIdCounter + 1) and 0xFFFF
        val messageId = messageIdCounter
        val packets = BlePacketizer.packetize(text, messageId, currentMaxPayload())
        totalBytes = text.toByteArray(Charsets.UTF_8).size
        packetsSent = 0
        cancelRequested = false

        writeQueue.clear()
        packets.forEach { writeQueue.addLast(it) }

        _sendState.value = SendState.Sending(
            sentPackets = 0,
            totalPackets = packets.size,
        )
        writeNext()
        return true
    }

    /** Cancels the current send: drops the queue and sends a CANCEL frame. */
    @Synchronized
    @SuppressLint("MissingPermission")
    fun cancelSend() {
        if (_sendState.value !is SendState.Sending) return
        cancelRequested = true
        writeQueue.clear()
        val cancelPacket = BlePacketizer.cancel(messageIdCounter)
        writeQueue.addLast(cancelPacket)
        _sendState.value = SendState.Cancelled
        writeNext()
    }

    @SuppressLint("MissingPermission")
    private fun writeNext() {
        val gatt = gatt ?: run {
            writeQueue.clear()
            _sendState.value = SendState.Failed("连接已丢失")
            return
        }
        val packet = writeQueue.firstOrNull() ?: return
        val svc = gatt.getService(SERVICE_UUID)
        if (svc == null) {
            writeQueue.clear()
            _sendState.value = SendState.Failed("未找到 Service")
            return
        }
        val characteristic = svc.getCharacteristic(TX_CHARACTERISTIC_UUID)
        if (characteristic == null) {
            writeQueue.clear()
            _sendState.value = SendState.Failed("未找到 TX Characteristic")
            return
        }

        val bytes = packet.toBytes()
        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.writeType = writeType

        // Fire-and-forget write: outcome is driven by onCharacteristicWrite().
        // Exceptions here mean the write could not even be queued.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, bytes, writeType)
            } else {
                characteristic.value = bytes
                gatt.writeCharacteristic(characteristic)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeCharacteristic failed", e)
            writeQueue.clear()
            _sendState.value = SendState.Failed("写入异常: ${e.message}")
        }
    }
}
