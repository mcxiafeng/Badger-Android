package top.mcxiafeng.badger.network.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.entity.UserInfo
import top.mcxiafeng.badger.network.core.PublicApi
import top.mcxiafeng.badger.platform.deviceIdentity
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore
import kotlin.uuid.Uuid

class AuthApi {

    companion object {
        private const val TAG = "AuthApi"

        val httpCore = KtorHttpCore()

        /**
         * 登录：POST /api/auth/login（username/password/deviceId/deviceName，参数与
         * 凭据由服务端裁决）→ GET /api/auth/me 取 userUuid → 写入 UserSyncState 载体。
         *
         * 载体写入规则：单会话模型（clear 后仅保留一行）；同一用户重登保留已有
         * syncVersion/lastSyncTime（游标按用户消费 op-log，与设备无关），换用户则
         * 旧会话整行清除（游标从 0 = 首次全量）。凭据与 token 不落日志。
         */
        suspend fun login(username: String, password: String): HttpResult {
            val identity = deviceIdentity()
            val body = buildJsonObject {
                put("username", username.trim())
                put("password", password)
                put("deviceId", identity.deviceId)
                put("deviceName", identity.deviceName)
            }.toString()
            val loginResult = httpCore.post(PublicApi.serverUrl + "/api/auth/login", body = body)
            val token = extractDataString(loginResult, "token")
                ?: return loginResult.also { BadgerLog.w(TAG, "login 失败：${describe(it)}") }

            val meResult = httpCore.get(
                PublicApi.serverUrl + "/api/auth/me",
                headers = mapOf("Authorization" to "Bearer $token"),
            )
            val userUuid = extractDataString(meResult, "uuid")?.let { Uuid.parse(it) }
            if (userUuid == null) {
                BadgerLog.e(TAG, "login 后拉取 /me 失败，无法写入载体：${describe(meResult)}")
                return HttpResult.Failure(0, null, HttpResult.ErrorType.OTHER)
            }

            val dao = SystemDbHolder.get().userInfoDao()
            val previous = dao.getUserInfo(userUuid)
            dao.clearAllUserInfos()
            dao.upsertUserInfo(
                UserInfo(
                    userUuid = userUuid,
                    token = token,
                    syncVersion = previous?.syncVersion ?: 0L,
                    lastSyncTime = previous?.lastSyncTime ?: 0L,
                )
            )
            BadgerLog.d(TAG, "login 成功，载体已写入（user=$userUuid, 游标=${previous?.syncVersion ?: 0L}）")
            return loginResult
        }

        suspend fun register() {

        }

        suspend fun changePassword() {

        }

        suspend fun logout() {

        }

        suspend fun me() {

        }

        suspend fun registerPolicy() {

        }

        suspend fun sendVerificationCode() {

        }

        suspend fun forgotPassword() {

        }

        private fun extractDataString(result: HttpResult, key: String): String? {
            if (result !is HttpResult.Success) return null
            return Json.parseToJsonElement(result.body).jsonObject["data"]
                ?.jsonObject?.get(key)?.jsonPrimitive?.content
        }

        private fun describe(result: HttpResult): String = when (result) {
            is HttpResult.Success -> "success"
            is HttpResult.Failure -> "HTTP ${result.code} (${result.errorType})"
        }
    }
}
