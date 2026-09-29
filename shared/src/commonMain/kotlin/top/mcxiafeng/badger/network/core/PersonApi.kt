package top.mcxiafeng.badger.network.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore
import kotlin.uuid.Uuid

class PersonApi {

    companion object {
        val httpCore = KtorHttpCore()
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        suspend fun getPersons(): List<Person>? {
            return when (val result = httpCore.get(
                PublicApi.serverUrl + "/api/user/persons",
                headers = PublicApi.authHeaders(),
            )) {
                is HttpResult.Success -> {
                    try {
                        val root = json.parseToJsonElement(result.body).jsonObject
                        val data = root["data"]?.jsonArray
                        BadgerLog.d("PersonApi", "getPersons 成功")
                        if (data != null) {
                            json.decodeFromJsonElement<List<Person>>(data)
                        } else {
                            BadgerLog.w("PersonApi", "getPersons 响应中没有 data 数组")
                            null
                        }
                    } catch (e: Exception) {
                        BadgerLog.e("PersonApi", "getPersons 解析 JSON 异常", e)
                        null
                    }
                }

                is HttpResult.Failure -> {
                    BadgerLog.w("PersonApi", "getPersons 失败: ${result.errorType}")
                    null
                }
            }
        }

        suspend fun getPerson(personId: Uuid): Person? {
            return when (val result = httpCore.get(
                PublicApi.serverUrl + "/api/user/persons/$personId",
                headers = PublicApi.authHeaders(),
            )) {
                is HttpResult.Success -> {
                    try {
                        val root = json.parseToJsonElement(result.body).jsonObject
                        val data = root["data"]?.jsonObject
                        BadgerLog.d("PersonApi", "getPerson 成功")
                        if (data != null) {
                            json.decodeFromJsonElement<Person>(data)
                        } else {
                            BadgerLog.w("PersonApi", "getPerson 响应中没有 data 对象")
                            null
                        }
                    } catch (e: Exception) {
                        BadgerLog.e("PersonApi", "getPerson 解析 JSON 异常", e)
                        null
                    }
                }

                is HttpResult.Failure -> {
                    BadgerLog.w("PersonApi", "getPerson 失败: ${result.errorType}")
                    null
                }
            }
        }

        suspend fun createPerson(person: Person): HttpResult {
            return httpCore.post(
                PublicApi.serverUrl + "/api/user/persons",
                headers = PublicApi.authHeaders(),
                body = json.encodeToString(person),
            )
        }

        suspend fun deletePerson(personId: Uuid): HttpResult {
            return httpCore.delete(
                url = PublicApi.serverUrl + "/api/user/persons/$personId",
                headers = PublicApi.authHeaders(),
            )
        }

        suspend fun updatePerson(person: Person): HttpResult {
            return httpCore.put(
                url = PublicApi.serverUrl + "/api/user/persons/${person.uuid}",
                body = json.encodeToString(person),
                headers = PublicApi.authHeaders(),
            )
        }
    }
}
