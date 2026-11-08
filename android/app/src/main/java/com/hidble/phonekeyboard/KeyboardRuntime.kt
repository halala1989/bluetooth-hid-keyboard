package com.hidble.phonekeyboard

import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

data class KeyboardSnapshot(
    val registered: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String? = null
)

/**
 * 全 App 共享的蓝牙 HID 会话。
 * 住院管理页面和原键盘工具页面使用同一个注册实例，避免重复占用系统 HID profile。
 */
object KeyboardRuntime {
    private const val PREFS_NAME = "hidble_prefs"
    private const val KEY_SPEED_LEVEL = "speed_level"
    private const val KEY_UNICODE_MODE = "unicode_mode"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var manager: HidDeviceManager? = null
    private var protocol: HidProtocol? = null
    private var typingEngine: TypingEngine? = null
    private var appContext: Context? = null
    private val stateListeners = CopyOnWriteArraySet<(KeyboardSnapshot) -> Unit>()

    @Volatile
    var registered: Boolean = false
        private set

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var connectedDeviceName: String? = null
        private set

    fun snapshot(): KeyboardSnapshot = KeyboardSnapshot(
        registered = registered,
        connected = connected,
        deviceName = connectedDeviceName
    )

    fun initialize(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (manager != null) return
            appContext = app
            val hidManager = HidDeviceManager(app)
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val engine = TypingEngine(
                send = { report -> hidManager.sendReport(report) },
                onSendInterrupted = { sent ->
                    LogStore.append("输出中断：电脑连接可能已断开（已输入 $sent 字）")
                }
            ).apply {
                speedLevel = prefs.getInt(KEY_SPEED_LEVEL, 5)
                unicodeMode = prefs.getInt(KEY_UNICODE_MODE, TypingEngine.MODE_ALTX)
                onGbkNumLockWarning = {
                    LogStore.append("GBK 模式需要目标电脑开启 NumLock")
                }
            }
            manager = hidManager
            protocol = HidProtocol(engine)
            typingEngine = engine

            hidManager.onAppStatusChanged = { isRegistered ->
                registered = isRegistered
                connected = if (isRegistered) connected else false
                connectedDeviceName = if (isRegistered) connectedDeviceName else null
                if (isRegistered) {
                    startForegroundService(app)
                } else {
                    stopForegroundService(app)
                }
                notifyState()
            }
            hidManager.onConnectionStateChanged = { device, state ->
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connected = true
                        connectedDeviceName = runCatching { device?.name }.getOrNull() ?: "目标电脑"
                        LogStore.append("已连接电脑：${connectedDeviceName}")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connected = false
                        connectedDeviceName = null
                        LogStore.append("电脑连接已断开")
                    }
                }
                notifyState()
            }
            hidManager.onError = { error ->
                LogStore.append("蓝牙错误：$error")
                notifyState()
            }
            hidManager.onLedState = { led -> protocol?.onLedReport(led) }
            hidManager.init()
        }
    }

    fun setStateListener(listener: ((KeyboardSnapshot) -> Unit)?) {
        stateListeners.clear()
        listener?.let { stateListeners.add(it) }
        listener?.invoke(snapshot())
    }

    fun addStateListener(listener: (KeyboardSnapshot) -> Unit) {
        stateListeners.add(listener)
        listener(snapshot())
    }

    fun removeStateListener(listener: (KeyboardSnapshot) -> Unit) {
        stateListeners.remove(listener)
    }

    fun hidManager(): HidDeviceManager? {
        manager?.init()
        return manager
    }

    fun hidProtocol(): HidProtocol? = protocol

    fun isBluetoothOn(): Boolean = manager?.isBluetoothOn() == true

    fun refreshConnection() {
        manager?.init()
        manager?.reconnectIfNeeded()
    }

    fun enable() {
        manager?.register()
    }

    fun disable() {
        manager?.unregister()
        manager?.disconnect()
        registered = false
        connected = false
        connectedDeviceName = null
        notifyState()
    }

    fun pairRequests(): List<Pair<String, android.bluetooth.BluetoothDevice>> {
        val result = mutableListOf<Pair<String, android.bluetooth.BluetoothDevice>>()
        manager?.bondedDevices()?.forEach { device ->
            result += (device.name ?: "未知设备") + " (${device.address})" to device
        }
        return result
    }

    fun lastComputerAddress(): String? = manager?.lastHostAddress()

    fun connectComputer(device: android.bluetooth.BluetoothDevice) {
        manager?.connect(device)
    }

    fun connect(device: android.bluetooth.BluetoothDevice) {
        manager?.connect(device)
    }

    fun sendText(text: String, onFinished: (Boolean, String) -> Unit) {
        val printer = protocol
        if (printer == null || !connected) {
            onFinished(false, "尚未连接到目标电脑")
            return
        }
        scope.launch {
            runCatching { printer.typeText(text) }
                .onSuccess { onFinished(true, "已发送 ${text.length} 字") }
                .onFailure { onFinished(false, it.message ?: "发送失败") }
        }
    }

    fun typeTextControlled(
        text: String,
        speed: Int,
        control: TextOutputControl,
        onFinished: (Boolean, String) -> Unit
    ) {
        val engine = typingEngine
        if (engine == null || !connected) {
            onFinished(false, "尚未连接到电脑")
            return
        }
        engine.speedLevel = speed.coerceIn(1, 10)
        scope.launch {
            runCatching { engine.typeText(text, control) }
                .onSuccess { onFinished(true, "输出完成") }
                .onFailure { error ->
                    if (control.cancelled) onFinished(false, "输出已停止")
                    else onFinished(false, error.message ?: "输出失败")
                }
        }
    }

    fun releaseAll() {
        protocol?.releaseAll()
    }

    private fun notifyState() {
        val state = snapshot()
        stateListeners.forEach { it(state) }
    }

    private fun startForegroundService(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HidKeyboardService::class.java)
            )
        }
    }

    private fun stopForegroundService(context: Context) {
        runCatching { context.stopService(Intent(context, HidKeyboardService::class.java)) }
    }
}
