package top.mcxiafeng.badger.data

import android.util.Log
import com.google.gson.JsonParser

/**
 * 从 QAuxiliary 导出文件中解析出的单条好友记录（中间结构，不直接写 DB）。
 *
 * @property uin QQ 号
 * @property displayName 显示名（按用户决策：remark 优先 → nick → uin.toString）
 * @property rawRemark 原始备注（可能为空字符串或 null）
 * @property rawNick 原始昵称（可能为空字符串或 null）
 * @property status FriendRecord 状态码（3=历史好友 / 4=互为 / 5=我加对方 / 6=对方加我 / 7=黑名单 / 0,1,2=无效）
 */
data class QAuxvFriendEntry(
    val uin: Long,
    val displayName: String,
    val rawRemark: String?,
    val rawNick: String?,
    val status: Int,
) {
    /** 状态码中文标签，用于预览 Dialog 显示。 */
    val statusLabel: String
        get() = when (status) {
            0 -> "错误数据"
            1 -> "保留"
            2 -> "陌生人"
            3 -> "历史好友"
            4 -> "互为好友"
            5 -> "我加对方"
            6 -> "对方加我"
            7 -> "黑名单"
            else -> "未知($status)"
        }
}

/**
 * QAuxv 导出文件解析器。
 *
 * 自动嗅探格式：trim 后以 '[' 开头视为 JSON，否则按 CSV 解析。
 * 失败抛 IllegalArgumentException，调用方负责转 Toast / UI 提示。
 *
 * 关于 CSV 转义规则（QAuxiliary `DebugUtils.csvenc`）：
 *   仅当字段含 `"` `,` `\r` `\n` `\t` ` ` 时用双引号包裹；quote 内的 `"` 写作 `""`。
 *   本解析器严格按此规则实现 splitCsvLine。
 */
object QAuxvFriendImporter {
    private const val TAG = "QAuxvFriendImporter"

    /**
     * 解析 QAuxv 导出文件内容。
     *
     * @param content 文件全文（UTF-8）
     * @return 解析出的好友列表；已过滤 uin<=0、displayName 空的行；保留所有合法 status
     */
    fun parse(content: String): List<QAuxvFriendEntry> {
        val trimmed = content.trimStart()
        return if (trimmed.startsWith("[")) {
            parseJson(content)
        } else {
            parseCsv(content)
        }
    }

    private fun parseJson(content: String): List<QAuxvFriendEntry> {
        val root = try {
            JsonParser.parseString(content)
        } catch (e: Exception) {
            Log.e(TAG, "parseJson: not valid JSON", e)
            throw IllegalArgumentException("文件不是合法的 JSON 格式: ${e.message}")
        }
        if (!root.isJsonArray) {
            throw IllegalArgumentException("JSON 根节点必须是数组")
        }
        val arr = root.asJsonArray
        val result = ArrayList<QAuxvFriendEntry>(arr.size())
        arr.forEachIndexed { idx, el ->
            try {
                val obj = el.asJsonObject
                val uin = obj.get("uin")?.asLong ?: 0L
                if (uin <= 0L) {
                    Log.d(TAG, "parseJson[$idx]: skip invalid uin=$uin")
                    return@forEachIndexed
                }
                val remark = obj.get("remark")?.takeIf { !it.isJsonNull }?.asString
                val nick = obj.get("nick")?.takeIf { !it.isJsonNull }?.asString
                val status = obj.get("status")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val displayName = pickDisplayName(remark, nick, uin)
                result.add(
                    QAuxvFriendEntry(
                        uin = uin,
                        displayName = displayName,
                        rawRemark = remark,
                        rawNick = nick,
                        status = status,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "parseJson[$idx]: skip malformed element", e)
            }
        }
        Log.d(TAG, "parseJson: parsed ${result.size}/${arr.size()} entries")
        return result
    }

    private fun parseCsv(content: String): List<QAuxvFriendEntry> {
        // QAuxv 默认 LF；也容忍 CRLF / CR。规范化后按 LF 切行。
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val result = ArrayList<QAuxvFriendEntry>(lines.size)
        for ((idx, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val fields = splitCsvLine(line)
            // 已知顺序：uin, remark, nick, status；列数 < 3 视为无效行
            if (fields.size < 3) {
                Log.d(TAG, "parseCsv[$idx]: skip insufficient columns=${fields.size}")
                continue
            }
            val uin = fields[0].trim().toLongOrNull() ?: run {
                Log.d(TAG, "parseCsv[$idx]: skip non-numeric uin='${fields[0]}'")
                continue
            }
            if (uin <= 0L) {
                Log.d(TAG, "parseCsv[$idx]: skip invalid uin=$uin")
                continue
            }
            val remark = fields[1].takeIf { it.isNotBlank() }
            val nick = fields[2].takeIf { it.isNotBlank() }
            val status = if (fields.size >= 4) {
                fields[3].trim().toIntOrNull() ?: 0
            } else 0
            val displayName = pickDisplayName(remark, nick, uin)
            if (displayName.isBlank()) continue
            result.add(
                QAuxvFriendEntry(
                    uin = uin,
                    displayName = displayName,
                    rawRemark = remark,
                    rawNick = nick,
                    status = status,
                )
            )
        }
        Log.d(TAG, "parseCsv: parsed ${result.size} entries from ${lines.size} lines")
        return result
    }

    /**
     * 解析一行 CSV，支持 QAuxv `csvenc` 规则：
     *   - 普通字段不含 `"` `,` `\r` `\n` `\t` ` ` 时直接是裸文本
     *   - 包含特殊字符时整个字段用 `"` 包裹，内部 `"` 写作 `""`
     *   - quote 模式下 `,` 不切列
     */
    internal fun splitCsvLine(line: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuote && c == '"' -> {
                    // 可能是转义 "" 或 quote 结束
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 2
                        continue
                    } else {
                        inQuote = false
                        i++
                    }
                }
                !inQuote && c == '"' -> {
                    // 字段起始 quote
                    inQuote = true
                    i++
                }
                !inQuote && c == ',' -> {
                    result.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * 用户决策：remark 优先 → nick → uin.toString。
     */
    private fun pickDisplayName(remark: String?, nick: String?, uin: Long): String {
        return remark?.takeIf { it.isNotBlank() }
            ?: nick?.takeIf { it.isNotBlank() }
            ?: uin.toString()
    }
}