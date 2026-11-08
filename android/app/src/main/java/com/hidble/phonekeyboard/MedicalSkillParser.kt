package com.hidble.phonekeyboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject

object MedicalSkillParser {
    data class ImportedSkill(
        val name: String,
        val content: String
    )

    fun parse(context: Context, uri: Uri): ImportedSkill {
        val name = displayName(context, uri)
        val lower = name.lowercase()
        if (!lower.endsWith(".md") && !lower.endsWith(".markdown") && !lower.endsWith(".txt")) {
            error("请选择 Markdown 文件（.md 或 .markdown）")
        }
        val content = context.contentResolver.openInputStream(uri)?.use {
            String(it.readBytes(), Charsets.UTF_8).removePrefix("\uFEFF")
        }.orEmpty().trim()
        if (content.isBlank()) error("Markdown Skill 文件为空")
        if (content.length > 200_000) error("Markdown Skill 过大，请控制在 20 万字符以内")
        return ImportedSkill(name, content)
    }

    fun toJson(skill: ImportedSkill): String =
        JSONObject()
            .put("name", skill.name)
            .put("content", skill.content)
            .toString()

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return uri.lastPathSegment ?: "Skill.md"
    }
}
