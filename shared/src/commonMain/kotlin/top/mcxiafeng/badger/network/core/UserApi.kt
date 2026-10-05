package top.mcxiafeng.badger.network.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.User
import top.mcxiafeng.badger.network.core.PersonApi.Companion.httpCore
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore

class UserApi {

    // 方式一：使用默认配置（默认超时 10秒）
    val httpCore = KtorHttpCore()

    companion object{

        suspend fun getUser(): User?{
            when (val result = httpCore.get(PublicApi.serverUrl + "/api/auth/me", headers = PublicApi.authHeaders())){
                is HttpResult.Success ->{
                    val root = Json.parseToJsonElement(result.body).jsonObject
                    val data = root["data"]?.jsonObject
                    BadgerLog.d("UserApi", "getUser success: ${data != null}")
                    if (data != null) {
                        return Json.decodeFromJsonElement<User>(data)
                    } else {
                        // 服务端畸形响应折 null：调用方按"用户不存在"处理，防 ISE 穿透崩溃
                        BadgerLog.w("UserApi", "getUser: 响应缺 data 对象")
                    }
                }
                is HttpResult.Failure ->{
                    BadgerLog.w("UserApi", "getUser failed: ${result.errorType}")
                }
            }
            return null;
        }


    }

//    suspend fun getShortLinks(): List<User>{
//        val result = httpCore.get(PublicApi.serverUrl, headers = PublicApi.authHeaders());
//        when (result){
//            is HttpResult.Success ->{
//                val rawObj = Json.parseToJsonElement(result.body).jsonObject
//                val patchedObj = buildJsonObject {
//                    // 先把原有的全放进去
//                    rawObj.forEach { (k, v) -> put(k, v) }
//                    // 再补上缺失的
//                    if (!rawObj.containsKey("email")) put("email", "fallback@example.com")
//                }
//                print(jsonElement)
//            }
//            is HttpResult.Failure ->{
//                print(result.errorType)
//            }
//        }
//
//    }


}