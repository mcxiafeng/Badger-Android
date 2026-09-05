package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * [B3] 设备管理 endpoints（新 Java `/api` 契约，`Badger-Server/docs/api-handover.md` §4.4）。
 *
 * - `GET /api/user/devices` → `data: [{uuid,deviceId,deviceName,ip,online,loginTime}, ...]`
 *   服务端一次返回当前用户全部已登录设备。
 * - `PUT /api/user/devices/{uuid}` → `data: null`（重命名设备，body `{ "deviceName": "..." }`）。
 * - `DELETE /api/user/devices/{uuid}` → `data: null`（注销设备，踢下线；当前设备不可自删）。
 *
 * 鉴权走 [ApiCore] Bearer；uuid 在拼路径前校验，拒绝 `/` `?` `#` 以免路径穿越。
 */
class DeviceApi(private val core: ApiCore) {

    /**
     * GET /api/user/devices — 全量设备列表。
     *
     * 单条字段类型异常时跳过该行（有日志），不炸整批。
     */
    fun listDevices(): List<UserDevice> {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] devices.list")
        return core.execute(core.buildRequest("GET", "/api/user/devices").build())
            .unwrapApiResult("devices.list", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    BadgerLog.w(TAG, "[$tag] list: expected data array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    UserDevice.parse(o)
                }
            }
    }

    /**
     * PUT /api/user/devices/{uuid} — 重命名设备。
     *
     * [修复防御]: uuid 拼路径前校验，拒绝 `/` `?` `#` 防路径穿越。
     */
    fun renameDevice(uuid: String, name: String) {
        validateUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] devices.rename uuid=${uuid.take(8)}")
        val payload = buildJsonObject {
            put("deviceName", name)
        }
        // [修复防御]: PUT body 走 buildRequest 的 body 参数，与 AuthApi.login 同模式。
        core.execute(
            core.buildRequest("PUT", "/api/user/devices/$uuid", payload.toString()).build(),
        ).unwrapApiResult("devices.rename", tag) { }
    }

    /**
     * DELETE /api/user/devices/{uuid} — 注销设备（踢下线）。
     *
     * 返回 true 表示成功；404 幂等视为成功（设备已不存在）。
     * 403（当前设备不可自删）**不**吞，抛给调用方。
     *
     * [修复防御]: uuid 拼路径前校验，拒绝 `/` `?` `#` 防路径穿越。
     */
    fun deleteDevice(uuid: String): Boolean {
        validateUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] devices.delete uuid=${uuid.take(8)}")
        return try {
            core.execute(
                core.buildRequest("DELETE", "/api/user/devices/$uuid").build(),
            ).unwrapApiResult("devices.delete", tag) { _ -> true }
        } catch (e: ApiException) {
            // [修复防御]: 404 幂等 —— 设备已删，视为成功；其它错误照抛。
            if (e.status == 404) {
                BadgerLog.d(TAG, "[$tag] devices.delete 404 idempotent")
                true
            } else throw e
        }
    }

    companion object {
        private const val TAG = "DeviceApi"

        /** uuid 校验：拒绝 `/` `?` `#` 防路径穿越（与 NotificationApi 同策略）。 */
        internal fun validateUuid(uuid: String) {
            require('/' !in uuid && '?' !in uuid && '#' !in uuid) {
                "invalid device uuid: contains path/query separator"
            }
        }
    }
}
