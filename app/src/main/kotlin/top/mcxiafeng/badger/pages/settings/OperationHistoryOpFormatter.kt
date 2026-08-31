package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** OperationHistoryPage 的纯函数格式化 helper。 */
object OperationHistoryOpFormatter {

    private val dateTimeFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    private val dateTimeSecondsFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    fun formatTimestampShort(epoch: Long): String = dateTimeFormat.format(Date(epoch))

    fun formatTimestampLong(epoch: Long): String = dateTimeSecondsFormat.format(Date(epoch))

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

    /** 待处理列表与 Repository 过滤条件保持一致。 */
    fun isPendingStatus(opStatus: String): Boolean =
        opStatus == "CONFLICT" ||
            opStatus == "FAILED" ||
            opStatus == "FAILED_PERMANENT"

    fun isSuccessStatus(opStatus: String): Boolean = opStatus == "DONE"

    fun isFailedStatus(opStatus: String): Boolean =
        opStatus == "FAILED" || opStatus == "FAILED_PERMANENT"

    fun isWithdrawnStatus(opStatus: String): Boolean = opStatus == "WITHDRAWN"

    fun isInFlightStatus(opStatus: String): Boolean =
        opStatus == "IN_FLIGHT" || opStatus == "PENDING"

    fun formatContactName(contactName: String?): String = contactName ?: "(已删除)"

    fun formatListSubtitle(item: OperationHistoryWithContact): String {
        val opLabel = OperationTypes.labelOf(item.history.opType)
        val time = formatTimestampShort(item.history.createdAt)
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
