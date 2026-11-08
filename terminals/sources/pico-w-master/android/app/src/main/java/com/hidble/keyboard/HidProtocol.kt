package com.hidble.keyboard

/**
 * HID 协议封装 - 提供便捷的键盘操作方法
 */
class HidProtocol(private val bleManager: BleManager) {
    
    /**
     * 输入文本
     */
    fun typeText(text: String) {
        bleManager.sendText(text)
    }
    
    /**
     * 按下按键
     */
    fun pressKey(key: SpecialKey) {
        bleManager.sendKey(key.name)
    }
    
    /**
     * 按下组合键
     */
    fun pressCombo(modifier: Modifier, key: SpecialKey) {
        bleManager.sendCombo(listOf(modifier.name), key.name)
    }
    
    /**
     * 按下组合键
     */
    fun pressCombo(modifiers: List<Modifier>, key: SpecialKey) {
        bleManager.sendCombo(modifiers.map { it.name }, key.name)
    }
    
    /**
     * 发送 Unicode 字符
     */
    fun sendUnicode(codepoint: Int) {
        bleManager.sendUnicode(codepoint)
    }

    /**
     * 设置中文/Unicode 输入模式（发送 UMOD 命令到固件）
     * 0 = 十进制 Unicode 码点（默认，记事本/写字板/Word 等 RichEdit 应用）
     * 1 = 十六进制（需目标机已开启 EnableHexNumpad 注册表项）
     * 2 = GBK 机内码（中文 Windows 下浏览器/聊天等绝大多数应用）
     */
    fun setUnicodeMode(mode: Int) {
        bleManager.sendCommand("UMOD:$mode")
    }

    /**
     * 设置输入速度（发送 SPEED 命令到固件）
     * 1 = 最慢，10 = 最快，5 = 默认
     */
    fun setSpeed(level: Int) {
        bleManager.sendCommand("SPEED:$level")
    }
    
    // 常用组合键快捷方法
    
    fun copy() = pressCombo(Modifier.CTRL, SpecialKey.C)
    fun paste() = pressCombo(Modifier.CTRL, SpecialKey.V)
    fun cut() = pressCombo(Modifier.CTRL, SpecialKey.X)
    fun selectAll() = pressCombo(Modifier.CTRL, SpecialKey.A)
    fun undo() = pressCombo(Modifier.CTRL, SpecialKey.Z)
    fun redo() = pressCombo(Modifier.CTRL, SpecialKey.Y)
    fun save() = pressCombo(Modifier.CTRL, SpecialKey.S)
    fun find() = pressCombo(Modifier.CTRL, SpecialKey.F)
    fun tab() = pressKey(SpecialKey.TAB)
    fun enter() = pressKey(SpecialKey.ENTER)
    fun backspace() = pressKey(SpecialKey.BACKSPACE)
    fun delete() = pressKey(SpecialKey.DELETE)
    fun escape() = pressKey(SpecialKey.ESCAPE)
    fun space() = pressKey(SpecialKey.SPACE)
    
    // 光标控制
    fun arrowUp() = pressKey(SpecialKey.UP)
    fun arrowDown() = pressKey(SpecialKey.DOWN)
    fun arrowLeft() = pressKey(SpecialKey.LEFT)
    fun arrowRight() = pressKey(SpecialKey.RIGHT)
    fun home() = pressKey(SpecialKey.HOME)
    fun end() = pressKey(SpecialKey.END)
    fun pageUp() = pressKey(SpecialKey.PAGEUP)
    fun pageDown() = pressKey(SpecialKey.PAGEDOWN)
    
    // 文本选择
    fun selectUp() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.UP)
    fun selectDown() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.DOWN)
    fun selectLeft() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.LEFT)
    fun selectRight() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.RIGHT)
    fun selectHome() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.HOME)
    fun selectEnd() = pressCombo(listOf(Modifier.SHIFT), SpecialKey.END)
    
    // Word 选择
    fun selectWordLeft() = pressCombo(listOf(Modifier.CTRL, Modifier.SHIFT), SpecialKey.LEFT)
    fun selectWordRight() = pressCombo(listOf(Modifier.CTRL, Modifier.SHIFT), SpecialKey.RIGHT)
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
