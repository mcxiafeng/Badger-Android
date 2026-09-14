package top.mcxiafeng.badger.data.queue

object OperationTypes {

    

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
    const val CREATE_CONTACT = "CREATE_CONTACT"
    const val DELETE_CONTACT = "DELETE_CONTACT"
    const val BATCH_DELETE = "BATCH_DELETE"
    const val MERGE_CONTACT = "MERGE_CONTACT"

    

    const val USER_PROFILE_UPSERT = "USER_PROFILE_UPSERT"
    const val TAG_UPSERT = "TAG_UPSERT"
    const val TAG_DELETE = "TAG_DELETE"
    const val COLLECTION_UPSERT = "COLLECTION_UPSERT"
    const val COLLECTION_DELETE = "COLLECTION_DELETE"

    
    const val UNDO_SUFFIX = "_UNDO"

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

    

    fun labelOf(opType: String): String {
        if (opType.endsWith(UNDO_SUFFIX)) {
            val base = opType.removeSuffix(UNDO_SUFFIX)
            return "撤销 ${labelOf(base)}"
        }
        return LABELS[opType] ?: opType
    }
}
