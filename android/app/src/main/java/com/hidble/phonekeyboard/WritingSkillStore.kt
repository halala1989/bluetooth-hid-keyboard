package com.hidble.phonekeyboard

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

object WritingSkillStore {
    private const val PREFS_NAME = "medical_writing_skill"
    private const val LEGACY_KEY_NAME = "skill_name"
    private const val LEGACY_KEY_CONTENT = "skill_content"
    private const val LEGACY_KEY_UPDATED_AT = "skill_updated_at"
    private const val SKILL_DIRECTORY = "writing_skills"
    private const val MAX_PROMPT_CHARS = 48_000

    data class Skill(
        val id: String,
        val name: String,
        val content: String,
        val updatedAt: Long,
        val enabled: Boolean,
        val createdAt: Long
    )

    @Synchronized
    fun all(context: Context): List<Skill> {
        migrateLegacySkill(context)
        return readAll(context)
    }

    /** 兼容旧调用：返回第一个启用的 Skill；没有启用项时返回库中的第一项。 */
    fun load(context: Context): Skill? = all(context).let { skills ->
        skills.firstOrNull { it.enabled } ?: skills.firstOrNull()
    }

    fun selected(context: Context): List<Skill> = all(context).filter { it.enabled }

    @Synchronized
    fun save(context: Context, imported: MedicalSkillParser.ImportedSkill): Skill {
        val existing = readAll(context).firstOrNull {
            it.name == imported.name && it.content == imported.content
        }
        val now = System.currentTimeMillis()
        val skill = existing?.copy(
            updatedAt = now,
            enabled = true
        ) ?: Skill(
            id = UUID.randomUUID().toString(),
            name = imported.name,
            content = imported.content,
            updatedAt = now,
            enabled = true,
            createdAt = now
        )
        writeSkill(context, skill)
        return skill
    }

    @Synchronized
    fun updateContent(context: Context, id: String, content: String): Skill? {
        val current = readAll(context).firstOrNull { it.id == id } ?: return null
        val updated = current.copy(
            content = content,
            updatedAt = System.currentTimeMillis()
        )
        writeSkill(context, updated)
        return updated
    }

    @Synchronized
    fun setEnabled(context: Context, id: String, enabled: Boolean): Skill? {
        val current = readAll(context).firstOrNull { it.id == id } ?: return null
        val updated = current.copy(
            enabled = enabled,
            updatedAt = System.currentTimeMillis()
        )
        writeSkill(context, updated)
        return updated
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        skillFiles(context).firstOrNull { readSkill(it)?.id == id }?.delete()
    }

    @Synchronized
    fun clear(context: Context) {
        skillFiles(context).forEach { it.delete() }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun label(context: Context): String {
        val skills = selected(context)
        if (skills.isEmpty()) return "未配置"
        val names = skills.take(3).joinToString("、") { it.name }
        val suffix = if (skills.size > 3) " 等" else ""
        val chars = skills.sumOf { it.content.length }
        return "已启用 ${skills.size} 个 Skill：$names$suffix（$chars 字）"
    }

    fun promptMarkdown(context: Context, maxChars: Int = MAX_PROMPT_CHARS): String {
        val skills = selected(context)
        if (skills.isEmpty()) return ""
        val sections = skills.mapIndexed { index, skill ->
            val safeName = skill.name.trim().ifBlank { "未命名 Skill" }
            "### ${index + 1}. $safeName\n\n${skill.content.trim()}"
        }
        val joined = sections.joinToString("\n\n---\n\n")
        if (joined.length <= maxChars) return joined
        val notice = "\n\n（Skill 内容较长，已按总长度限制截断。）"
        return joined.take((maxChars - notice.length).coerceAtLeast(0)) + notice
    }

    fun safeExportName(name: String): String {
        val base = name
            .substringBeforeLast('.')
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
            .trim()
            .trimEnd('.')
            .ifBlank { "书写规范" }
        return base.take(80)
    }

    private fun migrateLegacySkill(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(LEGACY_KEY_CONTENT) &&
            !prefs.contains(LEGACY_KEY_NAME) &&
            !prefs.contains(LEGACY_KEY_UPDATED_AT)
        ) {
            return
        }

        val content = prefs.all[LEGACY_KEY_CONTENT] as? String ?: ""
        if (content.isNotBlank() && readAll(context).isEmpty()) {
            val updatedAt = prefs.all[LEGACY_KEY_UPDATED_AT] as? Long
                ?: System.currentTimeMillis()
            writeSkill(
                context,
                Skill(
                    id = UUID.randomUUID().toString(),
                    name = prefs.all[LEGACY_KEY_NAME] as? String ?: "Markdown Skill",
                    content = content,
                    updatedAt = updatedAt,
                    enabled = true,
                    createdAt = updatedAt
                )
            )
        }
        prefs.edit()
            .remove(LEGACY_KEY_NAME)
            .remove(LEGACY_KEY_CONTENT)
            .remove(LEGACY_KEY_UPDATED_AT)
            .apply()
    }

    private fun readAll(context: Context): List<Skill> =
        skillFiles(context)
            .mapNotNull(::readSkill)
            .sortedWith(compareBy<Skill> { it.createdAt }.thenBy { it.name })

    private fun skillFiles(context: Context): List<File> {
        val directory = File(context.filesDir, SKILL_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) return emptyList()
        return directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .orEmpty()
    }

    private fun readSkill(file: File): Skill? = runCatching {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val content = json.optString("content", "")
        if (content.isBlank()) return@runCatching null
        Skill(
            id = json.getString("id"),
            name = json.optString("name", "Markdown Skill"),
            content = content,
            updatedAt = json.optLong("updatedAt", 0L),
            enabled = json.optBoolean("enabled", true),
            createdAt = json.optLong("createdAt", json.optLong("updatedAt", 0L))
        )
    }.getOrNull()

    private fun writeSkill(context: Context, skill: Skill) {
        val directory = File(context.filesDir, SKILL_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            error("无法创建书写规范存储目录")
        }
        val json = JSONObject()
            .put("id", skill.id)
            .put("name", skill.name)
            .put("content", skill.content)
            .put("updatedAt", skill.updatedAt)
            .put("enabled", skill.enabled)
            .put("createdAt", skill.createdAt)
        val target = File(directory, "${skill.id}.json")
        val temporary = File(directory, "${skill.id}.tmp")
        temporary.writeText(json.toString(), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(json.toString(), Charsets.UTF_8)
            temporary.delete()
        }
    }
}
