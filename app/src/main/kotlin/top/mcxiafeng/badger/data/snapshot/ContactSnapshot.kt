package top.mcxiafeng.badger.data.snapshot

import com.google.gson.annotations.SerializedName

/**
 * [V2-P3] 联系人的"完整快照"数据结构,用于 [ContactSnapshotter] 序列化。
 *
 * 这是 OperationHistory 记录 before / after 状态时写入 JSON 的形态。
 * 设计要点(对应 `docs/BADGER_V2_CLIENT_PLAN.md` §5.5.1 + §6.4):
 * 1. **version 字段**:envelope 顶端写 `version: 1`,未来字段扩展不破坏旧 history。
 * 2. **不包含 avatar**:本地 avatarPath(磁盘文件路径)/ avatarUrl(远端 URL)都进 snapshot;
 *    不含 base64 / 头像二进制 — 防止 history 表膨胀(§5.5.1 第 12 行决策)。
 * 3. **关联子表**:platforms / fieldValues / tags 同步内联,撤销时直接按 snapshot 整体回滚。
 * 4. **字段容错**:所有字段缺省都有合理兜底(null / 空集合),Gson 在 JSON 缺字段时不抛异常。
 * 5. **空数据**:`ContactSnapshot.empty(id)` 给"被删除前已经没有快照"的极端场景兜底。
 *
 * 与 [top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity] 的关系:
 * - ContactCacheEntity 是 Room 表行;
 * - ContactSnapshot 是 cache entity + 关联子表 + 元信息的**完整对象快照**。
 *
 * [修复防御]:快照字段命名用 snake_case(`server_id` / `server_version`),与 §3.2 服务端
 * 协议保持一致,history 入服务端时不需要再转换格式(若未来需要)。
 */
internal data class ContactSnapshot(
    @SerializedName("version") val version: Int = SNAPSHOT_VERSION,
    @SerializedName("captured_at") val capturedAt: Long,
    @SerializedName("contact_id") val contactId: Long,
    @SerializedName("server_id") val serverId: String? = null,
    @SerializedName("name") val name: String = "",
    @SerializedName("note") val note: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("pinyin_initial") val pinyinInitial: String = "",
    @SerializedName("server_version") val serverVersion: Long = 0L,
    @SerializedName("last_synced_at") val lastSyncedAt: Long = 0L,
    @SerializedName("is_local_only") val isLocalOnly: Boolean = true,
    @SerializedName("is_deleted") val isDeleted: Boolean = false,
    @SerializedName("platforms") val platforms: Map<String, SnapshotPlatformEntry> = emptyMap(),
    @SerializedName("field_values") val fieldValues: List<SnapshotFieldValue> = emptyList(),
    @SerializedName("tags") val tags: List<SnapshotTagRef> = emptyList(),
) {
    companion object {
        const val SNAPSHOT_VERSION = 1

        /**
         * 极端场景:某联系人已经不存在任何状态时(理论上不应触发,history 写入前应该先
         * 读 cache),给一个最小可序列化的"空快照",避免 Gson 抛 NPE。
         */
        fun empty(contactId: Long, capturedAt: Long = System.currentTimeMillis()): ContactSnapshot =
            ContactSnapshot(
                capturedAt = capturedAt,
                contactId = contactId,
                name = "",
            )
    }
}

/**
 * 快照中的平台条目(与 [top.mcxiafeng.badger.data.PlatformEntry] 字段对齐)。
 * 用独立 class 而不是直接复用 PlatformEntry,是因为:
 * 1. PlatformEntry 没有 @SerializedName,在 Gson 反射下默认用字段名(已经是 snake-camel,
 *    但顶层 ContactSnapshot 用了 snake_case 风格);保持一致风格便于排查。
 * 2. 快照字段缺省值与运行时 PlatformEntry 不同(快照可能 value=null,jumpLink="" 也 OK)。
 */
internal data class SnapshotPlatformEntry(
    @SerializedName("value") val value: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("jump_link") val jumpLink: String = "",
    @SerializedName("original_link") val originalLink: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
)

/**
 * 快照中的字段值(对应 contact_field_values_cache)。
 */
internal data class SnapshotFieldValue(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("field_id") val fieldId: Long? = null,
    @SerializedName("custom_field_id") val customFieldId: Long? = null,
    @SerializedName("value") val value: String = "",
    @SerializedName("display_order") val displayOrder: Int = 0,
)

/**
 * 快照中的标签引用(只存最小信息,name 冗余存储是为了撤销时不依赖当前 tag 表)。
 *
 * [修复防御]:撤销时若 tag 已被删,我们仍能从 snapshotBeforeJson 拿到当时的 tagName,
 * UI 渲染"撤销: 删除标签 X"时不会显示空字符串。
 *
 * `data class` 字段语义稳定,公开暴露给 [RestoredContact] 作为字段类型,允许 caller
 * 读取但不应修改其结构。修改本类的字段名/类型是 breaking change,需要走 bumpVersion。
 */
data class SnapshotTagRef(
    @SerializedName("tag_id") val tagId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("source") val source: String = "manual",
    @SerializedName("confidence") val confidence: Float = 1.0f,
)