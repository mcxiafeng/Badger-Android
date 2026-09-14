package top.mcxiafeng.badger.pages.settings.history

import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object OperationHistoryOpFormatter {

    fun formatTimestamp(epoch: Long): String = formatEpoch(epoch)

    
    fun formatStatusLabel(opStatus: String): String = when (opStatus) {
        "PENDING" -> "等待中"
        "IN_FLIGHT" -> "发送中"
        "DONE" -> "成功"
        "CONFLICT" -> "冲突"
        "FAILED" -> "失败"
        "FAILED_PERMANENT" -> "永久失败"
        "WITHDRAWN" -> "已撤销"
        else -> opStatus
    }

    
    fun formatContactName(contactName: String?): String = contactName ?: "(已删除)"

    
    fun formatListSubtitle(item: OperationHistoryWithContact): String {
        val opLabel = OperationTypes.labelOf(item.history.opType)
        val time = formatTimestamp(item.history.createdAt)
        return "$time  ·  $opLabel"
    }

    
    fun formatDetailSummary(item: OperationHistoryWithContact): String {
        val contact = formatContactName(item.contactName)
        val opLabel = OperationTypes.labelOf(item.history.opType)
        return "$contact  ·  $opLabel"
    }

    
    fun formatFilterLabel(filter: HistoryFilter): String = when (filter) {
        HistoryFilter.All -> "全部"
        HistoryFilter.Pending -> "待处理"
    }
}

internal fun formatEpoch(epoch: Long): String {
    val local = Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault())
    fun p2(v: Int) = v.toString().padStart(2, '0')
    return "${local.year}-${p2(local.monthNumber)}-${p2(local.dayOfMonth)} ${p2(local.hour)}:${p2(local.minute)}"
}
