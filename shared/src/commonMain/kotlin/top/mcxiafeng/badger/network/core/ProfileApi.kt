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

        val httpCore = KtorHttpCore()

        suspend fun getProfile(uuid: Uuid): Profile? {
            when (val result = httpCore.get(
                PublicApi.serverUrl + "/api/user/profile/$uuid",
                headers = PublicApi.authHeaders()
            )) {
                is HttpResult.Success -> {
                    val rawObj = Json.parseToJsonElement(result.body).jsonObject
                    val data = rawObj.jsonObject["data"]?.jsonObject
                    if(data != null){
                        return Json.decodeFromJsonElement(data);
                    }
                    BadgerLog.w("ProfileApi", "getProfile: 响应缺 data")
                }

                is HttpResult.Failure -> {
                    BadgerLog.w("ProfileApi", "getProfile failed: ${result.errorType}")
                }
            }
            return null;
        }

        suspend fun updateProfile(profile: Profile): Profile? {
            when (val result = httpCore.put(
                PublicApi.serverUrl + "/api/user/profile",
                headers = PublicApi.authHeaders(),
                body = Json.encodeToString(profile)
            )) {
                is HttpResult.Success -> {
                    val rawObj = Json.parseToJsonElement(result.body).jsonObject
                    BadgerLog.d("ProfileApi", "updateProfile success: ${rawObj.containsKey("data")}")
                }

                is HttpResult.Failure -> {
                    BadgerLog.w("ProfileApi", "updateProfile failed: ${result.errorType}")
                }
            }
            return null;
        }

    }


}