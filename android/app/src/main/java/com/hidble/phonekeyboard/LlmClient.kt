package com.hidble.phonekeyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 大模型提供方预设：像 CC Switch 一样，只选提供方 + 填 API Token，
 * 接口地址 / 模型名 / 请求格式都已预置好。
 */
data class LlmProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,          // 不含 /chat/completions
    val defaultModel: String,
    val needsKey: Boolean = true  // 自定义提供方可不填 token
) {
    fun endpoint(): String = baseUrl.trimEnd('/') + "/chat/completions"
}

object LlmProviders {
    val list = listOf(
        LlmProvider("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash"),
        LlmProvider("mimo", "小米 MiMo", "https://api.xiaomimimo.com/v1", "mimo-v2.5"),
        LlmProvider("volcano", "火山引擎（豆包）", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-1-8-251228")
    )

    fun byId(id: String): LlmProvider = list.firstOrNull { it.id == id } ?: list[0]

    fun indexOf(id: String): Int = list.indexOfFirst { it.id == id }.coerceAtLeast(0)
}

/**
 * 一条消息中的一个内容部件。
 * - text ：纯文本正文；
 * - image：本地图片，base64 为原始字节（不含 data: 头），按 OpenAI image_url 格式发送；
 * - audio：本地音频，base64 为原始字节，按 input_audio 格式发送（小米 MiMo 原生支持）。
 */
data class LlmPart(
    val type: String,        // text | image | audio
    val text: String = "",   // type=text 时的正文
    val mime: String = "",   // image/audio 的 MIME，如 image/png、audio/mpeg
    val base64: String = ""  // image/audio 的 Base64 原文
) {
    fun dataUri(): String = "data:$mime;base64,$base64"
}

/** 发给模型的一条消息：角色 + 内容部件列表（纯文本可只用 LlmMessage.text） */
data class LlmMessage(
    val role: String,
    val parts: List<LlmPart>
) {
    companion object {
        fun text(role: String, content: String): LlmMessage =
            LlmMessage(role, listOf(LlmPart(type = "text", text = content)))

        fun textParts(role: String, text: String, files: List<LlmPart>): LlmMessage {
            val parts = mutableListOf<LlmPart>()
            if (text.isNotBlank()) parts.add(LlmPart(type = "text", text = text))
            parts.addAll(files)
            return LlmMessage(role, parts)
        }
    }
}

/** 大模型配置的 SharedPreferences 键（主界面与设置页共用） */
object LlmPrefs {
    const val PREFS_NAME = "hidble_prefs"
    const val KEY_PROVIDER = "llm_provider"
    const val KEY_API_KEY = "llm_api_key"
    const val KEY_MODEL = "llm_model"
    const val KEY_OUTPUT = "llm_output"
    const val KEY_PROMPTS = "llm_prompts"
    const val KEY_SELECTED_PROMPT = "llm_selected_prompt"
    const val KEY_PROMPT_PRESET_VERSION = "llm_prompt_preset_version"
    const val KEY_HISTORY = "llm_history"
    const val KEY_CONVERSATIONS = "llm_conversations"
}

/** OpenAI 兼容 Chat Completions 客户端（HttpURLConnection，无额外依赖，非流式） */
object LlmClient {

    /**
     * 发送一轮对话，返回助手回复文本。
     * messages 为 (role, content) 列表，如 ("user" to "你好")。
     */
    suspend fun chat(
        provider: LlmProvider,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", model.ifBlank { provider.defaultModel })
            .put("temperature", 0.7)
            .put("messages", JSONArray().apply {
                messages.forEach { (role, content) ->
                    put(JSONObject().put("role", role).put("content", content))
                }
            })

        val conn = URL(provider.endpoint()).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val msg = try {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                        ?: raw.ifBlank { "HTTP $code" }
                } catch (e: Exception) {
                    raw.ifBlank { "HTTP $code" }
                }
                throw RuntimeException("请求失败（$code）：$msg")
            }

            val content = JSONObject(raw)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.opt("content")
                ?.takeIf { it is String && it.isNotEmpty() }
                ?.toString()
            if (content.isNullOrBlank()) {
                throw RuntimeException("模型返回为空，请检查模型名是否正确")
            }
            content
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 流式对话（SSE）：边读边通过 onDelta 回调内容增量（在 IO 线程调用，需自行切回主线程），
     * 返回完整回复文本。出错时抛异常。
     */
    suspend fun chatStream(
        provider: LlmProvider,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", model.ifBlank { provider.defaultModel })
            .put("temperature", 0.7)
            .put("stream", true)
            .put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    val obj = JSONObject().put("role", msg.role)
                    obj.putContentParts(msg.parts)
                    put(obj)
                }
            })

        val conn = URL(provider.endpoint()).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "text/event-stream")
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val raw = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                val msg = try {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                        ?: raw.ifBlank { "HTTP $code" }
                } catch (e: Exception) {
                    raw.ifBlank { "HTTP $code" }
                }
                throw RuntimeException("请求失败（$code）：$msg")
            }

            val sb = StringBuilder()
            conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("data:")) {
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            // 只接受真正的字符串内容：角色切换/思考阶段/结束标记等增量块的
                            // content 是 JSON null，optString 会返回字面量 "null" 导致正文串入 null。
                            val delta = JSONObject(data)
                                .optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                                ?.opt("content")
                                ?.takeIf { it is String && it.isNotEmpty() }
                                ?.toString()
                            if (delta != null) {
                                sb.append(delta)
                                onDelta(delta)
                            }
                        } catch (e: Exception) {
                            // 忽略非 JSON 的注释行
                        }
                    }
                    line = reader.readLine()
                }
            }

            val full = sb.toString()
            if (full.isBlank()) {
                throw RuntimeException("模型返回为空，请检查模型名是否正确或是否支持流式输出")
            }
            full
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 把消息部件写入 JSON content 字段：
     * - 只有一个 text 部件 → 用普通字符串（所有提供方最兼容）；
     * - 含图片/音频 → 用 OpenAI 多模态 content 数组。
     */
    private fun JSONObject.putContentParts(parts: List<LlmPart>) {
        if (parts.size == 1 && parts[0].type == "text") {
            put("content", parts[0].text)
            return
        }
        val arr = JSONArray()
        parts.forEach { p ->
            when (p.type) {
                "image" -> arr.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", p.dataUri()))
                )
                "audio" -> arr.put(
                    JSONObject()
                        .put("type", "input_audio")
                        .put("input_audio", JSONObject().put("data", p.dataUri()))
                )
                else -> arr.put(JSONObject().put("type", "text").put("text", p.text))
            }
        }
        put("content", arr)
    }
}
