package com.hidble.phonekeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.ScrollingMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WritingRulesActivity : AppCompatActivity() {

    private lateinit var skillName: TextView
    private lateinit var skillState: TextView
    private lateinit var skillPreview: TextView
    private lateinit var skillPreviewCard: View
    private lateinit var skillRevisionInput: EditText
    private lateinit var skillRevisionState: TextView
    private lateinit var aiRefineSkillButton: Button
    private lateinit var rulesScroll: ScrollView
    private lateinit var skillLibraryContainer: LinearLayout
    private lateinit var skillLibraryState: TextView
    private lateinit var exportSelectedButton: Button

    private var skills: List<WritingSkillStore.Skill> = emptyList()
    private var previewSkillId: String? = null
    private var refineJob: Job? = null
    private var pendingExport: PendingExport? = null

    private lateinit var skillPicker: ActivityResultLauncher<Array<String>>
    private lateinit var singleSkillExporter: ActivityResultLauncher<String>
    private lateinit var skillArchiveExporter: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            skillPicker = registerForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) importSkills(uris)
            }
            singleSkillExporter = registerForActivityResult(
                ActivityResultContracts.CreateDocument("text/markdown")
            ) { uri ->
                if (uri == null) pendingExport = null else writePendingExport(uri)
            }
            skillArchiveExporter = registerForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri == null) pendingExport = null else writePendingExport(uri)
            }
            initializePage()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to initialize the writing rules page", error)
            showRecoveryPage(error)
        }
    }

    private fun initializePage() {
        setContentView(R.layout.activity_writing_rules)
        skillName = findViewById(R.id.skillName)
        skillState = findViewById(R.id.skillState)
        skillPreview = findViewById(R.id.skillPreview)
        skillPreview.movementMethod = ScrollingMovementMethod()
        skillPreviewCard = findViewById(R.id.skillPreviewCard)
        skillRevisionInput = findViewById(R.id.skillRevisionInput)
        skillRevisionState = findViewById(R.id.skillRevisionState)
        aiRefineSkillButton = findViewById(R.id.aiRefineSkillButton)
        rulesScroll = findViewById(R.id.rulesScroll)
        skillLibraryContainer = findViewById(R.id.skillLibraryContainer)
        skillLibraryState = findViewById(R.id.skillLibraryState)
        exportSelectedButton = findViewById(R.id.exportSelectedButton)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.importSkillButton).setOnClickListener {
            skillPicker.launch(
                arrayOf(
                    "text/markdown",
                    "text/plain",
                    "text/*",
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
                )
            )
        }
        exportSelectedButton.setOnClickListener { exportSelectedSkills() }
        findViewById<Button>(R.id.clearSkillButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("删除全部 Skill？")
                .setMessage("Skill 库中的全部内容都会被删除，所有病历模块将恢复为系统默认规则。")
                .setPositiveButton("删除") { _, _ ->
                    WritingSkillStore.clear(this)
                    previewSkillId = null
                    refreshSkillLibrary()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        aiRefineSkillButton.setOnClickListener { refineSkillWithAi() }
        refreshSkillLibrary()
    }

    private fun refreshSkillLibrary(
        selectId: String? = previewSkillId,
        previousContent: String? = null
    ) {
        skills = WritingSkillStore.all(this)
        previewSkillId = selectId
            ?.takeIf { id -> skills.any { it.id == id } }
            ?: skills.firstOrNull { it.enabled }?.id
            ?: skills.firstOrNull()?.id

        val selectedCount = skills.count { it.enabled }
        skillLibraryState.text = if (skills.isEmpty()) {
            "0 个 Skill"
        } else {
            "${skills.size} 个 · 已启用 $selectedCount 个"
        }
        exportSelectedButton.isEnabled = selectedCount > 0
        renderSkillList()
        renderSelectedSkill(previousContent)
        refreshAiRevisionState()
    }

    private fun renderSkillList() {
        skillLibraryContainer.removeAllViews()
        if (skills.isEmpty()) {
            skillLibraryContainer.addView(TextView(this).apply {
                text = "还没有 Skill。点击下方“导入 Skill”，可一次选择多个 Markdown 文件或 ZIP 包。"
                setTextColor(ContextCompat.getColor(context, R.color.text_hint))
                textSize = 10f
                setPadding(4, 12, 4, 8)
            })
            return
        }

        val inflater = LayoutInflater.from(this)
        skills.forEach { skill ->
            val row = inflater.inflate(R.layout.item_writing_skill, skillLibraryContainer, false)
            val enabledCheck = row.findViewById<CheckBox>(R.id.skillEnabledCheck)
            enabledCheck.isChecked = skill.enabled
            enabledCheck.setOnCheckedChangeListener { _, checked ->
                WritingSkillStore.setEnabled(this, skill.id, checked)
                refreshSkillLibrary(selectId = skill.id)
            }
            row.findViewById<TextView>(R.id.skillItemName).text = skill.name
            row.findViewById<TextView>(R.id.skillItemMeta).text = buildString {
                append(if (skill.enabled) "已启用" else "未启用")
                append(" · ${skill.content.length} 字")
                append(" · 更新于 ")
                append(formatTime(skill.updatedAt))
            }
            row.setBackgroundResource(
                if (skill.id == previewSkillId) R.drawable.bg_status_chip else R.drawable.bg_log
            )
            row.setOnClickListener {
                refreshSkillLibrary(selectId = skill.id)
            }
            row.findViewById<Button>(R.id.skillExportButton).setOnClickListener {
                requestExportSkill(skill)
            }
            row.findViewById<Button>(R.id.skillDeleteButton).setOnClickListener {
                confirmDeleteSkill(skill)
            }
            skillLibraryContainer.addView(row)
        }
    }

    private fun renderSelectedSkill(previousContent: String? = null) {
        val current = skills.firstOrNull { it.id == previewSkillId }
        if (current == null) {
            skillName.text = "未配置 Markdown Skill"
            skillState.text = "未启用"
            skillState.setBackgroundResource(R.drawable.bg_warning_chip)
            skillState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            skillPreview.text = "尚未导入 Markdown Skill。"
            return
        }

        skillName.text = current.name
        skillState.text = if (current.enabled) "已启用" else "未启用"
        skillState.setBackgroundResource(
            if (current.enabled) R.drawable.bg_status_chip else R.drawable.bg_warning_chip
        )
        skillState.setTextColor(
            ContextCompat.getColor(
                this,
                if (current.enabled) R.color.clinical_teal else R.color.clinical_warning
            )
        )
        val selectedCount = skills.count { it.enabled }
        val header = buildString {
            append("当前预览：${current.name}\n")
            append("已启用 $selectedCount / ${skills.size} 个 Skill\n")
            append("更新时间：${formatTime(current.updatedAt)}\n")
            append("字数：${current.content.length}\n\n")
        }
        val display = SpannableString(header + current.content)
        val changedRange = previousContent
            ?.takeIf { it != current.content }
            ?.let { findChangedRange(it, current.content) }

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
        skillPreview.scrollTo(0, 0)
        if (changedRange != null && !changedRange.isEmpty()) {
            val highlightStart = header.length + changedRange.first
            skillPreview.post {
                scrollPreviewToTextOffset(highlightStart)
                rulesScroll.smoothScrollTo(
                    0,
                    (skillPreviewCard.top - 12).coerceAtLeast(0)
                )
            }
        }
    }

    private fun confirmDeleteSkill(skill: WritingSkillStore.Skill) {
        AlertDialog.Builder(this)
            .setTitle("删除 Skill？")
            .setMessage("将删除“${skill.name}”。此操作不会影响其他 Skill。")
            .setPositiveButton("删除") { _, _ ->
                WritingSkillStore.delete(this, skill.id)
                if (previewSkillId == skill.id) previewSkillId = null
                refreshSkillLibrary()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importSkills(uris: List<Uri>) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                var importedCount = 0
                var failedCount = 0
                var firstImportedId: String? = null
                uris.forEach { uri ->
                    runCatching {
                        MedicalSkillParser.parseAll(this@WritingRulesActivity, uri)
                    }.onSuccess { parsed ->
                        parsed.forEach { imported ->
                            runCatching {
                                WritingSkillStore.save(this@WritingRulesActivity, imported)
                            }.onSuccess { saved ->
                                importedCount++
                                if (firstImportedId == null) firstImportedId = saved.id
                            }.onFailure {
                                failedCount++
                                Log.e(TAG, "Unable to save imported skill", it)
                            }
                        }
                    }.onFailure {
                        failedCount++
                        Log.e(TAG, "Unable to parse imported skill", it)
                    }
                }
                ImportResult(importedCount, failedCount, firstImportedId)
            }
            previewSkillId = result.firstImportedId ?: previewSkillId
            refreshSkillLibrary(selectId = previewSkillId)
            val message = when {
                result.imported == 0 -> "没有导入任何 Skill，请检查文件格式"
                result.failed == 0 -> "已导入 ${result.imported} 个 Skill，并默认启用"
                else -> "已导入 ${result.imported} 个 Skill，${result.failed} 个文件失败"
            }
            Toast.makeText(this@WritingRulesActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshAiRevisionState(message: String? = null, isError: Boolean = false) {
        val current = skills.firstOrNull { it.id == previewSkillId }
        val prefs = getSharedPreferences(LlmPrefs.PREFS_NAME, MODE_PRIVATE)
        val provider = LlmProviders.byId(
            prefs.readString(LlmPrefs.KEY_PROVIDER, LlmProviders.list.first().id)
        )
        val model = prefs.readString(LlmPrefs.KEY_MODEL, provider.defaultModel)
            .takeIf { it.isNotBlank() } ?: provider.defaultModel
        val hasToken = prefs.readString(LlmPrefs.KEY_API_KEY, "").isNotBlank()
        aiRefineSkillButton.isEnabled =
            current != null && hasToken && refineJob?.isActive != true
        skillRevisionInput.isEnabled = current != null && refineJob?.isActive != true
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
            current == null -> {
                skillRevisionState.text = "请先导入并选择一个 Skill，再使用 AI 修改。"
                skillRevisionState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            }
            !hasToken -> {
                skillRevisionState.text = "尚未配置 API Token，请先到“设置 → 模型”完成配置和连接测试。"
                skillRevisionState.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            }
            else -> {
                skillRevisionState.text =
                    "将使用 ${provider.displayName} · $model 修改当前预览：${current.name}。"
                skillRevisionState.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun refineSkillWithAi() {
        if (refineJob?.isActive == true) return
        val current = skills.firstOrNull { it.id == previewSkillId }
        if (current == null) {
            Toast.makeText(this, "请先选择一个 Skill", Toast.LENGTH_SHORT).show()
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
            prefs.readString(LlmPrefs.KEY_PROVIDER, LlmProviders.list.first().id)
        )
        val apiKey = prefs.readString(LlmPrefs.KEY_API_KEY, "").trim()
        if (apiKey.isBlank()) {
            refreshAiRevisionState()
            Toast.makeText(this, "请先到设置中配置 API Token", Toast.LENGTH_LONG).show()
            return
        }
        val model = prefs.readString(LlmPrefs.KEY_MODEL, provider.defaultModel)
            .takeIf { it.isNotBlank() } ?: provider.defaultModel

        aiRefineSkillButton.isEnabled = false
        aiRefineSkillButton.text = "AI 修改中…"
        skillRevisionInput.isEnabled = false
        skillRevisionState.text =
            "正在连接 ${provider.displayName} · $model 修改“${current.name}”，请稍候…"
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

                WritingSkillStore.updateContent(this@WritingRulesActivity, current.id, revised)
                skillRevisionInput.setText("")
                refreshSkillLibrary(selectId = current.id, previousContent = current.content)
                refreshAiRevisionState("AI 修改完成，已保存当前 Skill。", false)
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

    private fun requestExportSkill(skill: WritingSkillStore.Skill) {
        pendingExport = PendingExport(
            content = skill.content.toByteArray(Charsets.UTF_8),
            description = skill.name
        )
        singleSkillExporter.launch(
            "${WritingSkillStore.safeExportName(skill.name)}.md"
        )
    }

    private fun exportSelectedSkills() {
        val selected = skills.filter { it.enabled }
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先勾选至少一个 Skill", Toast.LENGTH_SHORT).show()
            return
        }
        if (selected.size == 1) {
            requestExportSkill(selected.first())
            return
        }

        lifecycleScope.launch {
            val archive = withContext(Dispatchers.IO) { buildSkillArchive(selected) }
            if (archive.isEmpty()) {
                Toast.makeText(this@WritingRulesActivity, "生成导出包失败", Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingExport = PendingExport(
                content = archive,
                description = "${selected.size} 个 Skill"
            )
            skillArchiveExporter.launch("医键书写规范-已选${selected.size}项.zip")
        }
    }

    private fun buildSkillArchive(skills: List<WritingSkillStore.Skill>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val usedNames = mutableSetOf<String>()
            skills.forEach { skill ->
                var baseName = WritingSkillStore.safeExportName(skill.name)
                var fileName = "$baseName.md"
                var suffix = 2
                while (!usedNames.add(fileName.lowercase())) {
                    fileName = "$baseName-$suffix.md"
                    suffix++
                }
                zip.putNextEntry(ZipEntry(fileName))
                zip.write(skill.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun writePendingExport(uri: Uri) {
        val export = pendingExport ?: return
        pendingExport = null
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(export.content)
                    } ?: error("无法打开导出文件")
                }
            }.onSuccess {
                Toast.makeText(
                    this@WritingRulesActivity,
                    "已导出${export.description}",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                Log.e(TAG, "Unable to export writing skill", it)
                Toast.makeText(
                    this@WritingRulesActivity,
                    "导出失败：${it.message ?: "请检查文件权限"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun buildSkillRefinePrompt(
        skill: WritingSkillStore.Skill,
        request: String
    ): String = """
        当前 Markdown Skill：${skill.name}
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
                skillPreview.postDelayed(
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
                skillPreview.height / 3
            ).coerceAtLeast(0)
        skillPreview.scrollTo(0, targetY)
    }

    private fun SharedPreferences.readString(key: String, fallback: String): String =
        all[key] as? String ?: fallback

    private fun formatTime(timestamp: Long): String =
        if (timestamp <= 0L) "未知" else SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.CHINA
        ).format(Date(timestamp))

    private fun showRecoveryPage(error: Exception) {
        val savedSkills = runCatching { WritingSkillStore.selected(this) }.getOrNull().orEmpty()
        val density = resources.displayMetrics.density
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.background))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(
                (20 * density).toInt(),
                (28 * density).toInt(),
                (20 * density).toInt(),
                (28 * density).toInt()
            )
        }
        content.addView(TextView(this).apply {
            text = "书写规范"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "页面配置读取异常，但应用已自动恢复。已启用的 Skill 仍可查看。"
            setTextColor(ContextCompat.getColor(context, R.color.clinical_warning))
            textSize = 13f
            setPadding(0, (12 * density).toInt(), 0, (16 * density).toInt())
        })
        content.addView(TextView(this).apply {
            text = if (savedSkills.isEmpty()) {
                "尚未读取到已启用的 Markdown Skill。"
            } else {
                savedSkills.joinToString("\n\n---\n\n") { skill ->
                    "## ${skill.name}\n\n${skill.content}"
                }
            }
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt()
            )
            setBackgroundResource(R.drawable.bg_card)
        })
        val errorText = error.stackTraceToString().take(6_000)
        content.addView(TextView(this).apply {
            text = "错误信息：\n$errorText"
            setTextColor(ContextCompat.getColor(context, R.color.text_hint))
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(0, (12 * density).toInt(), 0, (8 * density).toInt())
        })
        content.addView(Button(this).apply {
            text = "复制错误详情"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("书写规范错误", errorText))
                Toast.makeText(context, "错误详情已复制", Toast.LENGTH_SHORT).show()
            }
        })
        content.addView(Button(this).apply {
            text = "重新载入页面"
            setOnClickListener { recreate() }
        })
        content.addView(Button(this).apply {
            text = "返回主界面"
            setOnClickListener { finish() }
        })
        scroll.addView(content)
        setContentView(scroll)
    }

    private data class ImportResult(
        val imported: Int,
        val failed: Int,
        val firstImportedId: String?
    )

    private data class PendingExport(
        val content: ByteArray,
        val description: String
    )

    companion object {
        private const val TAG = "WritingRulesActivity"
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
