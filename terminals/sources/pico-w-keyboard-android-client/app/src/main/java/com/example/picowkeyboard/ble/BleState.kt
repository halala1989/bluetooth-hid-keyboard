package com.example.picowkeyboard.ble

import android.bluetooth.BluetoothDevice

/** A discovered BLE peripheral shown in the scan list. */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: address
}

/** BLE connection state machine exposed to the UI. */
sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data object Scanning : BleConnectionState
    data object Connecting : BleConnectionState
    data class Connected(val device: BleDevice) : BleConnectionState
    data object Disconnecting : BleConnectionState
}

/** Send pipeline state machine exposed to the UI. */
sealed interface SendState {
    data object Idle : SendState
    data class Sending(val sentPackets: Int, val totalPackets: Int) : SendState
    data class Success(val totalBytes: Int, val totalPackets: Int) : SendState
    data class Failed(val reason: String) : SendState
    data object Cancelled : SendState
}
