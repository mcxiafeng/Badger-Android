package top.mcxiafeng.badger.pages.settings.history

import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

/**
 * [V2-P7] OperationHistoryPage 的纯函数格式化 helper。
 *
 * [Phase 3] 移除撤销 / 重发 / 冲突解决相关方法（队列退役，历史页只读）。
 * 只保留展示用格式化：状态 label、时间、联系人名、filter label。
 */
object OperationHistoryOpFormatter {

    fun formatTimestampShort(epoch: Long): String =
        formatEpoch(epoch)

    fun formatTimestampLong(epoch: Long): String =
        formatEpoch(epoch)

    /**
     * opStatus 字符串 → 中文 label。简单映射,UI 层调用 `formatStatus` 后再决定配色。
     */
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

    /**
     * 状态语义分类,UI 用来分桶显示(used for filter / 排序权重等)。
     */
    fun isPendingStatus(opStatus: String): Boolean =
        opStatus == "CONFLICT" || opStatus == "FAILED_PERMANENT"

    fun isSuccessStatus(opStatus: String): Boolean = opStatus == "DONE"

    fun isFailedStatus(opStatus: String): Boolean =
        opStatus == "FAILED" || opStatus == "FAILED_PERMANENT"

    fun isWithdrawnStatus(opStatus: String): Boolean = opStatus == "WITHDRAWN"

    fun isInFlightStatus(opStatus: String): Boolean =
        opStatus == "IN_FLIGHT" || opStatus == "PENDING"

    /**
     * 联系名兜底:history 里存的 contactId 找不到联系人(已删除 / 已被合并 / V1 时期数据)
     * 统一显示占位字符串,UI 列表项不至于空白。
     */
    fun formatContactName(contactName: String?): String =
        contactName ?: "(已删除)"

    /**
     * line subtitle: `[时间] · [opLabel]`。一行紧凑显示,UI 列表项用。
     */
    fun formatListSubtitle(item: OperationHistoryWithContact): String {
        val opLabel = OperationTypes.labelOf(item.history.opType)
        val time = formatTimestampShort(item.history.createdAt)
        return "$time  ·  $opLabel"
    }

    /**
     * 详情 dialog 用的操作摘要:联系人 / opLabel。
     */
    fun formatDetailSummary(item: OperationHistoryWithContact): String {
        val contact = formatContactName(item.contactName)
        val opLabel = OperationTypes.labelOf(item.history.opType)
        return "$contact  ·  $opLabel"
    }

    /**
     * filter 中文 label(顶部 tab 用)。
     */
    fun formatFilterLabel(filter: HistoryFilter): String = when (filter) {
        HistoryFilter.All -> "全部"
        HistoryFilter.Pending -> "待处理"
    }
}

/** [KMP K13c] epoch millis → `yyyy-MM-dd HH:mm`（kotlinx-datetime 跨端）。 */
internal fun formatEpoch(epoch: Long): String {
    val local = Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault())
    fun p2(v: Int) = v.toString().padStart(2, '0')
    return "${local.year}-${p2(local.monthNumber)}-${p2(local.dayOfMonth)} ${p2(local.hour)}:${p2(local.minute)}"
}
