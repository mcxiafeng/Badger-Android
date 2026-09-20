package top.mcxiafeng.badger.network.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.User
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore
import kotlin.uuid.Uuid

class PersonApi {

    companion object {
        val httpCore = KtorHttpCore()

        suspend fun getPersons(): List<Person>?{
            when (val result = httpCore.get(PublicApi.serverUrl + "/api/user/persons", headers = PublicApi.authHeaders())){
                is HttpResult.Success ->{
                    val root = Json.parseToJsonElement(result.body).jsonObject
                    val data = root["data"]?.jsonArray
                    BadgerLog.d("PersonApi", "getPersons success")
                    if (data != null) {
                        return Json.decodeFromJsonElement<List<Person>>(data)
                    } else {
                        throw IllegalStateException("响应中没有 data 字段或不是数组")
                    }
                }
                is HttpResult.Failure ->{
                    BadgerLog.w("PersonApi", "getPersons failed: ${result.errorType}")
                }
            }
            return null;
        }


        suspend fun getPerson(personId: Uuid): Person? {
            when (val result = httpCore.get(PublicApi.serverUrl + "/api/user/persons/$personId", headers = PublicApi.authHeaders(),
            )){
                is HttpResult.Success ->{
                    val root = Json.parseToJsonElement(result.body).jsonObject
                    val data = root["data"]?.jsonObject
                    BadgerLog.d("PersonApi", "getPersons success")
                    if (data != null) {
                        return Json.decodeFromJsonElement<Person>(data)
                    } else {
                        throw IllegalStateException("响应中没有 data 字段或不是数组")
                    }
                }
                is HttpResult.Failure ->{
                    BadgerLog.w("PersonApi", "getPersons failed: ${result.errorType}")
                    return null;
                }
            }
        }

        suspend fun createPerson(person: Person): HttpResult {
            return httpCore.post(PublicApi.serverUrl + "/api/user/persons", headers = PublicApi.authHeaders(),body = Json.encodeToString(person));
        }

        suspend fun deletePerson(personId: Uuid): HttpResult{
            return httpCore.delete(url = PublicApi.serverUrl + "/api/user/persons/$personId", headers = PublicApi.authHeaders());
        }

        suspend fun updatePerson(person: Person): HttpResult{
            return httpCore.put(url = PublicApi.serverUrl + "/api/user/persons/${person.uuid}", body = Json.encodeToString(person), headers = PublicApi.authHeaders());
        }
    }


}

