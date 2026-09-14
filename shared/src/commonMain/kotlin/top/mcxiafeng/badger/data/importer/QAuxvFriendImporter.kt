package top.mcxiafeng.badger.data.importer

import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import top.mcxiafeng.badger.network.BadgerJson
import top.mcxiafeng.badger.network.contentOrNull
import top.mcxiafeng.badger.network.intOr
import top.mcxiafeng.badger.network.longOr

data class QAuxvFriendEntry(
    val uin: Long,
    val displayName: String,
    val rawRemark: String?,
    val rawNick: String?,
    val status: Int,
) {
    
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

object QAuxvFriendImporter {
    private const val TAG = "QAuxvFriendImporter"

    

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
            BadgerJson.parseToJsonElement(content)
        } catch (e: Exception) {
            BadgerLog.e(TAG, "parseJson: not valid JSON", e)
            throw IllegalArgumentException("文件不是合法的 JSON 格式: ${e.message}")
        }
        val arr = root as? JsonArray
        if (arr == null) {
            throw IllegalArgumentException("JSON 根节点必须是数组")
        }
        val result = ArrayList<QAuxvFriendEntry>(arr.size)
        arr.forEachIndexed { idx, el ->
            try {
                val obj = el as? JsonObject ?: throw IllegalStateException("element not object")
                val uin = longOr(obj["uin"], 0L)
                if (uin <= 0L) {
                    BadgerLog.d(TAG, "parseJson[$idx]: skip invalid uin=$uin")
                    return@forEachIndexed
                }
                val remark = obj["remark"].contentOrNull()
                val nick = obj["nick"].contentOrNull()
                val status = intOr(obj["status"], 0)
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
                BadgerLog.w(TAG, "parseJson[$idx]: skip malformed element", e)
            }
        }
        BadgerLog.d(TAG, "parseJson: parsed ${result.size}/${arr.size} entries")
        return result
    }

    private fun parseCsv(content: String): List<QAuxvFriendEntry> {
        
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val result = ArrayList<QAuxvFriendEntry>(lines.size)
        for ((idx, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val fields = splitCsvLine(line)
            
            if (fields.size < 3) {
                BadgerLog.d(TAG, "parseCsv[$idx]: skip insufficient columns=${fields.size}")
                continue
            }
            val uin = fields[0].trim().toLongOrNull() ?: run {
                BadgerLog.d(TAG, "parseCsv[$idx]: skip non-numeric uin='${fields[0]}'")
                continue
            }
            if (uin <= 0L) {
                BadgerLog.d(TAG, "parseCsv[$idx]: skip invalid uin=$uin")
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
        BadgerLog.d(TAG, "parseCsv: parsed ${result.size} entries from ${lines.size} lines")
        return result
    }

    

    fun splitCsvLine(line: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuote && c == '"' -> {
                    
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

    

    private fun pickDisplayName(remark: String?, nick: String?, uin: Long): String {
        return remark?.takeIf { it.isNotBlank() }
            ?: nick?.takeIf { it.isNotBlank() }
            ?: uin.toString()
    }
}
