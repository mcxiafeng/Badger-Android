package top.mcxiafeng.badger.network.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore
import kotlin.uuid.Uuid

class TagsApi {

    companion object {
        val httpCore = KtorHttpCore()

        suspend fun getTags(): List<Tags>? {
            when (val result = httpCore.get(PublicApi.serverUrl + "/api/user/tags", headers = PublicApi.authHeaders())) {
                is HttpResult.Success -> {
                    val root = Json.parseToJsonElement(result.body).jsonObject
                    val data = root["data"]?.jsonArray
                    BadgerLog.d("TagsApi", "getTags success: ${data != null}")
                    if (data != null) {
                        return Json.decodeFromJsonElement<List<Tags>>(data)
                    } else {
                        // 服务端畸形响应折 null：由 sync 层 requireBody 统一翻译为 SGX 留队，ISE 会穿透崩溃
                        BadgerLog.w("TagsApi", "getTags: 响应缺 data 数组")
                    }
                }
                is HttpResult.Failure -> {
                    BadgerLog.w("TagsApi", "getTags failed: ${result.errorType}")
                }
            }
            return null;
        }

        suspend fun createTag(tag: Tags): HttpResult {
            return httpCore.post(PublicApi.serverUrl + "/api/user/tags", headers = PublicApi.authHeaders(), body = Json.encodeToString(tag));
        }

        suspend fun updateTag(tag: Tags): HttpResult {
            return httpCore.put(url = PublicApi.serverUrl + "/api/user/tags/${tag.uuid}", body = Json.encodeToString(tag), headers = PublicApi.authHeaders());
        }

        suspend fun deleteTag(tagId: Uuid): HttpResult {
            return httpCore.delete(url = PublicApi.serverUrl + "/api/user/tags/$tagId", headers = PublicApi.authHeaders());
        }

        suspend fun addTagMember(tagId: Uuid, personUuid: Uuid): HttpResult {
            return httpCore.post(PublicApi.serverUrl + "/api/user/tags/$tagId/members", headers = PublicApi.authHeaders(), body = Json.encodeToString(mapOf("personUuid" to personUuid)));
        }

        suspend fun removeTagMember(tagId: Uuid, personUuid: Uuid): HttpResult {
            return httpCore.delete(url = PublicApi.serverUrl + "/api/user/tags/$tagId/members/$personUuid", headers = PublicApi.authHeaders());
        }
    }


}
