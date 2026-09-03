package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 用户个人设置 endpoints（新 Java `/api` 契约，`Badger-Server/docs/api-handover.md` §4.8）。
 *
 * - `GET /api/user/getSettings` → `data: { language, theme, notifyEmail, shortLinkProvider, shortioApiKeySet }`
 *   shortioApiKey 绝不明文回传（只回布尔 `shortioApiKeySet`）。
 * - `POST /api/user/settings` → body `{ language?, theme?, notifyEmail?, shortLinkProvider?, shortioApiKey?, clearShortioApiKey? }`
 *   空白 shortioApiKey = 保留已存；显式 `clearShortioApiKey: true` 才清。
 */
internal class SettingsApi(private val core: ApiCore) {

    /** GET /api/user/getSettings — 个人设置。 */
    fun getUserSettings(): UserSettings {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] getSettings")
        return core.execute(core.buildRequest("GET", "/api/user/getSettings").build())
            .unwrapApiResult("user.getSettings", tag) { data ->
                val obj = data as? JsonObject
                if (obj == null) {
                    Log.w(TAG, "[$tag] getSettings: expected object, got ${data::class.simpleName}")
                    return@unwrapApiResult UserSettings(null, null, false, null, false)
                }
                UserSettings.from(obj)
            }
    }

    /**
     * POST /api/user/settings — 更新个人设置。
     *
     * 仅传非 null 字段（浅合并语义）。`clearShortioApiKey`=true 时清除已存密钥。
     */
    fun updateUserSettings(
        language: String? = null,
        theme: String? = null,
        notifyEmail: Boolean? = null,
        shortLinkProvider: String? = null,
        shortioApiKey: String? = null,
        clearShortioApiKey: Boolean? = null,
    ) {
        // [修复防御]: 全 null 时跳过空 body POST，避免无意义网络开销
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
        Log.d(TAG, "[$tag] updateSettings: bytes=${payload.toString().length}")
        val body = payload.toString()
        core.execute(core.buildRequest("POST", "/api/user/settings", body).build())
            .unwrapApiResult("user.updateSettings", tag) { /* data: null */ }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
