package com.example.picowkeyboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.picowkeyboard.ble.BleConnectionState
import com.example.picowkeyboard.ble.BleDevice
import com.example.picowkeyboard.ble.BleManager
import com.example.picowkeyboard.ble.SendState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)

    private val prefs =
        application.getSharedPreferences("pico_w_prefs", Application.MODE_PRIVATE)

    /** Last successfully used device, for auto-reconnect on next launch. */
    private val _lastDevice = MutableStateFlow<BleDevice?>(loadLastDevice())
    val lastDevice: StateFlow<BleDevice?> = _lastDevice.asStateFlow()

    val scanResults: StateFlow<List<BleDevice>> = bleManager.scanResults
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val sendState: StateFlow<SendState> = bleManager.sendState

    val selectedDevice = MutableStateFlow<BleDevice?>(null)

    val text = MutableStateFlow("")

    /** Debug toggle: when false only devices named PICO-W-KEYBOARD* are shown. */
    val showAllDevices = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // Auto-reconnect to the last device once, without infinite retry.
            val last = _lastDevice.value
            if (last != null) {
                selectedDevice.value = last
                bleManager.connect(last)
            }
        }
    }

    // ------------------------------------------------------------------
    // Device & connection actions
    // ------------------------------------------------------------------

    fun startScan() {
        bleManager.startScan(showAllDevices.value)
    }

    fun stopScan() = bleManager.stopScan()

    fun selectDevice(device: BleDevice?) {
        selectedDevice.value = device
    }

    fun connectSelected() {
        val device = selectedDevice.value ?: return
        bleManager.connect(device)
        saveLastDevice(device)
    }

    fun disconnect() = bleManager.disconnect()

    fun isConnected(): Boolean =
        connectionState.value is BleConnectionState.Connected

    // ------------------------------------------------------------------
    // Text / send actions
    // ------------------------------------------------------------------

    fun updateText(newText: String) {
        text.value = newText
    }

    fun utf8Bytes(): ByteArray = text.value.toByteArray(Charsets.UTF_8)

    fun utf8Hex(limit: Int = 256): String {
        val bytes = utf8Bytes()
        val full = bytes.joinToString(" ") { "%02X".format(it) }
        return if (bytes.size > limit) full.take(limit * 3) + " …" else full
    }

    fun characterCount(): Int = text.value.length

    fun byteCount(): Int = utf8Bytes().size

    fun sendText() {
        val t = text.value
        if (t.isBlank()) return
        bleManager.sendText(t)
    }

    fun cancelSend() = bleManager.cancelSend()

    /** Largest single DATA payload under the current negotiated MTU. */
    fun currentMaxPayload(): Int = bleManager.currentMaxPayload()

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private fun saveLastDevice(device: BleDevice) {
        _lastDevice.value = device
        prefs.edit()
            .putString("last_device_address", device.address)
            .putString("last_device_name", device.name ?: "")
            .apply()
    }

    private fun loadLastDevice(): BleDevice? {
        val address = prefs.getString("last_device_address", null) ?: return null
        val name = prefs.getString("last_device_name", null)
        return BleDevice(address, name?.takeIf { it.isNotBlank() }, 0)
    }

    override fun onCleared() {
        bleManager.stopScan()
        bleManager.disconnect()
        super.onCleared()
    }
}
