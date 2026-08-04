package top.mcxiafeng.badger.data.queue

/**
 * [V2-P5] PendingUpload opType 常量集中定义(对齐 `docs/BADGER_V2_CLIENT_PLAN.md` §5.1)。
 *
 * 设计要点:
 * - opType 是 **字符串** 而非 enum,因为 Room/PendingUploadEntity.opType 是 `String`,
 *   历史表 `operation_history.opType` 也是 String。统一 String 可避免 enum 改名时的
 *   Room migration。
 * - `labelOf(opType)` 返回中文展示名(历史页 / 通知用),缺失时回退到 opType 本身。
 * - P6 关键操作(DELETE_CONTACT / MERGE_CONTACT 等)与 P5 普通 CRUD 同源定义,
 *   便于 P7 历史页统一渲染。
 */
object OperationTypes {

    // ============ §5.1 普通 CRUD 走队列(opType 命名严格对齐 §5.1 表) ============

    const val UPDATE_NAME = "UPDATE_NAME"
    const val UPDATE_NOTE = "UPDATE_NOTE"
    const val UPDATE_BIO = "UPDATE_BIO"
    const val ADD_PLATFORM = "ADD_PLATFORM"
    const val UPDATE_PLATFORM = "UPDATE_PLATFORM"
    const val REMOVE_PLATFORM = "REMOVE_PLATFORM"
    const val ADD_FIELD_VALUE = "ADD_FIELD_VALUE"
    const val UPDATE_FIELD_VALUE = "UPDATE_FIELD_VALUE"
    const val REMOVE_FIELD_VALUE = "REMOVE_FIELD_VALUE"
    const val ADD_TAG = "ADD_TAG"
    const val REMOVE_TAG = "REMOVE_TAG"
    const val STAR = "STAR"
    const val UNSTAR = "UNSTAR"

    /** §5.2 新建联系人走乐观(本地分配 id,服务端 id 由后续 sync 补上)。 */
    const val CREATE_CONTACT = "CREATE_CONTACT"

    // ============ §5.2 关键操作(P6 阶段接入,P5 留定义但不使用) ============

    /** [P6] 删除联系人(双通道:直发 + Worker 兜底)。 */
    const val DELETE_CONTACT = "DELETE_CONTACT"

    /** [P6] 批量删除。 */
    const val BATCH_DELETE = "BATCH_DELETE"

    /** [P6] 合并联系人。 */
    const val MERGE_CONTACT = "MERGE_CONTACT"

    // ============ [V2-P12] 非 contact 域(Profile / Tag / Collection)走队列 ============

    /** 修改「我的名片」profile 整体(name / bio / avatarUrl / platformsJson)。 */
    const val USER_PROFILE_UPSERT = "USER_PROFILE_UPSERT"

    /** 新建/更新标签(upsert 语义,服务端按 name 去重)。 */
    const val TAG_UPSERT = "TAG_UPSERT"

    /** 删除标签。 */
    const val TAG_DELETE = "TAG_DELETE"

    /** 新建/更新名片夹。 */
    const val COLLECTION_UPSERT = "COLLECTION_UPSERT"

    /** 删除名片夹。 */
    const val COLLECTION_DELETE = "COLLECTION_DELETE"

    /** [P8] 撤销某 op 时入队的反向 op,在原 opType 后追加 "_UNDO"。 */
    const val UNDO_SUFFIX = "_UNDO"

    // ============ Label 映射(中文展示名,历史页 / 通知 / 调试用) ============

    private val LABELS: Map<String, String> = mapOf(
        UPDATE_NAME to "修改姓名",
        UPDATE_NOTE to "修改备注",
        UPDATE_BIO to "修改个人简介",
        ADD_PLATFORM to "添加联系方式",
        UPDATE_PLATFORM to "更新联系方式",
        REMOVE_PLATFORM to "删除联系方式",
        ADD_FIELD_VALUE to "添加字段值",
        UPDATE_FIELD_VALUE to "更新字段值",
        REMOVE_FIELD_VALUE to "删除字段值",
        ADD_TAG to "添加标签",
        REMOVE_TAG to "移除标签",
        STAR to "收藏",
        UNSTAR to "取消收藏",
        CREATE_CONTACT to "创建联系人",
        DELETE_CONTACT to "删除联系人",
        BATCH_DELETE to "批量删除",
        MERGE_CONTACT to "合并联系人",
        USER_PROFILE_UPSERT to "更新我的名片",
        TAG_UPSERT to "更新标签",
        TAG_DELETE to "删除标签",
        COLLECTION_UPSERT to "更新名片夹",
        COLLECTION_DELETE to "删除名片夹",
    )

    /**
     * 取中文展示名;未注册则回退 opType 本身(便于扩展新 opType 时不报错)。
     *
     * 历史页 / 通知一律调此处,不要硬编码中文 — 否则 P8 撤销(_UNDO 后缀)时
     * 显示"UPDATE_NAME_UNDO"很难看。
     */
    fun labelOf(opType: String): String {
        // [修复防御]: P8 撤销时 opType = "UPDATE_NAME_UNDO",label 也要变 "撤销 修改姓名"
        if (opType.endsWith(UNDO_SUFFIX)) {
            val base = opType.removeSuffix(UNDO_SUFFIX)
            return "撤销 ${labelOf(base)}"
        }
        return LABELS[opType] ?: opType
    }
}