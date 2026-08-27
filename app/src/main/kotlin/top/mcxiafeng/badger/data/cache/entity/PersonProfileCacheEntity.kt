package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * [Phase 2] Person Profile 子表（`person_profile_cache`）。
 *
 * 存储 `ProfileDto` 中原先未持久化的 6 个字段：sex / country / region / birthday / backgroundURL / extra。
 * 与 `contacts_cache` 一对一（主键 `contactServerId` = `contacts_cache.serverId`）。
 *
 * 设计决策（docs/architecture-refactor-plan.md Q1）：
 * - 新建子表而非给 `contacts_cache` 加列，避免主表继续膨胀；
 * - 主键直接用 `contactServerId`（非自增 Long），保证 1:1 约束在 schema 层强制执行；
 * - `@Upsert` 按主键 `contactServerId` 匹配，重复 sync 同一 Person 正确走 UPDATE 路径；
 * - sync ADD/UPDATE Person 时写入；本地纯新增 Person（isLocalOnly=true）暂不写（无 profile 数据）。
 */
@Entity(
    tableName = "person_profile_cache",
    foreignKeys = [
        ForeignKey(
            entity = ContactCacheEntity::class,
            parentColumns = ["serverId"],
            childColumns = ["contactServerId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class PersonProfileCacheEntity(
    /** 主键 = `contacts_cache.serverId`（服务端 Person uuid），保证 1:1。 */
    @PrimaryKey val contactServerId: String,
    val sex: String? = null,
    val country: String? = null,
    val region: String? = null,
    val birthday: String? = null,
    val backgroundURL: String? = null,
    /** ProfileDto.extra 原始 JSON（`Map<String,Map<String,Object>>`），客户端透传。 */
    val extra: String? = null,
)
