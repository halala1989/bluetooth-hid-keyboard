package com.hidble.keyboard

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

/**
 * BLE 管理器 - 负责 BLE 扫描、连接和数据传输
 */
class BleManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BleManager"
        
        // 自定义服务 UUID（与 Pico W 固件匹配）
        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
        val CMD_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
        val NOTIFY_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef2")
        
        private const val SCAN_TIMEOUT_MS = 10000L
    }
    
    // BLE 相关
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    
    // 回调
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onDeviceFound: ((BluetoothDevice, Int) -> Unit)? = null
    var onDataReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    
    /**
     * 检查 BLE 是否可用
     */
    fun isBleAvailable(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }
    
    /**
     * 开始扫描 BLE 设备
     */
    fun startScan() {
        if (!isBleAvailable()) {
            onError?.invoke("蓝牙不可用或未开启")
            return
        }
        
        if (isScanning) return
        
        isScanning = true
        bleScanner?.startScan(scanCallback)
        
        // 设置扫描超时
        handler.postDelayed({
            stopScan()
        }, SCAN_TIMEOUT_MS)
        
        Log.d(TAG, "BLE 扫描已开始")
    }
    
    /**
     * 停止扫描
     */
    fun stopScan() {
        if (!isScanning) return
        
        isScanning = false
        bleScanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
        
        Log.d(TAG, "BLE 扫描已停止")
    }
    
    /**
     * 连接到设备
     */
    fun connect(device: BluetoothDevice) {
        stopScan()
        
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            Log.d(TAG, "正在连接到 ${device.name}")
        } catch (e: SecurityException) {
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            cmdCharacteristic = null
            notifyCharacteristic = null
            Log.d(TAG, "已断开连接")
        } catch (e: Exception) {
            Log.e(TAG, "断开连接时出错", e)
        }
    }
    
    /**
     * 发送命令
     */
    fun sendCommand(command: String) {
        val gatt = bluetoothGatt ?: run {
            onError?.invoke("未连接到设备")
            return
        }
        
        val characteristic = cmdCharacteristic ?: run {
            onError?.invoke("命令特征值不可用")
            return
        }
        
        try {
            val data = (command + "\n").toByteArray(Charsets.UTF_8)
            characteristic.value = data
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            val result = gatt.writeCharacteristic(characteristic)
            if (!result) {
                onError?.invoke("发送命令失败")
            } else {
                Log.d(TAG, "命令已发送: $command")
            }
        } catch (e: SecurityException) {
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
        } catch (e: Exception) {
            onError?.invoke("发送命令出错: ${e.message}")
        }
    }
    
    /**
     * 发送文本
     */
    fun sendText(text: String) {
        sendCommand("TEXT:$text")
    }
    
    /**
     * 发送按键
     */
    fun sendKey(key: String) {
        sendCommand("KEY:$key")
    }
    
    /**
     * 发送组合键
     */
    fun sendCombo(modifiers: List<String>, key: String) {
        val modStr = modifiers.joinToString("+")
        sendCommand("MOD:$modStr+$key")
    }
    
    /**
     * 发送 Unicode 字符
     */
    fun sendUnicode(codepoint: Int) {
        sendCommand("UNI:$codepoint")
    }
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return bluetoothGatt != null && cmdCharacteristic != null
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopScan()
        disconnect()
    }
    
    // BLE 扫描回调
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            
            Log.d(TAG, "发现设备: ${device.name} (${device.address}) RSSI: $rssi")
            
            // 过滤 Pico HID Keyboard 设备
            if (device.name?.contains("Pico HID Keyboard") == true) {
                onDeviceFound?.invoke(device, rssi)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            onError?.invoke("扫描失败，错误码: $errorCode")
        }
    }
    
    // GATT 回调
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "已连接，正在发现服务...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "已断开连接")
                    handler.post {
                        onConnectionStateChanged?.invoke(false)
                    }
                    cleanup()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    cmdCharacteristic = service.getCharacteristic(CMD_CHAR_UUID)
                    notifyCharacteristic = service.getCharacteristic(NOTIFY_CHAR_UUID)
                    
                    // 启用通知
                    if (notifyCharacteristic != null) {
                        gatt.setCharacteristicNotification(notifyCharacteristic, true)
                        
                        val descriptor = notifyCharacteristic?.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                    
                    Log.d(TAG, "服务发现完成")
                    handler.post {
                        onConnectionStateChanged?.invoke(true)
                    }
                } else {
                    onError?.invoke("未找到 HID 服务")
                }
            } else {
                onError?.invoke("服务发现失败: $status")
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == NOTIFY_CHAR_UUID) {
                val data = characteristic.value?.toString(Charsets.UTF_8)
                if (data != null) {
                    Log.d(TAG, "收到数据: $data")
                    handler.post {
                        onDataReceived?.invoke(data)
                    }
                }
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "特征值写入成功")
            } else {
                onError?.invoke("特征值写入失败: $status")
            }
        }
    }
}
