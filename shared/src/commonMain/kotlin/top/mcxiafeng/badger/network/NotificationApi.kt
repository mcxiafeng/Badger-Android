package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import top.mcxiafeng.badger.utils.BadgerLog

class NotificationApi(private val core: ApiCore) {

    
    fun getUnreadCount(): Int {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] notifications.unreadCount")
        return core.execute(core.request("GET", "/api/user/notifications/unread-count"))
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

    

    fun listNotifications(): List<UserNotification> {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] notifications.list")
        return core.execute(core.request("GET", "/api/user/notifications"))
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

    
    fun markAsRead(uuid: String) {
        val id = requireNotificationUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] notifications.markAsRead uuid=${id.take(8)}")
        core.execute(core.request("PUT", "/api/user/notifications/$id/read"))
            .unwrapApiResult("notifications.markAsRead", tag) {  }
    }

    

    fun delete(uuid: String): Boolean {
        val id = requireNotificationUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] notifications.delete uuid=${id.take(8)}")
        return try {
            core.execute(core.request("DELETE", "/api/user/notifications/$id"))
                .unwrapApiResult("notifications.delete", tag) {  true }
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

internal fun requireNotificationUuid(uuid: String): String {
    val t = uuid.trim()
    if (t.isEmpty() || t.length > 64 || t.any { it == '/' || it == '?' || it == '#' }) {
        throw IllegalArgumentException("invalid notification uuid")
    }
    return t
}
