package com.hidble.phonekeyboard

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 跨页面共享的命令日志存储。
 * 主界面、按键页、连接页往这里追加；命令日志页订阅变化实时刷新。
 */
object LogStore {
    private const val MAX_LINES = 200

    private val lines = mutableListOf<String>()

    /** 页面订阅：有新增/清空时回调（应在 UI 线程） */
    var listener: (() -> Unit)? = null

    val size: Int get() = lines.size

    fun get(index: Int): String = lines[index]

    fun all(): List<String> = lines.toList()

    fun append(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        lines.add("[$timestamp] $message")
        if (lines.size > MAX_LINES) {
            lines.removeAt(0)
        }
        listener?.invoke()
    }

    fun clear() {
        lines.clear()
        listener?.invoke()
    }
}
