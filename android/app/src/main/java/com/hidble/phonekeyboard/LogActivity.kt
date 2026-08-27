package com.hidble.phonekeyboard

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 二级页面：命令日志。实时展示 LogStore 内容，自动滚到底部。
 */
class LogActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private var scrollView: ScrollView? = null
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        logView = findViewById(R.id.logView)
        scrollView = findViewById(R.id.logScroll)

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            LogStore.clear()
        }
        findViewById<Button>(R.id.closeLogButton).setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        listening = true
        LogStore.listener = { if (listening) refresh() }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        listening = false
        LogStore.listener = null
    }

    private fun refresh() {
        logView.text = LogStore.all().joinToString("\n").ifBlank { "暂无日志" }
        scrollView?.post {
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }
}
