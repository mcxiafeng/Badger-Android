package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * [B1] 站内通知 endpoints（新 Java `/api` 契约，`Badger-Server/docs/api-handover.md` §4.7）。
 *
 * - `GET /api/user/notifications` → `data: [{uuid,senderName,title,body,read,createTime}, ...]`
 *   服务端一次返回全量（未读在前、createTime 倒序），**无 page/size**。
 * - `GET /api/user/notifications/unread-count` → `data: { unread }`（导航栏 badge 轮询）。
 * - `PUT /api/user/notifications/{uuid}/read` → `data: null`（已读幂等，重复标记不记 sync）。
 * - `DELETE /api/user/notifications/{uuid}` → `data: null`。
 *
 * 鉴权走 [ApiCore] Bearer；uuid 在拼路径前校验，拒绝 `/` `?` `#` 以免路径穿越。
 */
class NotificationApi(private val core: ApiCore) {

    /** GET /api/user/notifications/unread-count — `data: { unread }`。契约异常时降级 0（有日志）。 */
    fun getUnreadCount(): Int {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] notifications.unreadCount")
        return core.execute(core.buildRequest("GET", "/api/user/notifications/unread-count").build())
            .unwrapApiResult("notifications.unreadCount", tag) { data ->
                val obj = data as? JsonObject
                val unread = obj?.get("unread")
                if (unread == null || unread is JsonNull) {
                    BadgerLog.w(TAG, "[$tag] unread-count missing data.unread, got ${data::class.simpleName}")
                    return@unwrapApiResult 0
                }
                val unreadPrimitive = unread as? JsonPrimitive
                if (!unreadPrimitive.isNumberPrimitive()) {
                    BadgerLog.w(TAG, "[$tag] unread-count unread not number")
                    return@unwrapApiResult 0
                }
                val n = unreadPrimitive?.longOrNull
                    ?: unreadPrimitive?.content?.toDoubleOrNull()?.toLong()
                    ?: 0L
                n.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            }
    }

    /**
     * GET /api/user/notifications — 全量列表。
     *
     * 单条字段类型异常时跳过该行（有日志），不炸整批。
     */
    fun listNotifications(): List<UserNotification> {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] notifications.list")
        return core.execute(core.buildRequest("GET", "/api/user/notifications").build())
            .unwrapApiResult("notifications.list", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    BadgerLog.w(TAG, "[$tag] list: expected data array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    UserNotification.parse(o)
                }
            }
    }

    /** PUT /api/user/notifications/{uuid}/read — 已读幂等。 */
    fun markAsRead(uuid: String) {
        val id = requireNotificationUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] notifications.markAsRead uuid=${id.take(8)}")
        core.execute(core.buildRequest("PUT", "/api/user/notifications/$id/read").build())
            .unwrapApiResult("notifications.markAsRead", tag) { /* data: null */ }
    }

    /**
     * DELETE /api/user/notifications/{uuid}
     *
     * 幂等：404（行不存在）视为已删成功；撞他人 403 原样抛出。
     */
    fun delete(uuid: String): Boolean {
        val id = requireNotificationUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] notifications.delete uuid=${id.take(8)}")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/user/notifications/$id").build())
                .unwrapApiResult("notifications.delete", tag) { /* data: null */ true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                BadgerLog.w(TAG, "[$tag] delete 404: already gone, treating as idempotent success")
                true
            } else throw e
        }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}

/**
 * 路径参数 uuid 边界校验。
 *
 * [修复防御]: 拒绝空串 / 过长 / 含 `/` `?` `#`，避免拼进 URL 后变成路径穿越或 query 注入。
 */
internal fun requireNotificationUuid(uuid: String): String {
    val t = uuid.trim()
    if (t.isEmpty() || t.length > 64 || t.any { it == '/' || it == '?' || it == '#' }) {
        throw IllegalArgumentException("invalid notification uuid")
    }
    return t
}
