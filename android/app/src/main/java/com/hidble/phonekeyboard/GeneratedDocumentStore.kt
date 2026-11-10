package com.hidble.phonekeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GeneratedDocumentSummary(
    val id: String,
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
                    val item = normalize(array.getJSONObject(index))
                    add(
                        GeneratedDocumentSummary(
                            id = item.getString("id"),
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

    fun save(context: Context, patientId: Long, documentJson: String): Boolean {
        val original = runCatching { JSONObject(documentJson) }.getOrNull() ?: return false
        val item = normalize(original).put(
            "updatedAt",
            original.optLong("updatedAt", System.currentTimeMillis())
        )
        item.put("preview", previewOf(item))
        val documents = listRaw(context, patientId).toMutableList()
        val index = documents.indexOfFirst { normalize(it).optString("id") == item.optString("id") }
        if (index >= 0) documents[index] = item else documents.add(item)
        return writeRaw(context, patientId, documents)
    }

    fun get(context: Context, patientId: Long, documentId: String): String? =
        listRaw(context, patientId)
            .firstOrNull { normalize(it).optString("id") == documentId }
            ?.let { normalize(it).toString() }

    fun sync(context: Context, patientId: Long, documentsJson: String) {
        val incoming = runCatching { JSONArray(documentsJson) }.getOrNull() ?: return
        val byId = listRaw(context, patientId)
            .associateBy { normalize(it).optString("id") }
            .toMutableMap()
        for (index in 0 until incoming.length()) {
            val raw = incoming.optJSONObject(index) ?: continue
            val item = normalize(raw)
                .put("updatedAt", raw.optLong("updatedAt", System.currentTimeMillis()))
                .put("preview", previewOf(raw))
            byId[item.optString("id")] = item
        }
        writeRaw(
            context,
            patientId,
            byId.values.sortedByDescending { it.optLong("updatedAt") }
        )
    }

    fun delete(context: Context, patientId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(patientId))
            .apply()
    }

    fun deleteOne(context: Context, patientId: Long, documentId: String) {
        val remaining = listRaw(context, patientId)
            .filter { normalize(it).optString("id") != documentId }
        writeRaw(context, patientId, remaining)
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

    private fun normalize(item: JSONObject): JSONObject {
        val copy = JSONObject(item.toString())
        val type = copy.optString("type").trim()
        val updatedAt = copy.optLong("updatedAt", System.currentTimeMillis())
        val id = copy.optString("id").trim().ifBlank { "legacy-$type-$updatedAt" }
        copy.put("id", id)
        copy.put("type", type)
        copy.put("title", copy.optString("title").ifBlank { type })
        copy.put("updatedAt", updatedAt)
        return copy
    }

    private fun previewOf(item: JSONObject): String {
        val sections = item.optJSONArray("sections") ?: JSONArray()
        return buildString {
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
    }

    private fun writeRaw(
        context: Context,
        patientId: Long,
        documents: Collection<JSONObject>
    ): Boolean {
        val array = JSONArray()
        documents.sortedByDescending { it.optLong("updatedAt") }.forEach {
            array.put(normalize(it))
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(patientId), ClinicalCrypto.encrypt(array.toString()))
            .commit()
    }
}
