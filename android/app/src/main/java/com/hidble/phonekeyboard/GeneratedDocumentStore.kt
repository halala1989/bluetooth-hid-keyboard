package com.hidble.phonekeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GeneratedDocumentSummary(
    val type: String,
    val title: String,
    val preview: String,
    val updatedAt: Long
)

object GeneratedDocumentStore {
    private const val PREFS_NAME = "generated_medical_documents"

    fun list(context: Context, patientId: Long): List<GeneratedDocumentSummary> {
        val raw = encryptedValue(context, patientId) ?: return emptyList()
        val json = ClinicalCrypto.decrypt(raw)
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        GeneratedDocumentSummary(
                            type = item.optString("type"),
                            title = item.optString("title"),
                            preview = item.optString("preview"),
                            updatedAt = item.optLong("updatedAt")
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun sync(context: Context, patientId: Long, documentsJson: String) {
        val incoming = runCatching { JSONArray(documentsJson) }.getOrNull() ?: return
        val byType = listRaw(context, patientId).associateBy { it.optString("type") }.toMutableMap()
        for (index in 0 until incoming.length()) {
            val item = incoming.optJSONObject(index) ?: continue
            val type = item.optString("type").trim()
            if (type.isBlank()) continue
            val sections = item.optJSONArray("sections") ?: JSONArray()
            val preview = buildString {
                for (sectionIndex in 0 until sections.length()) {
                    val section = sections.optJSONObject(sectionIndex) ?: continue
                    val content = section.optString("content").replace(Regex("\\s+"), " ").trim()
                    if (content.isNotEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append(content.take(140))
                    }
                    if (length >= 180) break
                }
            }
            byType[type] = JSONObject()
                .put("type", type)
                .put("title", item.optString("title").ifBlank { type })
                .put("updatedAt", item.optLong("updatedAt", System.currentTimeMillis()))
                .put("preview", preview)
        }
        val array = JSONArray()
        byType.values.sortedByDescending { it.optLong("updatedAt") }.forEach(array::put)
        val encrypted = ClinicalCrypto.encrypt(array.toString())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(patientId), encrypted)
            .apply()
    }

    fun delete(context: Context, patientId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(patientId))
            .apply()
    }

    fun deleteOne(context: Context, patientId: Long, type: String) {
        val remaining = listRaw(context, patientId).filter { it.optString("type") != type }
        val array = JSONArray()
        remaining.forEach(array::put)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(patientId), ClinicalCrypto.encrypt(array.toString()))
            .apply()
    }

    private fun listRaw(context: Context, patientId: Long): List<JSONObject> {
        val raw = encryptedValue(context, patientId) ?: return emptyList()
        return runCatching {
            val array = JSONArray(ClinicalCrypto.decrypt(raw))
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encryptedValue(context: Context, patientId: Long): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(patientId), null)

    private fun key(patientId: Long) = "patient_$patientId"
}
