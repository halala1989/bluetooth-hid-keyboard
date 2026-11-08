package com.hidble.phonekeyboard

import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WritingRulesActivity : AppCompatActivity() {

    private lateinit var skillName: TextView
    private lateinit var skillState: TextView
    private lateinit var skillPreview: TextView
    private var pendingSkill: MedicalSkillParser.ImportedSkill? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        loadSkill(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_writing_rules)
        skillName = findViewById(R.id.skillName)
        skillState = findViewById(R.id.skillState)
        skillPreview = findViewById(R.id.skillPreview)
        skillPreview.movementMethod = ScrollingMovementMethod()

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.importSkillButton).setOnClickListener {
            picker.launch(arrayOf("text/markdown", "text/plain", "text/*", "application/octet-stream"))
        }
        findViewById<Button>(R.id.clearSkillButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空书写规范？")
                .setMessage("所有病历模块将恢复为系统默认规则。")
                .setPositiveButton("清空") { _, _ ->
                    WritingSkillStore.clear(this)
                    pendingSkill = null
                    renderSavedSkill()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        renderSavedSkill()
    }

    private fun loadSkill(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { MedicalSkillParser.parse(this@WritingRulesActivity, uri) }
            }.onSuccess { skill ->
                pendingSkill = skill
                WritingSkillStore.save(this@WritingRulesActivity, skill)
                renderSkill(skill, saved = true)
                Toast.makeText(this@WritingRulesActivity, "书写规范已保存并全局应用", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this@WritingRulesActivity, it.message ?: "Skill 导入失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderSavedSkill() {
        val saved = WritingSkillStore.load(this)
        if (saved == null) {
            skillName.text = "未配置 Markdown Skill"
            skillState.text = "未导入"
            skillState.setBackgroundResource(R.drawable.bg_warning_chip)
            skillState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            skillPreview.text = "尚未导入 Markdown Skill。"
            return
        }
        renderSkill(MedicalSkillParser.ImportedSkill(saved.name, saved.content), saved = true, saved.updatedAt)
    }

    private fun renderSkill(
        skill: MedicalSkillParser.ImportedSkill,
        saved: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        skillName.text = skill.name
        skillState.text = if (saved) "已启用" else "待保存"
        skillState.setBackgroundResource(R.drawable.bg_status_chip)
        skillState.setTextColor(ContextCompat.getColor(this, R.color.clinical_teal))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(updatedAt))
        skillPreview.text = "更新时间：$time\n字数：${skill.content.length}\n\n${skill.content}"
    }
}
