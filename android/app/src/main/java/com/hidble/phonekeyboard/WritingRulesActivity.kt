package com.hidble.phonekeyboard

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WritingRulesActivity : AppCompatActivity() {

    private lateinit var skillName: TextView
    private lateinit var skillState: TextView
    private lateinit var skillPreview: TextView
    private lateinit var skillPreviewScroll: NestedScrollView
    private lateinit var skillPreviewCard: View
    private lateinit var skillRevisionInput: EditText
    private lateinit var skillRevisionState: TextView
    private lateinit var aiRefineSkillButton: Button
    private lateinit var rulesScroll: NestedScrollView
    private var pendingSkill: MedicalSkillParser.ImportedSkill? = null
    private var refineJob: Job? = null

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
        skillPreviewScroll = findViewById(R.id.skillPreviewScroll)
        skillPreviewCard = findViewById(R.id.skillPreviewCard)
        skillRevisionInput = findViewById(R.id.skillRevisionInput)
        skillRevisionState = findViewById(R.id.skillRevisionState)
        aiRefineSkillButton = findViewById(R.id.aiRefineSkillButton)
        rulesScroll = findViewById(R.id.rulesScroll)

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
                    refreshAiRevisionState()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        aiRefineSkillButton.setOnClickListener { refineSkillWithAi() }
        renderSavedSkill()
        refreshAiRevisionState()
    }

    private fun loadSkill(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { MedicalSkillParser.parse(this@WritingRulesActivity, uri) }
            }.onSuccess { skill ->
                pendingSkill = skill
                WritingSkillStore.save(this@WritingRulesActivity, skill)
                renderSkill(skill, saved = true)
                refreshAiRevisionState()
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

    private fun refreshAiRevisionState(message: String? = null, isError: Boolean = false) {
        val skill = WritingSkillStore.load(this)
        val prefs = getSharedPreferences(LlmPrefs.PREFS_NAME, MODE_PRIVATE)
        val provider = LlmProviders.byId(
            prefs.getString(LlmPrefs.KEY_PROVIDER, LlmProviders.list.first().id)
                ?: LlmProviders.list.first().id
        )
        val model = prefs.getString(LlmPrefs.KEY_MODEL, provider.defaultModel)
            ?.takeIf { it.isNotBlank() }
            ?: provider.defaultModel
        val hasToken = !prefs.getString(LlmPrefs.KEY_API_KEY, "").isNullOrBlank()
        aiRefineSkillButton.isEnabled = skill != null && hasToken && refineJob?.isActive != true
        skillRevisionInput.isEnabled = skill != null && refineJob?.isActive != true
        when {
            message != null -> {
                skillRevisionState.text = message
                skillRevisionState.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (isError) R.color.clinical_danger else R.color.clinical_teal
                    )
                )
            }
            skill == null -> {
                skillRevisionState.text = "请先导入 Markdown Skill，再使用 AI 修改。"
                skillRevisionState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            }
            !hasToken -> {
                skillRevisionState.text = "尚未配置 API Token，请先到“设置 → 模型”完成配置和连接测试。"
                skillRevisionState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            }
            else -> {
                skillRevisionState.text =
                    "将使用 ${provider.displayName} · $model，修改成功后直接覆盖当前 Skill。"
                skillRevisionState.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun refineSkillWithAi() {
        if (refineJob?.isActive == true) return
        val current = WritingSkillStore.load(this)
        if (current == null) {
            Toast.makeText(this, "请先导入 Markdown Skill", Toast.LENGTH_SHORT).show()
            return
        }
        val request = skillRevisionInput.text.toString().trim()
        if (request.isBlank()) {
            skillRevisionInput.requestFocus()
            skillRevisionInput.error = "请输入希望修改的内容"
            return
        }

        val prefs = getSharedPreferences(LlmPrefs.PREFS_NAME, MODE_PRIVATE)
        val provider = LlmProviders.byId(
            prefs.getString(LlmPrefs.KEY_PROVIDER, LlmProviders.list.first().id)
                ?: LlmProviders.list.first().id
        )
        val apiKey = prefs.getString(LlmPrefs.KEY_API_KEY, "").orEmpty().trim()
        if (apiKey.isBlank()) {
            refreshAiRevisionState()
            Toast.makeText(this, "请先到设置中配置 API Token", Toast.LENGTH_LONG).show()
            return
        }
        val model = prefs.getString(LlmPrefs.KEY_MODEL, provider.defaultModel)
            ?.takeIf { it.isNotBlank() }
            ?: provider.defaultModel

        aiRefineSkillButton.isEnabled = false
        aiRefineSkillButton.text = "AI 修改中…"
        skillRevisionInput.isEnabled = false
        skillRevisionState.text = "正在连接 ${provider.displayName} · $model 修改 Skill，请稍候…"
        skillRevisionState.setTextColor(ContextCompat.getColor(this, R.color.accent))

        refineJob = lifecycleScope.launch {
            try {
                val raw = LlmClient.chat(
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    messages = listOf(
                        "system" to SKILL_REFINE_SYSTEM_PROMPT,
                        "user" to buildSkillRefinePrompt(current, request)
                    )
                )
                val revised = extractRevisedSkill(raw)
                if (revised.isBlank()) error("AI 未返回有效的 Markdown")
                if (revised.length > MAX_SKILL_LENGTH) error("修改后的 Skill 超过 20 万字符")

                val updated = MedicalSkillParser.ImportedSkill(current.name, revised)
                WritingSkillStore.save(this@WritingRulesActivity, updated)
                pendingSkill = updated
                skillRevisionInput.setText("")
                renderSkill(updated, saved = true, previousContent = current.content)
                skillRevisionState.text = "AI 修改完成，已保存并全局应用。"
                skillRevisionState.setTextColor(
                    ContextCompat.getColor(this@WritingRulesActivity, R.color.clinical_teal)
                )
                Toast.makeText(
                    this@WritingRulesActivity,
                    "书写 Skill 已由 AI 修改并保存",
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                skillRevisionState.text =
                    "AI 修改失败：${error.message ?: "请检查网络、Token 或模型设置"}。当前 Skill 未修改。"
                skillRevisionState.setTextColor(
                    ContextCompat.getColor(this@WritingRulesActivity, R.color.clinical_danger)
                )
                Toast.makeText(
                    this@WritingRulesActivity,
                    "AI 修改失败，当前 Skill 未发生变化",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                aiRefineSkillButton.text = "调用 AI 修改并保存"
                aiRefineSkillButton.isEnabled = true
                skillRevisionInput.isEnabled = true
                refineJob = null
            }
        }
    }

    private fun buildSkillRefinePrompt(skill: WritingSkillStore.Skill, request: String): String = """
        当前 Markdown Skill：
        $BEGIN_MARKER
        ${skill.content}
        $END_MARKER

        用户希望进行的修改：
        $request

        请根据用户要求修改当前 Skill。保留未被要求改动的内容，不要删除必要的医疗文书栏目。
        不要加入患者姓名、住院号、身份证号、电话等可识别个人身份的信息。
        不得要求编造检查结果、诊断、手术方案或知情同意事实。
        只返回修改后的完整 Markdown Skill，并用下面两行标记包围，不要输出解释：
        $BEGIN_MARKER
        （这里放完整 Markdown）
        $END_MARKER
    """.trimIndent()

    private fun extractRevisedSkill(raw: String): String {
        val text = raw.trim().removePrefix("\uFEFF")
        val start = text.indexOf(BEGIN_MARKER)
        val end = text.indexOf(
            END_MARKER,
            startIndex = if (start >= 0) start + BEGIN_MARKER.length else 0
        )
        if (start >= 0) {
            if (end <= start) {
                error("AI 返回的 Markdown 不完整，缺少结束标记")
            }
            return text.substring(start + BEGIN_MARKER.length, end).trim()
        }
        val fenced = Regex(
            "^```(?:markdown|md)?\\s*\\n([\\s\\S]*?)\\n```\\s*$",
            RegexOption.IGNORE_CASE
        ).find(text)
        return (fenced?.groupValues?.getOrNull(1) ?: text).trim()
    }

    private fun renderSkill(
        skill: MedicalSkillParser.ImportedSkill,
        saved: Boolean,
        updatedAt: Long = System.currentTimeMillis(),
        previousContent: String? = null
    ) {
        skillName.text = skill.name
        skillState.text = if (saved) "已启用" else "待保存"
        skillState.setBackgroundResource(R.drawable.bg_status_chip)
        skillState.setTextColor(ContextCompat.getColor(this, R.color.clinical_teal))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(updatedAt))
        val header = "更新时间：$time\n字数：${skill.content.length}\n\n"
        val display = SpannableString(header + skill.content)
        val changedRange = previousContent
            ?.takeIf { it != skill.content }
            ?.let { findChangedRange(it, skill.content) }

        if (changedRange != null && !changedRange.isEmpty()) {
            val highlightStart = header.length + changedRange.first
            val highlightEnd = header.length + changedRange.last + 1
            display.setSpan(
                BackgroundColorSpan(
                    ColorUtils.setAlphaComponent(
                        ContextCompat.getColor(this, R.color.clinical_teal),
                        88
                    )
                ),
                highlightStart,
                highlightEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            display.setSpan(
                StyleSpan(Typeface.BOLD),
                highlightStart,
                highlightEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        skillPreview.text = display
        skillPreviewScroll.scrollTo(0, 0)

        if (changedRange != null && !changedRange.isEmpty()) {
            val highlightStart = header.length + changedRange.first
            skillPreviewScroll.post {
                scrollPreviewToTextOffset(highlightStart)
                rulesScroll.smoothScrollTo(
                    0,
                    (skillPreviewCard.top - 12).coerceAtLeast(0)
                )
            }
        }
    }

    private fun findChangedRange(oldText: String, newText: String): IntRange? {
        if (oldText == newText) return null
        val maxPrefix = minOf(oldText.length, newText.length)
        var prefix = 0
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++

        val maxSuffix = minOf(oldText.length - prefix, newText.length - prefix)
        var suffix = 0
        while (
            suffix < maxSuffix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) {
            suffix++
        }

        val start = prefix.coerceIn(0, newText.length)
        val endExclusive = (newText.length - suffix).coerceIn(start, newText.length)
        if (start == endExclusive) {
            val position = start.coerceAtMost((newText.length - 1).coerceAtLeast(0))
            return position..position
        }
        return start until endExclusive
    }

    private fun scrollPreviewToTextOffset(offset: Int, remainingRetries: Int = 4) {
        val layout = skillPreview.layout ?: run {
            if (remainingRetries > 0) {
                skillPreviewScroll.postDelayed(
                    { scrollPreviewToTextOffset(offset, remainingRetries - 1) },
                    50
                )
            }
            return
        }
        val safeOffset = offset.coerceIn(0, skillPreview.text.length)
        val line = layout.getLineForOffset(safeOffset)
        val targetY = (
            layout.getLineTop(line) +
                skillPreview.paddingTop -
                skillPreviewScroll.height / 3
            ).coerceAtLeast(0)
        skillPreviewScroll.smoothScrollTo(0, targetY)
    }

    companion object {
        private const val MAX_SKILL_LENGTH = 200_000
        private const val BEGIN_MARKER = "===BEGIN_WRITING_SKILL==="
        private const val END_MARKER = "===END_WRITING_SKILL==="
        private const val SKILL_REFINE_SYSTEM_PROMPT =
            "你是医疗文书书写规范编辑器。你的任务是修改用户提供的 Markdown 书写 Skill。" +
                "必须完整保留 Markdown 格式并返回完整修改后的 Skill，不得只返回差异。" +
                "不得添加患者隐私信息，不得降低文档来源一致性、禁止编造、医生审核等医疗安全要求。" +
                "不得把中医辨证内容改写成西医描述。只输出带指定标记的完整 Skill。"
    }
}
