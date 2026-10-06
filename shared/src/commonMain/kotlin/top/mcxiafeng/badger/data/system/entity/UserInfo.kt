package top.mcxiafeng.badger.data.system.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

/**
 * 用户同步状态载体（System 库）：账号级凭据与增量同步游标，一用户一行。
 *
 * 写入契约（勿破坏）：
 * - [token] 仅在登录/换发时写入；API 层从这里取，替代硬编码 token；
 * - [syncVersion] 是拉取游标 = 已消费到的服务端 User.syncVersion（该字段服务端
 *   新引入，存量用户为 null/0），增量拉取以 since=syncVersion 请求；0 表示从未
 *   同步或该用户无历史（首次拉取 since=0 = 全量重放）；
 * - [lastSyncTime] 仅允许写服务端下发的时间（/api/user/sync 响应 changes 行的
 *   createTime，服务端时钟），客户端本地 Clock 严禁写入；0 表示尚未同步；
 * - 两者有偏差时以 [syncVersion] 为准：是否推进游标只看版本号，时间只随行记录。
 */
@Entity
data class UserInfo(
    @PrimaryKey val userUuid: Uuid,
    val token: String,
    val syncVersion: Long = 0L,
    val lastSyncTime: Long = 0L,
    val serverIps: List<String> = emptyList(),
)
