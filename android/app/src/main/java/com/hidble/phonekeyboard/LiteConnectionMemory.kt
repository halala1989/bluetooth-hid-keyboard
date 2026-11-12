package com.hidble.phonekeyboard

import android.content.Context

object LiteConnectionMemory {
    private const val PREFS_NAME = "lite_connection_memory"
    private const val KEY_KIND = "kind"
    private const val KEY_ADDRESS = "address"
    private const val KEY_NAME = "name"
    private const val KEY_CONNECTED_AT = "connected_at"

    const val KIND_COMPUTER = "computer"
    const val KIND_BOARD = "board"

    data class Device(
        val kind: String,
        val address: String,
        val name: String,
        val connectedAt: Long
    )

    fun load(context: Context): Device? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val kind = prefs.getString(KEY_KIND, "").orEmpty()
        val address = prefs.getString(KEY_ADDRESS, "").orEmpty()
        if (kind.isBlank() || address.isBlank()) return null
        return Device(
            kind = kind,
            address = address,
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            connectedAt = prefs.getLong(KEY_CONNECTED_AT, 0L)
        )
    }

    fun save(
        context: Context,
        kind: String,
        address: String,
        name: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KIND, kind)
            .putString(KEY_ADDRESS, address)
            .putString(KEY_NAME, name)
            .putLong(KEY_CONNECTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun applyToRouter(context: Context, device: Device) {
        val terminal = if (device.kind == KIND_BOARD) {
            TerminalRegistry.byId(TerminalRegistry.ESP32_S3)
        } else {
            TerminalRegistry.byId(TerminalRegistry.PHONE_HID)
        }
        TerminalRegistry.select(context, terminal)
    }
}
