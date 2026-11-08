package com.hidble.phonekeyboard

import android.content.Context

object WritingSkillStore {
    private const val PREFS_NAME = "medical_writing_skill"
    private const val KEY_NAME = "skill_name"
    private const val KEY_CONTENT = "skill_content"
    private const val KEY_UPDATED_AT = "skill_updated_at"

    data class Skill(
        val name: String,
        val content: String,
        val updatedAt: Long
    )

    fun load(context: Context): Skill? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val content = prefs.getString(KEY_CONTENT, "").orEmpty()
        if (content.isBlank()) return null
        return Skill(
            name = prefs.getString(KEY_NAME, "Markdown Skill").orEmpty(),
            content = content,
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0)
        )
    }

    fun save(context: Context, skill: MedicalSkillParser.ImportedSkill) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, skill.name)
            .putString(KEY_CONTENT, skill.content)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun label(context: Context): String =
        load(context)?.let { "${it.name}（${it.content.length} 字）" } ?: "未配置"
}
