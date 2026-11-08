package com.hidble.phonekeyboard

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 把手机注册成标准蓝牙键盘（BluetoothHidDevice，Android 9+），
 * 电脑通过蓝牙直接配对即可打字，无需 Pico W。
 *
 * 连接流程：
 * 1. register() 注册 HID 服务 -> 手机在电脑“添加蓝牙设备”中显示为“手机蓝牙键盘”
 * 2. 电脑端配对后设备会出现在已配对列表，点选 connect()（多数电脑配对后会自动连接）
 * 3. 连接成功后 sendReport() 发送键盘报告
 */
@SuppressLint("MissingPermission")
class HidDeviceManager(private val context: Context) {

    companion object {
        private const val TAG = "HidDeviceManager"
        /** 电脑端显示的名称 */
        const val KEYBOARD_NAME = "手机蓝牙键盘"
        /** 掉线后自动重连：最多尝试次数与间隔 */
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 2000L
        /** connect() 内部重试上限 */
        private const val CONNECT_MAX_RETRY = 6

        /**
         * 标准键盘 HID Report Descriptor（8 字节报告：modifier, reserved, key0..key5）
         */
        private val REPORT_DESCRIPTOR = byteArrayOf(
            0x05, 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09, 0x06,          // Usage (Keyboard)
            0xA1.toByte(), 0x01, // Collection (Application)
            0x05, 0x07,          //   Usage Page (Keyboard)
            0x19, 0xE0.toByte(), //   Usage Minimum (224)
            0x29, 0xE7.toByte(), //   Usage Maximum (231)
            0x15, 0x00,          //   Logical Minimum (0)
            0x25, 0x01,          //   Logical Maximum (1)
            0x75, 0x01,          //   Report Size (1)
            0x95.toByte(), 0x08,          //   Report Count (8)
            0x81.toByte(), 0x02, //   Input (Data, Variable, Absolute) - modifier byte
            0x95.toByte(), 0x01,          //   Report Count (1)
            0x75, 0x08,          //   Report Size (8)
            0x81.toByte(), 0x01, //   Input (Constant) - reserved
            0x95.toByte(), 0x05,          //   Report Count (5)
            0x75, 0x01,          //   Report Size (1)
            0x05, 0x08,          //   Usage Page (LEDs)
            0x19, 0x01,          //   Usage Minimum (1)
            0x29, 0x05,          //   Usage Maximum (5)
            0x91.toByte(), 0x02, //   Output (Data, Variable, Absolute) - LEDs
            0x95.toByte(), 0x01,          //   Report Count (1)
            0x75, 0x03,          //   Report Size (3)
            0x91.toByte(), 0x01, //   Output (Constant) - LED padding
            0x95.toByte(), 0x06,          //   Report Count (6)
            0x75, 0x08,          //   Report Size (8)
            0x15, 0x00,          //   Logical Minimum (0)
            0x25, 0x65,          //   Logical Maximum (101)
            0x05, 0x07,          //   Usage Page (Keyboard)
            0x19, 0x00,          //   Usage Minimum (0)
            0x29, 0x65,          //   Usage Maximum (101)
            0x81.toByte(), 0x00, //   Input (Data, Array) - key codes
            0xC0.toByte()        // End Collection
        )
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var hidDevice: BluetoothHidDevice? = null
    private var registered = false
    private var hostDevice: BluetoothDevice? = null
    /** 最近一次成功连接过的主机（掉线后保留，用于自动重连） */
    private var lastHost: BluetoothDevice? = null
    private var reconnectAttempts = 0
    private var connectRetry = 0

    var onAppStatusChanged: ((Boolean) -> Unit)? = null
    var onConnectionStateChanged: ((BluetoothDevice?, Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onLedState: ((Int) -> Unit)? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE && proxy is BluetoothHidDevice) {
                hidDevice = proxy
                Log.d(TAG, "HID_DEVICE service connected")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                val wasRegistered = registered
                hidDevice = null
                Log.d(TAG, "HID_DEVICE service disconnected")
                if (wasRegistered) {
                    // 蓝牙 HID 服务重启后自动恢复注册，避免连接悄悄丢失
                    mainHandler.postDelayed({
                        init()
                        if (!registered) register()
                    }, 800)
                }
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidDeviceManager.registered = registered
            mainHandler.post { onAppStatusChanged?.invoke(registered) }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
                lastHost = device
                reconnectAttempts = 0
                // 连接/重连成功后先清一次键盘状态，避免主机端残留上次的按键
                try {
                    hidDevice?.sendReport(device, 0, ByteArray(8))
                } catch (e: Exception) {
                    Log.e(TAG, "sendReport reset error", e)
                }
            } else if (state == BluetoothProfile.STATE_DISCONNECTED && device == hostDevice) {
                hostDevice = null
                maybeReconnect()
            }
            mainHandler.post { onConnectionStateChanged?.invoke(device, state) }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            // 主机查询当前报告（键盘无状态），回一个空报告即可
            val size = bufferSize.coerceIn(0, 8)
            hidDevice?.replyReport(device, type, id, ByteArray(size))
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && data != null && data.isNotEmpty()) {
                val led = (data[0].toInt() and 0xFF)
                mainHandler.post { onLedState?.invoke(led) }
            }
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) { }
        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) { }
        override fun onVirtualCableUnplug(device: BluetoothDevice?) { }
    }

    /** 初始化：获取 HID_DEVICE profile（蓝牙开启后再调用更可靠） */
    fun init() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            // 首次启动可能权限尚未授予，静默忽略；授予后 onResume 会再次 init()
            Log.d(TAG, "getProfileProxy permission not ready: ${e.message}")
        }
    }

    fun isBluetoothOn(): Boolean = bluetoothAdapter != null && bluetoothAdapter.isEnabled

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hidDevice != null

    fun isRegistered(): Boolean = registered

    fun isConnected(): Boolean = hostDevice != null && registered

    fun connectedDeviceName(): String? = hostDevice?.name

    private var registerRetry = 0

    /** 注册 HID 键盘服务（手机开始以键盘身份出现） */
    @SuppressLint("MissingPermission")
    fun register() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError?.invoke("需要 Android 9 及以上系统")
            return
        }
        if (registered) return
        if (hidDevice == null) {
            // 蓝牙可能刚开启，profile 尚未连上：重新获取并稍后重试
            if (registerRetry >= 4) {
                registerRetry = 0
                onError?.invoke("此手机不支持蓝牙键盘功能")
                return
            }
            registerRetry++
            init()
            mainHandler.postDelayed({ register() }, 600)
            return
        }
        val hid = hidDevice ?: return
        registerRetry = 0
        try {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                KEYBOARD_NAME,
                "Android 手机模拟蓝牙键盘",
                "Codex",
                BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                REPORT_DESCRIPTOR
            )
            val ok = hid.registerApp(sdp, null, null, executor, callback)
            if (!ok) onError?.invoke("注册蓝牙键盘失败")
        } catch (e: SecurityException) {
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
        } catch (e: Exception) {
            onError?.invoke("注册蓝牙键盘失败: ${e.message}")
        }
    }

    fun unregister() {
        try {
            hidDevice?.unregisterApp()
        } catch (e: Exception) {
            Log.e(TAG, "unregisterApp error", e)
        }
    }

    /** 主动连接一个已配对主机；键盘未注册会自动先注册，服务未就绪会自动重试 */
    fun connect(device: BluetoothDevice) {
        lastHost = device
        if (hidDevice == null) {
            if (connectRetry >= CONNECT_MAX_RETRY) {
                connectRetry = 0
                onError?.invoke("连接失败：蓝牙键盘服务未就绪")
                return
            }
            connectRetry++
            init()
            mainHandler.postDelayed({ connect(device) }, 500)
            return
        }
        if (!registered) {
            if (connectRetry >= CONNECT_MAX_RETRY) {
                connectRetry = 0
                onError?.invoke("连接失败：请先开启模拟蓝牙键盘")
                return
            }
            connectRetry++
            register()
            mainHandler.postDelayed({ connect(device) }, 600)
            return
        }
        connectRetry = 0
        try {
            val ok = hidDevice?.connect(device) ?: false
            if (!ok) onError?.invoke("连接失败，请确认电脑已开启蓝牙")
        } catch (e: SecurityException) {
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
        }
    }

    /** 回到前台或手动触发时：已注册但未连接且知道上次主机，尝试恢复连接 */
    fun reconnectIfNeeded() {
        val target = lastHost
        if (registered && !isConnected() && target != null) {
            connect(target)
        }
    }

    /** 掉线后自动重连（最多 MAX_RECONNECT_ATTEMPTS 次，间隔 2 秒） */
    private fun maybeReconnect() {
        if (!registered || lastHost == null) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        val target = lastHost
        mainHandler.postDelayed({
            if (registered && !isConnected() && target != null) {
                connect(target)
            }
        }, RECONNECT_DELAY_MS)
    }

    fun disconnect() {
        val device = hostDevice
        try {
            if (device != null) hidDevice?.disconnect(device)
        } catch (e: Exception) {
            Log.e(TAG, "disconnect error", e)
        }
    }

    /** 发送 8 字节键盘报告 */
    fun sendReport(data: ByteArray): Boolean {
        val device = hostDevice ?: return false
        if (!registered) return false
        return try {
            hidDevice?.sendReport(device, 0, data) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "sendReport error", e)
            false
        }
    }

    /** 已配对设备列表（电脑） */
    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice>? {
        return try {
            bluetoothAdapter?.bondedDevices?.toList()
        } catch (e: SecurityException) {
            onError?.invoke("蓝牙权限被拒绝: ${e.message}")
            emptyList()
        }
    }

    fun cleanup() {
        try {
            unregister()
            hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        } catch (e: Exception) {
            Log.e(TAG, "cleanup error", e)
        }
        executor.shutdown()
    }
}
