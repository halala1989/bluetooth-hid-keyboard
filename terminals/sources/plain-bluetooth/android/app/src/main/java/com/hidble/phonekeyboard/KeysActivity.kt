package com.hidble.phonekeyboard

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * 二级页面：更多按键（功能键 / 光标控制 / 组合键）。
 * 通过 MainActivity 转发 HID 操作，保持单一 HID 引擎实例。
 */
class KeysActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keys)

        fun onKey(label: String, action: suspend (HidProtocol) -> Unit) {
            LogStore.append(label)
            MainActivity.instance?.performHid(action)
        }

        // 功能键
        findViewById<Button>(R.id.btnEnter).setOnClickListener { onKey("Enter") { it.enter() } }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener { onKey("Backspace") { it.backspace() } }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { onKey("Delete") { it.delete() } }
        findViewById<Button>(R.id.btnTab).setOnClickListener { onKey("Tab") { it.tab() } }
        findViewById<Button>(R.id.btnEscape).setOnClickListener { onKey("Escape") { it.escape() } }

        // 光标控制
        findViewById<Button>(R.id.btnUp).setOnClickListener { onKey("↑") { it.arrowUp() } }
        findViewById<Button>(R.id.btnDown).setOnClickListener { onKey("↓") { it.arrowDown() } }
        findViewById<Button>(R.id.btnLeft).setOnClickListener { onKey("←") { it.arrowLeft() } }
        findViewById<Button>(R.id.btnRight).setOnClickListener { onKey("→") { it.arrowRight() } }
        findViewById<Button>(R.id.btnHome).setOnClickListener { onKey("Home") { it.home() } }
        findViewById<Button>(R.id.btnEnd).setOnClickListener { onKey("End") { it.end() } }

        // 组合键
        findViewById<Button>(R.id.btnCtrlC).setOnClickListener { onKey("Ctrl+C") { it.copy() } }
        findViewById<Button>(R.id.btnCtrlV).setOnClickListener { onKey("Ctrl+V") { it.paste() } }
        findViewById<Button>(R.id.btnCtrlX).setOnClickListener { onKey("Ctrl+X") { it.cut() } }
        findViewById<Button>(R.id.btnCtrlA).setOnClickListener { onKey("Ctrl+A") { it.selectAll() } }
        findViewById<Button>(R.id.btnCtrlZ).setOnClickListener { onKey("Ctrl+Z") { it.undo() } }
        findViewById<Button>(R.id.btnCtrlS).setOnClickListener { onKey("Ctrl+S") { it.save() } }
    }
}
