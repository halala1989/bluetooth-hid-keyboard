package com.hidble.phonekeyboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object MedicalSkillParser {
    private const val MAX_CONTENT_LENGTH = 200_000
    private const val MAX_ARCHIVE_UNCOMPRESSED_BYTES = 8_000_000

    data class ImportedSkill(
        val name: String,
        val content: String
    )

    fun parse(context: Context, uri: Uri): ImportedSkill {
        return parseAll(context, uri).firstOrNull()
            ?: error("所选文件中没有可导入的 Markdown Skill")
    }

    fun parseAll(context: Context, uri: Uri): List<ImportedSkill> {
        val name = displayName(context, uri)
        val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
        val isZip = name.endsWith(".zip", ignoreCase = true) ||
            mimeType == "application/zip" ||
            mimeType == "application/x-zip-compressed"
        return if (isZip) {
            parseZip(context, uri)
        } else {
            if (!isSupportedMarkdown(name)) {
                error("请选择 Markdown 文件（.md、.markdown、.txt）或 Skill ZIP 包")
            }
            val content = context.contentResolver.openInputStream(uri)?.use {
                readLimitedText(it, MAX_ARCHIVE_UNCOMPRESSED_BYTES)
            }.orEmpty()
            listOf(normalizeSkill(name, content))
        }
    }

    fun toJson(skill: ImportedSkill): String =
        JSONObject()
            .put("name", skill.name)
            .put("content", skill.content)
            .toString()

    private fun parseZip(context: Context, uri: Uri): List<ImportedSkill> {
        val imported = mutableListOf<ImportedSkill>()
        var totalBytes = 0
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && isSupportedMarkdown(entry.name)) {
                        val entryName = entry.name.substringAfterLast('/')
                        val text = readLimitedText(
                            zip,
                            MAX_ARCHIVE_UNCOMPRESSED_BYTES - totalBytes
                        )
                        totalBytes += text.toByteArray(Charsets.UTF_8).size
                        if (totalBytes > MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                            error("ZIP 中的 Skill 总大小超过 8 MB")
                        }
                        imported += normalizeSkill(entryName, text)
                        if (imported.size >= 100) {
                            error("一次最多导入 100 个 Skill")
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        if (imported.isEmpty()) error("ZIP 中没有 .md、.markdown 或 .txt Skill 文件")
        return imported
    }

    private fun normalizeSkill(name: String, rawContent: String): ImportedSkill {
        val content = rawContent.removePrefix("\uFEFF").trim()
        if (content.isBlank()) error("$name 内容为空")
        if (content.length > MAX_CONTENT_LENGTH) {
            error("$name 超过 20 万字符，无法导入")
        }
        return ImportedSkill(name, content)
    }

    private fun isSupportedMarkdown(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".md") ||
            lower.endsWith(".markdown") ||
            lower.endsWith(".txt")
    }

    private fun readLimitedText(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > maxBytes) error("文件过大，无法导入")
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return uri.lastPathSegment ?: "Skill.md"
    }
}
