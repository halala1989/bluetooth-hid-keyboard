package com.hidble.phonekeyboard

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        @Suppress("DEPRECATION")
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        findViewById<TextView>(R.id.aboutVersion).text = "当前版本：$versionName"
        findViewById<Button>(R.id.aboutBackButton).setOnClickListener { finish() }
    }
}
