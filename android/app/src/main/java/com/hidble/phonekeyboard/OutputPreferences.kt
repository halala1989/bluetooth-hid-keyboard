package com.hidble.phonekeyboard

import android.content.Context

data class OutputSettings(
    val speedLevel: Int = OutputPreferences.DEFAULT_SPEED_LEVEL,
    val unicodeMode: Int = TypingEngine.MODE_ALTX
) {
    val modeLabel: String
        get() = OutputPreferences.modeLabel(unicodeMode)
}

/** 手机 HID 与开发板共用的输出参数。 */
object OutputPreferences {
    const val PREFS_NAME = "hidble_prefs"
    const val KEY_SPEED_LEVEL = "speed_level"
    const val KEY_UNICODE_MODE = "unicode_mode"
    const val DEFAULT_SPEED_LEVEL = 5

    fun load(context: Context): OutputSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return OutputSettings(
            speedLevel = prefs.getInt(KEY_SPEED_LEVEL, DEFAULT_SPEED_LEVEL).coerceIn(1, 10),
            unicodeMode = prefs.getInt(KEY_UNICODE_MODE, TypingEngine.MODE_ALTX)
        )
    }

    fun save(context: Context, settings: OutputSettings) {
        val sanitized = settings.copy(speedLevel = settings.speedLevel.coerceIn(1, 10))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SPEED_LEVEL, sanitized.speedLevel)
            .putInt(KEY_UNICODE_MODE, sanitized.unicodeMode)
            .apply()
        KeyboardRuntime.hidProtocol()?.apply {
            setSpeed(sanitized.speedLevel)
            setUnicodeMode(sanitized.unicodeMode)
        }
        OutputRouter.applySettingsToBoard(context)
    }

    fun modeLabel(mode: Int): String = when (mode) {
        TypingEngine.MODE_HEX -> "十六进制"
        TypingEngine.MODE_DECIMAL -> "十进制"
        TypingEngine.MODE_GBK -> "GBK"
        else -> "Alt+X"
    }

    fun modeDescription(mode: Int): String = when (mode) {
        TypingEngine.MODE_HEX ->
            "十六进制：需要 Windows 开启 EnableHexNumpad 并保持 NumLock 开启，部分编辑器不支持。"
        TypingEngine.MODE_DECIMAL ->
            "十进制：使用 Alt + 0 + 十进制 Unicode 码点，适用于部分 RichEdit 编辑器。"
        TypingEngine.MODE_GBK ->
            "GBK：使用 GBK 机内码输出，仅适用于中文版 Windows，并需要保持 NumLock 开启。"
        else ->
            "Alt+X：输入 U+ 十六进制码后按 Alt+X，兼容记事本和 Word，无需开启 NumLock。"
    }
}
