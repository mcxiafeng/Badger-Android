package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.BadgerLog

internal class SettingsApi(private val core: ApiCore) {

    
    fun getUserSettings(): UserSettings {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] getSettings")
        return core.execute(core.request("GET", "/api/user/getSettings"))
            .unwrapApiResult("user.getSettings", tag) { data ->
                val obj = data as? JsonObject
                if (obj == null) {
                    BadgerLog.w(TAG, "[$tag] getSettings: expected object, got ${data::class.simpleName}")
                    return@unwrapApiResult UserSettings(null, null, false, null, false)
                }
                UserSettings.from(obj)
            }
    }

    

    fun updateUserSettings(
        language: String? = null,
        theme: String? = null,
        notifyEmail: Boolean? = null,
        shortLinkProvider: String? = null,
        shortioApiKey: String? = null,
        clearShortioApiKey: Boolean? = null,
    ) {
        
        if (language == null && theme == null && notifyEmail == null &&
            shortLinkProvider == null && shortioApiKey == null && clearShortioApiKey == null
        ) return
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            language?.let { put("language", it) }
            theme?.let { put("theme", it) }
            notifyEmail?.let { put("notifyEmail", it) }
            shortLinkProvider?.let { put("shortLinkProvider", it) }
            shortioApiKey?.takeIf { it.isNotBlank() }?.let { put("shortioApiKey", it) }
            clearShortioApiKey?.let { put("clearShortioApiKey", it) }
        }
        BadgerLog.d(TAG, "[$tag] updateSettings: bytes=${payload.toString().length}")
        val body = payload.toString()
        core.execute(core.request("POST", "/api/user/settings", body))
            .unwrapApiResult("user.updateSettings", tag) {  }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
