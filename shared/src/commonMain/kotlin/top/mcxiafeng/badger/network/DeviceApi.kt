package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.BadgerLog

class DeviceApi(private val core: ApiCore) {

    

    fun listDevices(): List<UserDevice> {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] devices.list")
        return core.execute(core.request("GET", "/api/user/devices"))
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

    

    fun renameDevice(uuid: String, name: String) {
        validateUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] devices.rename uuid=${uuid.take(8)}")
        val payload = buildJsonObject {
            put("deviceName", name)
        }
        
        core.execute(
            core.request("PUT", "/api/user/devices/$uuid", payload.toString()),
        ).unwrapApiResult("devices.rename", tag) { }
    }

    

    fun deleteDevice(uuid: String): Boolean {
        validateUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] devices.delete uuid=${uuid.take(8)}")
        return try {
            core.execute(
                core.request("DELETE", "/api/user/devices/$uuid"),
            ).unwrapApiResult("devices.delete", tag) { _ -> true }
        } catch (e: ApiException) {
            
            if (e.status == 404) {
                BadgerLog.d(TAG, "[$tag] devices.delete 404 idempotent")
                true
            } else throw e
        }
    }

    companion object {
        private const val TAG = "DeviceApi"

        
        internal fun validateUuid(uuid: String) {
            require('/' !in uuid && '?' !in uuid && '#' !in uuid) {
                "invalid device uuid: contains path/query separator"
            }
        }
    }
}
