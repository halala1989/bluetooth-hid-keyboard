package com.hidble.phonekeyboard

import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 统一输出路由：住院病例不再关心当前是哪块板子。
 * 手机蓝牙仍走 KeyboardRuntime；Pico/ESP32/YD-ESP32 使用历史代码中完全一致的 BLE 协议。
 */
object OutputRouter {
    private const val PREFS_NAME = "hidble_prefs"
    private const val KEY_SPEED_LEVEL = "speed_level"
    private const val KEY_UNICODE_MODE = "unicode_mode"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun selected(context: Context): TerminalDefinition = TerminalRegistry.selected(context)

    fun select(context: Context, terminal: TerminalDefinition) {
        TerminalRegistry.select(context, terminal)
        applySettingsToBoard(context)
    }

    fun isConnected(context: Context, terminal: TerminalDefinition): Boolean =
        if (terminal.id == TerminalRegistry.PHONE_HID) {
            KeyboardRuntime.snapshot().connected
        } else {
            val expected = TerminalRegistry.address(context, terminal)
            BoardLink.isConnected() && (expected == null || BoardLink.connectedAddress() == expected)
        }

    fun connectSavedBoard(context: Context, terminal: TerminalDefinition): Boolean {
        if (!terminal.isBoard) return false
        val address = TerminalRegistry.address(context, terminal) ?: return false
        return runCatching {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                ?: return false
            val device = adapter.getRemoteDevice(address)
            BoardLink.get(context).connect(device)
            true
        }.getOrDefault(false)
    }

    fun connectBoard(context: Context, terminal: TerminalDefinition, device: android.bluetooth.BluetoothDevice) {
        TerminalRegistry.saveAddress(context, terminal, device)
        BoardLink.get(context).connect(device)
    }

    fun sendText(
        context: Context,
        text: String,
        onFinished: (Boolean, String) -> Unit
    ) {
        val terminal = selected(context)
        if (terminal.id == TerminalRegistry.PHONE_HID) {
            KeyboardRuntime.sendText(text, onFinished)
            return
        }

        scope.launch {
            val manager = BoardLink.get(context.applicationContext)
            if (!manager.isConnected()) {
                val reconnected = reconnectBoardAndWait(context, terminal)
                if (!reconnected) {
                    onFinished(false, "${terminal.displayName} 未连接")
                    return@launch
                }
            }
            applySettingsToBoard(context)
            if (manager.isConnected()) {
                manager.sendText(text)
                onFinished(true, "已提交到 ${terminal.displayName}")
            } else {
                onFinished(false, "${terminal.displayName} 发送失败")
            }
        }
    }

    suspend fun sendTextAwait(context: Context, text: String): Pair<Boolean, String> =
        suspendCancellableCoroutine { continuation ->
            sendText(context, text) { success, message ->
                if (continuation.isActive) {
                    continuation.resume(success to message)
                }
            }
        }

    fun applySettingsToBoard(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        BoardLink.setSpeed(prefs.getInt(KEY_SPEED_LEVEL, 5))
        BoardLink.setUnicodeMode(prefs.getInt(KEY_UNICODE_MODE, TypingEngine.MODE_ALTX))
    }

    private suspend fun reconnectBoardAndWait(
        context: Context,
        terminal: TerminalDefinition,
        timeoutMs: Long = 6000
    ): Boolean {
        val address = TerminalRegistry.address(context, terminal) ?: return false
        val manager = BoardLink.get(context.applicationContext)
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: return false
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return false
        manager.connect(device)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (manager.isConnected()) return true
            delay(250)
        }
        return false
    }
}
