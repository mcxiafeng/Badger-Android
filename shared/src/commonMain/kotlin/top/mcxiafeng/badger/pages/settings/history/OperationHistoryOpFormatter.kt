package top.mcxiafeng.badger.pages.settings.history

import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * OperationHistoryPage 纯函数格式化 helper。
 *
 * 只读日志视图：仅保留展示用格式化（状态 label / 时间 / 联系人名 / filter label）。
 * 已删除 5 个无调用方的状态分类函数（isPendingStatus 等）与重复的
 * formatTimestampShort（与 Long 同实现，合并为单一 formatTimestamp）。
 */
object OperationHistoryOpFormatter {

    fun formatTimestamp(epoch: Long): String = formatEpoch(epoch)

    /** opStatus 字符串 → 中文 label；未知值原样返回。 */
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

    /** 联系名兜底：history 里存的 contactName 为 null（已删除/已合并/V1 数据）时统一占位。 */
    fun formatContactName(contactName: String?): String = contactName ?: "(已删除)"

    /** 列表项副标题：`[时间] · [opLabel]`。 */
    fun formatListSubtitle(item: OperationHistoryWithContact): String {
        val opLabel = OperationTypes.labelOf(item.history.opType)
        val time = formatTimestamp(item.history.createdAt)
        return "$time  ·  $opLabel"
    }

    /** 详情 dialog 操作摘要：联系人 · opLabel。 */
    fun formatDetailSummary(item: OperationHistoryWithContact): String {
        val contact = formatContactName(item.contactName)
        val opLabel = OperationTypes.labelOf(item.history.opType)
        return "$contact  ·  $opLabel"
    }

    /** filter 中文 label（顶部 tab 用）。 */
    fun formatFilterLabel(filter: HistoryFilter): String = when (filter) {
        HistoryFilter.All -> "全部"
        HistoryFilter.Pending -> "待处理"
    }
}

/** epoch millis → `yyyy-MM-dd HH:mm`（kotlinx-datetime 跨端）。 */
internal fun formatEpoch(epoch: Long): String {
    val local = Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault())
    fun p2(v: Int) = v.toString().padStart(2, '0')
    return "${local.year}-${p2(local.monthNumber)}-${p2(local.dayOfMonth)} ${p2(local.hour)}:${p2(local.minute)}"
}
