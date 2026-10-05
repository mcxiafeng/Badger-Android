package top.mcxiafeng.badger.network.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore
import kotlin.uuid.Uuid

class ProfileApi {

    companion object {

        private const val TAG = "ProfileApiTester"

        val httpCore = KtorHttpCore()

        /** 原始 HttpResult：404 与网络失败由调用方（sync 层）区分，不在本层折叠。 */
        suspend fun getProfile(uuid: Uuid): HttpResult =
            httpCore.get(
                PublicApi.serverUrl + "/api/user/profile/$uuid",
                headers = PublicApi.authHeaders(),
            )

        suspend fun updateProfile(profile: Profile): HttpResult =
            httpCore.put(
                PublicApi.serverUrl + "/api/user/profile",
                headers = PublicApi.authHeaders(),
                body = Json.encodeToString(profile),
            ).also { result ->
                when (result) {
                    is HttpResult.Success -> BadgerLog.d(TAG, "updateProfile success")
                    is HttpResult.Failure -> BadgerLog.w(TAG, "updateProfile failed: ${result.errorType}")
                }
            }

    }


}