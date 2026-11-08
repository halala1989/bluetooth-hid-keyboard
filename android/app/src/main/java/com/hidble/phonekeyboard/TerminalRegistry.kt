package com.hidble.phonekeyboard

import android.bluetooth.BluetoothDevice
import android.content.Context

data class TerminalDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val protocol: String,
    val boardNameHints: List<String>,
    val isBoard: Boolean,
    val archived: Boolean = false
)

/**
 * 历史版本中实际出现过的输出终端。
 * 当前 App 只保留一个入口，由用户手动选择其中的终端。
 */
object TerminalRegistry {
    const val PHONE_HID = "phone_hid"
    const val PICO_W = "pico_w"
    const val ESP32_S3 = "esp32_s3"
    const val YD_ESP32_S3 = "yd_esp32_s3"
    const val ESP32_LEGACY = "esp32_legacy"

    private const val PREFS_NAME = "hidble_prefs"
    private const val KEY_SELECTED_TERMINAL = "selected_output_terminal"

    val all = listOf(
        TerminalDefinition(
            id = PHONE_HID,
            displayName = "手机蓝牙 HID",
            description = "手机直接注册为标准蓝牙键盘，不需要外接板",
            protocol = "BluetoothHidDevice",
            boardNameHints = emptyList(),
            isBoard = false
        ),
        TerminalDefinition(
            id = PICO_W,
            displayName = "Pico W",
            description = "手机通过 BLE 发送文本，Pico W 转成 USB HID 输出",
            protocol = "BLE 1234/1235/1236 → USB HID",
            boardNameHints = listOf("Pico"),
            isBoard = true
        ),
        TerminalDefinition(
            id = ESP32_S3,
            displayName = "ESP32-S3 Super Mini",
            description = "ESP32-S3 桥接固件，BLE 接收数据并通过 USB HID 输出",
            protocol = "BLE 1234/1235/1236 → USB HID",
            boardNameHints = listOf("ESP32-S3 Bridge", "ESP32-S3 Keyboard", "ESP32"),
            isBoard = true
        ),
        TerminalDefinition(
            id = YD_ESP32_S3,
            displayName = "YD-ESP32-S3 N16R8",
            description = "YD-ESP32-23-2022-V1.3，N16R8，双 Type-C 桥接板",
            protocol = "YD-ESP32-S3 Bridge → USB HID",
            boardNameHints = listOf("YD-ESP32-S3", "ESP32-S3 Bridge", "ESP32"),
            isBoard = true
        ),
        TerminalDefinition(
            id = ESP32_LEGACY,
            displayName = "ESP32-S3 旧 BLE HID",
            description = "早期 BLE HID 固件，保留用于兼容已烧录旧固件的板子",
            protocol = "BLE 数据服务 + BLE HID（兼容归档）",
            boardNameHints = listOf("ESP32-S3 Keyboard", "ESP32"),
            isBoard = true,
            archived = true
        )
    )

    fun byId(id: String?): TerminalDefinition =
        all.firstOrNull { it.id == id } ?: all.first()

    fun selected(context: Context): TerminalDefinition = byId(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_TERMINAL, PHONE_HID)
    )

    fun select(context: Context, terminal: TerminalDefinition) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_TERMINAL, terminal.id)
            .apply()
    }

    fun address(context: Context, terminal: TerminalDefinition): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(addressKey(terminal.id), null)

    fun saveAddress(context: Context, terminal: TerminalDefinition, device: BluetoothDevice) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(addressKey(terminal.id), device.address)
            .apply()
    }

    fun matches(terminal: TerminalDefinition, device: BluetoothDevice, name: String?): Boolean {
        if (!terminal.isBoard) return false
        val visibleName = try {
            name ?: device.name
        } catch (_: SecurityException) {
            name
        }.orEmpty()
        if (visibleName.isBlank()) return true
        return terminal.boardNameHints.any { visibleName.contains(it, ignoreCase = true) }
    }

    private fun addressKey(id: String) = "terminal_board_address_$id"
}
