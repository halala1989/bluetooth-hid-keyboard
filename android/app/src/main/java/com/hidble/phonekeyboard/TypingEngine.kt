package com.hidble.phonekeyboard

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * HID 键位常量与查找（1:1 移植自 v11 固件 usb_hid.c）。
 */
object HidKeys {
    // 修饰键位掩码
    const val MOD_LEFTCTRL = 0x01
    const val MOD_LEFTSHIFT = 0x02
    const val MOD_LEFTALT = 0x04
    const val MOD_LEFTGUI = 0x08
    const val MOD_RIGHTCTRL = 0x10
    const val MOD_RIGHTSHIFT = 0x20
    const val MOD_RIGHTALT = 0x40
    const val MOD_RIGHTGUI = 0x80

    // 常用 HID Usage keycode
    const val KEY_A = 0x04
    const val KEY_Z = 0x1D
    const val KEY_1 = 0x1E
    const val KEY_9 = 0x26
    const val KEY_0 = 0x27
    const val KEY_X = 0x1B
    const val KEY_RETURN = 0x28
    const val KEY_ESCAPE = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_MINUS = 0x2D
    const val KEY_EQUAL = 0x2E
    const val KEY_LBRACKET = 0x2F
    const val KEY_RBRACKET = 0x30
    const val KEY_BACKSLASH = 0x31
    const val KEY_SEMICOLON = 0x33
    const val KEY_APOSTROPHE = 0x34
    const val KEY_GRAVE = 0x35
    const val KEY_COMMA = 0x36
    const val KEY_PERIOD = 0x37
    const val KEY_SLASH = 0x38
    const val KEY_CAPS_LOCK = 0x39
    const val KEY_F1 = 0x3A
    const val KEY_F12 = 0x45
    const val KEY_PRINT_SCREEN = 0x46
    const val KEY_SCROLL_LOCK = 0x47
    const val KEY_PAUSE = 0x48
    const val KEY_INSERT = 0x49
    const val KEY_HOME = 0x4A
    const val KEY_PAGE_UP = 0x4B
    const val KEY_DELETE = 0x4C
    const val KEY_END = 0x4D
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52
    const val KEY_NUM_LOCK = 0x53
    const val KEY_KP_DIVIDE = 0x54
    const val KEY_KP_MULTIPLY = 0x55
    const val KEY_KP_SUBTRACT = 0x56
    const val KEY_KP_PLUS = 0x57
    const val KEY_KP_ENTER = 0x58
    const val KEY_KP_1 = 0x59
    const val KEY_KP_9 = 0x61
    const val KEY_KP_0 = 0x62
    const val KEY_KP_DECIMAL = 0x63
    const val KEY_MENU = 0x65

    // 小键盘 0-9 的 HID keycode（Windows Alt 码输入必须用小键盘键位）
    val KP_DIGITS = intArrayOf(
        KEY_KP_0, KEY_KP_1, KEY_KP_1 + 1, KEY_KP_1 + 2, KEY_KP_1 + 3,
        KEY_KP_1 + 4, KEY_KP_1 + 5, KEY_KP_1 + 6, KEY_KP_1 + 7, KEY_KP_1 + 8
    )

    private val specialKeys = mapOf(
        "ENTER" to KEY_RETURN, "RETURN" to KEY_RETURN,
        "ESC" to KEY_ESCAPE, "ESCAPE" to KEY_ESCAPE,
        "BACKSPACE" to KEY_BACKSPACE, "BACK" to KEY_BACKSPACE,
        "TAB" to KEY_TAB, "SPACE" to KEY_SPACE,
        "CAPSLOCK" to KEY_CAPS_LOCK, "NUMLOCK" to KEY_NUM_LOCK, "SCROLLLOCK" to KEY_SCROLL_LOCK,
        "PRINTSCREEN" to KEY_PRINT_SCREEN, "PAUSE" to KEY_PAUSE, "INSERT" to KEY_INSERT,
        "HOME" to KEY_HOME, "PAGEUP" to KEY_PAGE_UP, "PAGEDOWN" to KEY_PAGE_DOWN,
        "DELETE" to KEY_DELETE, "DEL" to KEY_DELETE, "END" to KEY_END,
        "UP" to KEY_UP, "DOWN" to KEY_DOWN, "LEFT" to KEY_LEFT, "RIGHT" to KEY_RIGHT,
        "MENU" to KEY_MENU, "APP" to KEY_MENU,
        "KP_0" to KEY_KP_0, "KP_1" to KEY_KP_1, "KP_2" to KEY_KP_1 + 1,
        "KP_3" to KEY_KP_1 + 2, "KP_4" to KEY_KP_1 + 3, "KP_5" to KEY_KP_1 + 4,
        "KP_6" to KEY_KP_1 + 5, "KP_7" to KEY_KP_1 + 6, "KP_8" to KEY_KP_1 + 7,
        "KP_9" to KEY_KP_1 + 8, "KP_DECIMAL" to KEY_KP_DECIMAL,
        "KP_MULTIPLY" to KEY_KP_MULTIPLY, "KP_ADD" to KEY_KP_PLUS,
        "KP_SUBTRACT" to KEY_KP_SUBTRACT, "KP_DIVIDE" to KEY_KP_DIVIDE,
        "KP_ENTER" to KEY_KP_ENTER
    )

    private val modifiers = mapOf(
        "CTRL" to MOD_LEFTCTRL, "CONTROL" to MOD_LEFTCTRL,
        "LEFTCTRL" to MOD_LEFTCTRL, "RIGHTCTRL" to MOD_RIGHTCTRL,
        "SHIFT" to MOD_LEFTSHIFT, "LEFTSHIFT" to MOD_LEFTSHIFT, "RIGHTSHIFT" to MOD_RIGHTSHIFT,
        "ALT" to MOD_LEFTALT, "LEFTALT" to MOD_LEFTALT, "RIGHTALT" to MOD_RIGHTALT,
        "GUI" to MOD_LEFTGUI, "WIN" to MOD_LEFTGUI, "WINDOWS" to MOD_LEFTGUI,
        "LEFTGUI" to MOD_LEFTGUI, "RIGHTGUI" to MOD_RIGHTGUI
    )

    /** 按名称查特殊键 keycode（ENUM 名或协议名，均大写） */
    fun specialKeyCode(name: String): Int? {
        val upper = name.uppercase(Locale.ROOT)
        if (upper.length == 1) {
            val ch = upper[0]
            if (ch in 'A'..'Z') return KEY_A + (ch - 'A')
            if (ch in '1'..'9') return KEY_1 + (ch - '1')
            if (ch == '0') return KEY_0
        }
        if (upper.startsWith("F") && upper.length > 1) {
            val n = upper.substring(1).toIntOrNull()
            if (n != null && n in 1..12) return KEY_F1 + (n - 1)
        }
        if (upper.startsWith("NUM_")) {
            val n = upper.substring(4).toIntOrNull()
            if (n != null && n in 0..9) return if (n == 0) KEY_0 else KEY_1 + (n - 1)
        }
        return specialKeys[upper]
    }

    /** 按名称查修饰键掩码 */
    fun modifierMask(name: String): Int? = modifiers[name.uppercase(Locale.ROOT)]
}

/**
 * HID 打字引擎：把文本/按键/Unicode 转成标准键盘 HID 报告序列。
 * 逻辑 1:1 移植自 v11 固件 usb_hid.c（Alt+X/十六进制/十进制/GBK 四种中文输入 + SPEED 调速）。
 * 标准键盘报告：8 字节 [modifier, 0, key0..key5]。
 */
class TypingEngine(
    private val send: (ByteArray) -> Boolean,
    private val onSendInterrupted: ((Int) -> Unit)? = null
) {

    companion object {
        // 中文输入模式（与固件 UMOD 一致）
        const val MODE_DECIMAL = 0 // Alt + 0 + 十进制 Unicode 码点（RichEdit 应用）
        const val MODE_HEX = 1     // Alt + Numpad+ + 十六进制（需 EnableHexNumpad 注册表 + NumLock；Win11 记事本无效）
        const val MODE_GBK = 2     // Alt + 十进制 GBK 机内码（仅中文版 Windows）
        const val MODE_ALTX = 3    // 十六进制码 + Alt+X（Win11 记事本/Word 等；无需注册表/NumLock）——默认

        private const val KEY_DOWN_MS = 15
        private const val KEY_UP_MS = 15
        // 字符抬起后不再额外等待：下一键的按下延迟负责拉开间距
        private const val CHAR_GAP_MS = 0
        private const val ALT_FINAL_MS = 40
        private const val MIN_DELAY_MS = 1L
        /** 长文本自动分段：每段字符数 */
        const val CHUNK_SIZE = 80
        /** 段间暂停，让蓝牙发送缓冲消化，避免长文本高速发送时丢字 */
        private const val CHUNK_FLUSH_MS = 20L
        /** 单份报告发送失败的最大重试次数（约 1 秒），仍失败视为连接中断 */
        private const val SEND_MAX_RETRIES = 50
        private const val SEND_RETRY_WAIT_MS = 20L
        // 1=最慢 .. 10=最快；高速度档只保留 1-2ms 最小间隔（余下由蓝牙链路节奏决定）
        private val SPEED_SCALES = intArrayOf(2000, 1550, 1200, 900, 650, 450, 300, 180, 100, 60)

        private val gbkEncoder = Charset.forName("GBK").newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
    }

    @Volatile
    var unicodeMode: Int = MODE_ALTX

    @Volatile
    var speedLevel: Int = 5

    private var capsLock = false
    private val mutex = Mutex()

    /** 由 HID 输出报告（LED）更新键盘状态；bit1 = CapsLock */
    fun onLedReport(led: Int) {
        capsLock = (led and 0x02) != 0
    }

    /**
     * 输入一段文本（自动按当前中文模式逐字输出）。
     * 长文本自动分段发送（每 CHUNK_SIZE 字暂停一下让蓝牙缓冲消化）；
     * 报告发送失败自动重试，连接中断则停止并回调 onSendInterrupted。
     */
    suspend fun typeText(text: String) = mutex.withLock {
        val cps = text.codePoints().toArray()
        var sent = 0
        try {
            for (cp in cps) {
                if (cp == '\r'.code) continue
                if (cp == '\n'.code) {
                    queue(0, HidKeys.KEY_RETURN, KEY_DOWN_MS)
                    queue(0, 0, CHAR_GAP_MS)
                } else if (cp < 0x80) {
                    val hit = asciiToHid(cp.toChar())
                    if (hit != null) {
                        queue(hit.first, hit.second, KEY_DOWN_MS)
                        queue(0, 0, CHAR_GAP_MS)
                    } else {
                        typeUnicodeLocked(cp)
                    }
                } else {
                    typeUnicodeLocked(cp)
                }
                sent++
                if (sent % CHUNK_SIZE == 0) delay(CHUNK_FLUSH_MS)
            }
        } catch (e: HidSendInterruptedException) {
            onSendInterrupted?.invoke(sent)
        }
    }

    /** 按一个键（可带修饰键） */
    suspend fun pressKey(key: Int, modifiers: Int = 0) = mutex.withLock {
        try {
            queue(modifiers, key, KEY_DOWN_MS)
            queue(0, 0, KEY_UP_MS)
        } catch (e: HidSendInterruptedException) {
            // 发送失败（连接断开等）：忽略单键操作
        }
    }

    /** 直接输入一个 Unicode 码点（走当前中文模式） */
    suspend fun sendUnicode(codepoint: Int) = mutex.withLock {
        try {
            typeUnicodeLocked(codepoint)
        } catch (e: HidSendInterruptedException) {
            // 发送失败（连接断开等）：忽略
        }
    }

    private suspend fun typeUnicodeLocked(cp: Int) {
        when (unicodeMode) {
            MODE_ALTX -> {
                val digits = Integer.toHexString(cp).uppercase(Locale.ROOT)
                // 关键修复：先打 "U+" 前缀，再打十六进制码，最后 Alt+X。
                // Word/记事本会把光标前所有相邻十六进制字符读成一个码点；前面有数字/字母时
                // （如日期 "2025" 后面接 "5E74"）会被合并成超长无效码点导致不转换/乱码。
                // "U+" 是微软官方给出的消除歧义写法，转换时会被应用本身吃掉，不留残余。
                queue(0, 0, CHAR_GAP_MS)
                val u = asciiToHid('U') ?: return
                queue(u.first, u.second, KEY_DOWN_MS)
                queue(0, 0, CHAR_GAP_MS)
                val plus = asciiToHid('+') ?: return
                queue(plus.first, plus.second, KEY_DOWN_MS)
                queue(0, 0, CHAR_GAP_MS)
                for (ch in digits) {
                    val hit = asciiToHid(ch) ?: return
                    queue(hit.first, hit.second, KEY_DOWN_MS)
                    queue(0, 0, CHAR_GAP_MS)
                }
                queue(HidKeys.MOD_LEFTALT, HidKeys.KEY_X, KEY_DOWN_MS)
                queue(0, 0, ALT_FINAL_MS)
            }
            MODE_HEX -> {
                val digits = Integer.toHexString(cp).uppercase(Locale.ROOT)
                // Alt 与小键盘+ 合并进同一份报告，省 1 个报告/字
                queue(HidKeys.MOD_LEFTALT, HidKeys.KEY_KP_PLUS, KEY_DOWN_MS)
                queue(HidKeys.MOD_LEFTALT, 0, 0)
                for (ch in digits) {
                    val key = if (ch in '0'..'9') HidKeys.KP_DIGITS[ch - '0'] else HidKeys.KEY_A + (ch - 'A')
                    queue(HidKeys.MOD_LEFTALT, key, KEY_DOWN_MS)
                    // 数字抬起不再额外等待：蓝牙链路本身有传输间隔，下一键的按下延迟负责拉开间距
                    queue(HidKeys.MOD_LEFTALT, 0, 0)
                }
                queue(0, 0, ALT_FINAL_MS)
            }
            MODE_GBK -> {
                val gbk = gbkValue(cp)
                val digits = if (gbk != null) gbk.toString() else "0$cp"
                // Alt 与第一位数字合并发送，省 1 个报告/字
                for (ch in digits) {
                    queue(HidKeys.MOD_LEFTALT, HidKeys.KP_DIGITS[ch - '0'], KEY_DOWN_MS)
                    queue(HidKeys.MOD_LEFTALT, 0, 0)
                }
                queue(0, 0, ALT_FINAL_MS)
            }
            else -> { // MODE_DECIMAL：Alt+0+十进制（必须带前导 0，否则 Windows 按 ANSI 码页取模）
                val digits = "0$cp"
                // Alt 与第一位数字（前导 0）合并发送，省 1 个报告/字
                for (ch in digits) {
                    queue(HidKeys.MOD_LEFTALT, HidKeys.KP_DIGITS[ch - '0'], KEY_DOWN_MS)
                    queue(HidKeys.MOD_LEFTALT, 0, 0)
                }
                queue(0, 0, ALT_FINAL_MS)
            }
        }
    }

    /** GBK(CP936) 机内码：(lead<<8)|trail；无法映射返回 null（回退 "0"+码点） */
    private fun gbkValue(cp: Int): Int? {
        if (cp < 0 || cp > 0x10FFFF) return null
        val bytes = try {
            val buf = gbkEncoder.encode(CharBuffer.wrap(String(Character.toChars(cp))))
            val arr = ByteArray(buf.remaining())
            buf.get(arr)
            arr
        } catch (e: CharacterCodingException) {
            return null
        }
        return when (bytes.size) {
            2 -> ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            1 -> bytes[0].toInt() and 0xFF
            else -> null
        }
    }

    /** ASCII 字符 -> (修饰键, keycode)；与固件 ascii_to_hid 一致，考虑 CapsLock */
    private fun asciiToHid(ch: Char): Pair<Int, Int>? {
        var modifier = 0
        var key = 0
        when {
            ch in 'a'..'z' -> {
                key = HidKeys.KEY_A + (ch - 'a')
                if (capsLock) modifier = HidKeys.MOD_LEFTSHIFT
            }
            ch in 'A'..'Z' -> {
                key = HidKeys.KEY_A + (ch - 'A')
                if (!capsLock) modifier = HidKeys.MOD_LEFTSHIFT
            }
            ch in '1'..'9' -> key = HidKeys.KEY_1 + (ch - '1')
            else -> when (ch) {
                '0' -> key = HidKeys.KEY_0
                ' ' -> key = HidKeys.KEY_SPACE
                '-' -> key = HidKeys.KEY_MINUS
                '=' -> key = HidKeys.KEY_EQUAL
                '[' -> key = HidKeys.KEY_LBRACKET
                ']' -> key = HidKeys.KEY_RBRACKET
                '\\' -> key = HidKeys.KEY_BACKSLASH
                ';' -> key = HidKeys.KEY_SEMICOLON
                '\'' -> key = HidKeys.KEY_APOSTROPHE
                '`' -> key = HidKeys.KEY_GRAVE
                ',' -> key = HidKeys.KEY_COMMA
                '.' -> key = HidKeys.KEY_PERIOD
                '/' -> key = HidKeys.KEY_SLASH
                '\t' -> key = HidKeys.KEY_TAB
                '\b' -> key = HidKeys.KEY_BACKSPACE
                '!' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 }
                '@' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 + 1 }
                '#' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 + 2 }
                '$' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 + 3 }
                '%' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 + 4 }
                '^' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 + 5 }
                '&' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 + 6 }
                '*' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 + 7 }
                '(' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_1 + 8 }
                ')' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_0 }
                '_' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_MINUS }
                '+' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_EQUAL }
                '{' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_LBRACKET }
                '}' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_RBRACKET }
                '|' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_BACKSLASH }
                ':' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_SEMICOLON }
                '"' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_APOSTROPHE }
                '~' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_GRAVE }
                '<' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_COMMA }
                '>' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_PERIOD }
                '?' -> { modifier = HidKeys.MOD_LEFTSHIFT; key = HidKeys.KEY_SLASH }
                else -> return null
            }
        }
        return Pair(modifier, key)
    }

    /** 发送一份 8 字节报告并等待（速度缩放）；发送失败自动重试，中断则抛异常终止本次输入 */
    private suspend fun queue(modifier: Int, key: Int, delayMs: Int) {
        val report = ByteArray(8)
        report[0] = modifier.toByte()
        report[2] = key.toByte()
        sendReportWithRetry(report)
        delay(scaled(delayMs))
    }

    private suspend fun sendReportWithRetry(report: ByteArray) {
        var attempts = 0
        while (!send(report)) {
            attempts++
            if (attempts >= SEND_MAX_RETRIES) throw HidSendInterruptedException()
            delay(SEND_RETRY_WAIT_MS)
        }
    }

    private fun scaled(ms: Int): Long {
        val idx = speedLevel.coerceIn(1, 10) - 1
        val v = ms.toLong() * SPEED_SCALES[idx] / 1000L
        return if (v < MIN_DELAY_MS) MIN_DELAY_MS else v
    }

    /** 发送彻底失败（连接中断）时抛出，用于终止长文本输入 */
    private class HidSendInterruptedException : Exception()
}
