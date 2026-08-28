package com.hidble.phonekeyboard

/**
 * HID 协议封装 - 基于手机端打字引擎（不再需要 Pico W / BLE）
 */
class HidProtocol(private val engine: TypingEngine) {

    /** 由 HID 输出报告（LED）更新键盘状态（CapsLock 等） */
    fun onLedReport(led: Int) {
        engine.onLedReport(led)
    }

    /** 输入文本 */
    suspend fun typeText(text: String) {
        engine.typeText(text)
    }

    /** 按下按键 */
    suspend fun pressKey(key: SpecialKey) {
        val code = HidKeys.specialKeyCode(key.name) ?: return
        engine.pressKey(code)
    }

    /** 按下组合键 */
    suspend fun pressCombo(modifier: Modifier, key: SpecialKey) {
        pressCombo(listOf(modifier), key)
    }

    /** 按下组合键 */
    suspend fun pressCombo(modifiers: List<Modifier>, key: SpecialKey) {
        var mask = 0
        for (m in modifiers) {
            mask = mask or (HidKeys.modifierMask(m.name) ?: 0)
        }
        val code = HidKeys.specialKeyCode(key.name) ?: return
        engine.pressKey(code, mask)
    }

    /** 发送 Unicode 字符（走当前中文模式） */
    suspend fun sendUnicode(codepoint: Int) {
        engine.sendUnicode(codepoint)
    }

    /** 设置中文/Unicode 输入模式（与固件 UMOD 一致） */
    fun setUnicodeMode(mode: Int) {
        engine.unicodeMode = mode
    }

    /** 设置输入速度 1=最慢 .. 10=最快（默认 5） */
    fun setSpeed(level: Int) {
        engine.speedLevel = level
    }

    /** 发送全释放报告，清空目标端可能残留的按键/修饰键状态 */
    fun releaseAll() {
        engine.releaseAll()
    }

    // 常用组合键快捷方法
    suspend fun copy() = pressCombo(Modifier.CTRL, SpecialKey.C)
    suspend fun paste() = pressCombo(Modifier.CTRL, SpecialKey.V)
    suspend fun cut() = pressCombo(Modifier.CTRL, SpecialKey.X)
    suspend fun selectAll() = pressCombo(Modifier.CTRL, SpecialKey.A)
    suspend fun undo() = pressCombo(Modifier.CTRL, SpecialKey.Z)
    suspend fun redo() = pressCombo(Modifier.CTRL, SpecialKey.Y)
    suspend fun save() = pressCombo(Modifier.CTRL, SpecialKey.S)
    suspend fun find() = pressCombo(Modifier.CTRL, SpecialKey.F)
    suspend fun tab() = pressKey(SpecialKey.TAB)
    suspend fun enter() = pressKey(SpecialKey.ENTER)
    suspend fun backspace() = pressKey(SpecialKey.BACKSPACE)
    suspend fun delete() = pressKey(SpecialKey.DELETE)
    suspend fun escape() = pressKey(SpecialKey.ESCAPE)
    suspend fun space() = pressKey(SpecialKey.SPACE)

    // 光标控制
    suspend fun arrowUp() = pressKey(SpecialKey.UP)
    suspend fun arrowDown() = pressKey(SpecialKey.DOWN)
    suspend fun arrowLeft() = pressKey(SpecialKey.LEFT)
    suspend fun arrowRight() = pressKey(SpecialKey.RIGHT)
    suspend fun home() = pressKey(SpecialKey.HOME)
    suspend fun end() = pressKey(SpecialKey.END)
    suspend fun pageUp() = pressKey(SpecialKey.PAGEUP)
    suspend fun pageDown() = pressKey(SpecialKey.PAGEDOWN)

    // 文本选择
    suspend fun selectUp() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.UP)
    suspend fun selectDown() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.DOWN)
    suspend fun selectLeft() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.LEFT)
    suspend fun selectRight() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.RIGHT)
    suspend fun selectHome() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.HOME)
    suspend fun selectEnd() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.END)

    // Word 选择
    suspend fun selectWordLeft() = pressCombo(listOf(Modifier.CTRL, Modifier.SHIFT), SpecialKey.LEFT)
    suspend fun selectWordRight() = pressCombo(listOf(Modifier.CTRL, Modifier.SHIFT), SpecialKey.RIGHT)
}

/**
 * 特殊按键枚举
 */
enum class SpecialKey {
    ENTER, BACKSPACE, DELETE, TAB, ESCAPE,
    CAPSLOCK, NUMLOCK, SCROLLLOCK,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    UP, DOWN, LEFT, RIGHT,
    HOME, END, PAGEUP, PAGEDOWN,
    INSERT, PRINTSCREEN, PAUSE, MENU,
    SPACE,
    // 数字小键盘
    KP_0, KP_1, KP_2, KP_3, KP_4, KP_5, KP_6, KP_7, KP_8, KP_9,
    KP_DECIMAL, KP_MULTIPLY, KP_ADD, KP_SUBTRACT, KP_DIVIDE, KP_ENTER,
    // 字母和符号
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9, NUM_0;

    companion object {
        fun fromString(key: String): SpecialKey? {
            return try {
                valueOf(key.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * 修饰键枚举
 */
enum class Modifier {
    CTRL, SHIFT, ALT, GUI,
    LEFTCTRL, LEFTSHIFT, LEFTALT, LEFTGUI,
    RIGHTCTRL, RIGHTSHIFT, RIGHTALT, RIGHTGUI;

    companion object {
        fun fromString(mod: String): Modifier? {
            return try {
                valueOf(mod.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
