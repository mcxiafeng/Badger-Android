package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.data.queue.OperationHistoryEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.repository.HistoryFilter
import top.mcxiafeng.badger.data.repository.OperationHistoryWithContact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [V2-P7] OperationHistoryPage 的纯函数格式化 helper。
 *
 * 把 `OperationHistoryEntity` 的 opStatus、opType、inversePayloadJson 等原始字段
 * 转成 UI 展示用的中文 label / 状态徽章 key / 时间字符串。所有 function 都是 pure,
 * 容易在 VM / UI / test 三层复用。
 *
 * [修复防御]:状态徽章颜色由 [OperationHistoryStatusBadge] 这类 @Composable 函数返回,
 * 不能在这里 invoke `MiuixTheme.colorScheme` (`@Composable` 不能从纯函数调用)。
 * 这里只暴露 `opStatusKey` 字符串,UI 层在 @Composable 上下文里做颜色映射。
 */
object OperationHistoryOpFormatter {

    /**
     * 时间格式(yyyy-MM-dd HH:mm)。列表项用短格式,详情 dialog 用长格式。
     */
    private val dateTimeFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    private val dateTimeSecondsFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    fun formatTimestampShort(epoch: Long): String =
        dateTimeFormat.format(Date(epoch))

    fun formatTimestampLong(epoch: Long): String =
        dateTimeSecondsFormat.format(Date(epoch))

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
     * 撤销按钮置灰条件(集中收敛,避免 UI 散落判断):
     * - WITHDRAWN 已撤销过
     * - DONE 但 canUndo=false(创建类不可撤销)
     * - FAILED_PERMANENT 永久失败(只能重发)
     * - CONFLICT 需先解决冲突
     */
    fun isUndoDisabled(op: OperationHistoryEntity): Boolean = when (op.opStatus) {
        "WITHDRAWN" -> true
        "DONE" -> !op.canUndo
        "FAILED_PERMANENT" -> true
        "CONFLICT" -> true
        else -> false
    }

    /**
     * 重发按钮置灰条件:已 WITHDRAWN / 已 DONE 都不能重发。
     */
    fun isRetryDisabled(op: OperationHistoryEntity): Boolean = when (op.opStatus) {
        "WITHDRAWN", "DONE" -> true
        else -> false
    }

    /**
     * 解决冲突按钮(采用本地 / 采用服务端)置灰条件:仅 CONFLICT 可点。
     */
    fun isResolveDisabled(op: OperationHistoryEntity): Boolean = op.opStatus != "CONFLICT"

    /**
     * [V2-P10] 该 op 是否可参与批量重试。
     *
     * 限定规则:仅 FAILED 状态可批量重试。其他状态(retryNow 是 SQL UPDATE 没有 WHERE status
     * 过滤,会"无副作用但算成功"——这里提前过滤避免误标)。
     */
    fun canBatchRetry(op: OperationHistoryEntity): Boolean = op.opStatus == "FAILED"

    /**
     * [V2-P10] 该 op 是否可参与批量撤销。
     *
     * 限定规则:
     * - canUndo=false:不允许任何撤销(对应 DELETE_CONTACT 等)
     * - WITHDRAWN:已撤销过
     * - CONFLICT:必须单条"采用本地/服务端"解决
     */
    fun canBatchWithdraw(op: OperationHistoryEntity): Boolean = op.canUndo &&
        op.opStatus != "WITHDRAWN" &&
        op.opStatus != "CONFLICT"

    /**
     * filter 中文 label(顶部 tab 用)。
     */
    fun formatFilterLabel(filter: HistoryFilter): String = when (filter) {
        HistoryFilter.All -> "全部"
        HistoryFilter.Pending -> "待处理"
    }

    /**
     * [V2-P8] 判断一个 WITHDRAWN op 是否走"仅本地回滚"路径。
     *
     * `OperationHistoryRepositoryImpl.withdraw` 对 `SUPPORTS_UNDO_OP_TYPES`(UPDATE_NAME/BIO/NOTE、
     * CREATE_CONTACT)走完整双边同步 — 入 PendingUpload 反向 op,由 Worker 推服务端。
     * 其他 opType(ADD/UPDATE/REMOVE_PLATFORM、UPDATE_FIELD_VALUE、ADD/REMOVE_TAG、
     * STAR、UNSTAR 等)P9+ 之前只本地回滚,服务端反向同步未接入。
     *
     * UI 用此 flag 在 WITHDRAWN 行追加"撤销仅本地"提示,避免用户误以为同步完成。
     */
    fun isLocalOnlyUndo(op: OperationHistoryEntity): Boolean {
        if (op.opStatus != "WITHDRAWN") return false
        val baseOpType = op.opType.removeSuffix(OperationTypes.UNDO_SUFFIX)
        return baseOpType !in SUPPORTS_UNDO_OP_TYPES_FOR_FORMATTER
    }

    /**
     * WITHDRAWN op 的"是否仅本地"附加 label,UI 列表 subtitle / 详情 dialog 拼接到 label 末尾。
     * 返回空串表示不需要附加(已双边同步或非 WITHDRAWN)。
     */
    fun localOnlySuffix(op: OperationHistoryEntity): String =
        if (isLocalOnlyUndo(op)) " · 仅本地" else ""
}

/**
 * 与 `OperationHistoryRepositoryImpl.SUPPORTS_UNDO_OP_TYPES` 同步。
 * 复制一份避免模块循环依赖(repository → settings 反向依赖反而更糟)。
 * 仅 `OperationHistoryOpFormatter` 内部访问。
 */
private val SUPPORTS_UNDO_OP_TYPES_FOR_FORMATTER = setOf(
    OperationTypes.UPDATE_NAME,
    OperationTypes.UPDATE_BIO,
    OperationTypes.UPDATE_NOTE,
    OperationTypes.CREATE_CONTACT,
)