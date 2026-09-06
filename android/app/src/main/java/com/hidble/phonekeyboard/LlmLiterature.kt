package com.hidble.phonekeyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 火山 AI Hub(Agent Plan)「专业数据集」MCP 检索客户端。
 *
 * 背景：Agent Plan 的科学文献检索不是一个“对话开关”，而是官方以 MCP Server 提供的
 * “专业数据集”Harness（工具名 dataPro_search，入参 query）。App 在把问题发给大模型之前，
 * 先调这个 MCP 把检索到的文献取回来，作为上下文塞给模型，让模型据此回答并标注来源。
 *
 * 端点（2026-09-06 实测）：
 *   POST https://datapro.hqd.cn-beijing.volces.com/mcp
 *   鉴权头：X-Agent-Plan-Key: <Agent Plan 专属 Key>（网关也接受 X-Hqd-Api-Key）
 *   协议：MCP Streamable HTTP，JSON-RPC 2.0（initialize -> notifications/initialized ->
 *         tools/list -> tools/call）。本服务实测无 Mcp-Session-Id，但客户端做了兼容。
 *   返回：structuredContent / content[].text 为 JSON：
 *         { "code":0, "msg":"success", "query":..., "total":N, "items":[...] }
 *         code==0 成功；code==4011 = Key 无效 / 额度不足 / 未开启专业数据集权限。
 *   覆盖（服务端能力说明原文）：学术文献库覆盖 CNKI、万方、维普、arxiv、PubMed、
 *         MDPI、Biorxiv 等；另含企业工商/风险、股票金融、汽车配置/销量等。
 */
object LlmLiterature {

    private const val MCP_URL = "https://datapro.hqd.cn-beijing.volces.com/mcp"
    private const val PROTOCOL_VERSION = "2025-06-18"
    private const val TOOL_NAME = "dataPro_search"

    /** 单条文献/数据结果（字段名各家不一，做了宽容解析，raw 保留原始 JSON 便于排查） */
    data class Item(
        val title: String,
        val url: String,
        val date: String,
        val authors: String,
        val journal: String,
        val snippet: String,
        val raw: String
    )

    /**
     * 检索专业数据集。apiKey 为 Agent Plan 专属 Key；query 为自然语言查询。
     * 成功返回条目列表（可能为空）；失败抛异常（含服务端中文错误）。
     * 网络在 IO 线程执行。
     */
    suspend fun search(apiKey: String, query: String): List<Item> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw RuntimeException("缺少科学文献检索 Key（Agent Plan 专属 Key）")
        if (query.isBlank()) throw RuntimeException("检索关键词为空")

        // 1) initialize（协商协议版本，可能返回 Mcp-Session-Id）
        val initParams = JSONObject()
            .put("protocolVersion", PROTOCOL_VERSION)
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "phonekeyboard").put("version", "2.0-beta"))
        val session = rpc("initialize", initParams, null, apiKey).first

        // 2) notifications/initialized（规范要求，通知无响应体，失败可忽略）
        try {
            rpc("notifications/initialized", JSONObject(), session, apiKey)
        } catch (e: Exception) {
            // 部分服务不要求该通知，忽略
        }

        // 3) tools/list：确认 dataPro_search 存在（顺便拿到最新工具说明）
        val tools = rpc("tools/list", null, session, apiKey).second
            .optJSONObject("result")?.optJSONArray("tools")
        if (tools == null || !toolExists(tools)) {
            throw RuntimeException("专业数据集 MCP 未找到 dataPro_search 工具（服务端工具列表可能已变化）")
        }

        // 4) tools/call：真正检索
        val callParams = JSONObject()
            .put("name", TOOL_NAME)
            .put("arguments", JSONObject().put("query", query))
        val result = rpc("tools/call", callParams, session, apiKey).second
            .optJSONObject("result")
            ?: throw RuntimeException("专业数据集无返回结果")

        parseResult(result)
    }

    private fun toolExists(tools: JSONArray): Boolean {
        for (i in 0 until tools.length()) {
            if (tools.optJSONObject(i)?.optString("name") == TOOL_NAME) return true
        }
        return false
    }

    // ===== 返回体解析 =====

    private fun parseResult(result: JSONObject): List<Item> {
        // 优先 structuredContent（MCP 结构化输出），回退 content[].text 里的 JSON
        var code = -999
        var msg = ""
        var itemsArr: JSONArray? = null

        val sc = result.optJSONObject("structuredContent")
        if (sc != null) {
            code = sc.optInt("code", code)
            msg = sc.optString("msg", msg)
            itemsArr = sc.optJSONArray("items")
        }
        if (itemsArr == null) {
            val text = result.optJSONArray("content")?.optJSONObject(0)?.optString("text")
            if (!text.isNullOrBlank()) {
                try {
                    val j = JSONObject(text)
                    code = j.optInt("code", code)
                    msg = j.optString("msg", msg)
                    itemsArr = j.optJSONArray("items")
                } catch (e: Exception) {
                    // text 不是 JSON（罕见）：当作单条原始结果返回
                    return listOf(itemFromRaw(text))
                }
            }
        }

        // 鉴权/权限/额度类错误：带上服务端中文提示
        if (code == 4011) {
            throw RuntimeException("科学文献检索鉴权失败：${msg.ifBlank { "Key 无效、额度不足或未开启专业数据集 Harness" }}")
        }
        if (code != 0) {
            throw RuntimeException("专业数据集调用失败（code=$code）：${msg.ifBlank { "未知错误" }}")
        }

        // code==0 但 items 缺失/为空：在 structuredContent 里找第一个“对象数组”兜底
        if (itemsArr == null || itemsArr.length() == 0) {
            if (sc != null) itemsArr = findFirstObjectArray(sc, skipKey = "content")
        }

        val list = mutableListOf<Item>()
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val o = itemsArr.optJSONObject(i) ?: continue
                list.add(itemFromJson(o))
            }
        }
        return list
    }

    /** 宽容地从条目 JSON 里抽取常用字段（不同数据类型字段名可能不同） */
    private fun itemFromJson(o: JSONObject): Item {
        val raw = o.toString()
        return Item(
            title = findValue(o, listOf("title", "标题", "篇名", "论文标题", "论文题目", "name", "名称")),
            url = findValue(o, listOf("url", "link", "链接", "原文链接", "论文链接", "source_url")),
            date = findValue(o, listOf("date", "time", "year", "pubdate", "publish", "发表时间", "日期", "年份")),
            authors = findValue(o, listOf("author", "authors", "作者", "作者列表")),
            journal = findValue(o, listOf("journal", "venue", "来源", "期刊", "出版物")),
            snippet = findValue(o, listOf("abstract", "summary", "snippet", "desc", "description", "摘要", "简介", "内容")),
            raw = raw
        )
    }

    private fun itemFromRaw(text: String): Item =
        Item(title = text.take(120), url = "", date = "", authors = "", journal = "", snippet = text, raw = text)

    /** 按候选子串（大小写不敏感）在对象里找第一个匹配字段的值，数组会拼成字符串 */
    private fun findValue(o: JSONObject, candidates: List<String>): String {
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            for (c in candidates) {
                if (k.contains(c, ignoreCase = true)) {
                    return stringify(o.opt(k))
                }
            }
        }
        return ""
    }

    private fun stringify(v: Any?): String {
        return when (v) {
            is String -> v.trim()
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until Math.min(v.length(), 8)) {
                    val e = v.opt(i)
                    if (e == null || e == JSONObject.NULL) continue
                    if (sb.isNotEmpty()) sb.append("、")
                    sb.append(if (e is String) e.trim() else e.toString())
                }
                sb.toString().trim()
            }
            is Number, is Boolean -> v.toString()
            else -> ""
        }
    }

    /** 在 JSON 树里找第一个“数组里都是对象”的数组（跳过 content，避免误把 MCP 内容块当条目） */
    private fun findFirstObjectArray(node: Any?, skipKey: String): JSONArray? {
        if (node is JSONObject) {
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k == skipKey) continue
                val v = node.opt(k)
                if (v is JSONArray) {
                    if (v.length() > 0 && v.opt(0) is JSONObject) return v
                }
            }
            val keys2 = node.keys()
            while (keys2.hasNext()) {
                val k = keys2.next()
                if (k == skipKey) continue
                findFirstObjectArray(node.opt(k), skipKey)?.let { return it }
            }
        } else if (node is JSONArray) {
            for (i in 0 until node.length()) {
                findFirstObjectArray(node.opt(i), skipKey)?.let { return it }
            }
        }
        return null
    }

    // ===== JSON-RPC 传输 =====

    /** @return Pair(会话ID, JSON-RPC 响应对象)。响应里取 .result/.error */
    private fun rpc(method: String, params: JSONObject?, session: String?, apiKey: String): Pair<String?, JSONObject> {
        val reqId = nextId()
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", reqId)
            .put("method", method)
        if (params != null) body.put("params", params)

        val conn = URL(MCP_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            conn.setRequestProperty("X-Agent-Plan-Key", apiKey) // Agent Plan 专属 Key
            if (!session.isNullOrBlank()) {
                conn.setRequestProperty("Mcp-Session-Id", session)
            }
            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val code = conn.responseCode
            val newSession = conn.getHeaderField("Mcp-Session-Id")
            val raw = if (code in 200..299) {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                throw RuntimeException("专业数据集服务不可用（HTTP $code）：${raw.take(300)}")
            }
            val contentType = conn.contentType ?: ""
            val json = if (contentType.contains("text/event-stream", ignoreCase = true)) {
                parseSse(raw, reqId)
            } else {
                JSONObject(raw)
            }
            if (json.opt("error") is JSONObject) {
                val err = json.getJSONObject("error")
                throw RuntimeException("专业数据集错误：${err.optString("message", err.toString()).take(300)}")
            }
            return newSession to json
        } finally {
            conn.disconnect()
        }
    }

    private var idCounter = 0
    private fun nextId(): Int = ++idCounter

    /** 简易 SSE 解析：把每个 data: 行当独立 JSON-RPC 消息，优先取与请求 id 匹配的响应 */
    private fun parseSse(raw: String, reqId: Int): JSONObject {
        val candidates = mutableListOf<JSONObject>()
        raw.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.startsWith("data:")) {
                val data = t.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") return@forEach
                try {
                    candidates.add(JSONObject(data))
                } catch (e: Exception) {
                    // 忽略非 JSON 事件（注释/心跳等）
                }
            }
        }
        val hit = candidates.lastOrNull {
            it.optInt("id", -1) == reqId && (it.has("result") || it.has("error"))
        }
        if (hit != null) return hit
        if (candidates.isNotEmpty()) return candidates.last()
        throw RuntimeException("专业数据集服务返回了无法解析的响应")
    }
}