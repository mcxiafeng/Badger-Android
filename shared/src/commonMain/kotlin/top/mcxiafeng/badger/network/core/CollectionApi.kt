package top.mcxiafeng.badger.network.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.mcxiafeng.badger.utils.KtorHttpCore
import kotlin.uuid.Uuid

class CollectionApi {

    companion object {
        val httpCore = KtorHttpCore()

        suspend fun getCollections(): List<Collection>? {
            when (val result = httpCore.get(PublicApi.serverUrl + "/api/user/collections", headers = PublicApi.authHeaders())) {
                is HttpResult.Success -> {
                    val root = Json.parseToJsonElement(result.body).jsonObject
                    val data = root["data"]?.jsonArray
                    BadgerLog.d("CollectionApi", "getCollections success: ${data != null}")
                    if (data != null) {
                        return Json.decodeFromJsonElement<List<Collection>>(data)
                    } else {
                        throw IllegalStateException("响应中没有 data 字段或不是数组")
                    }
                }
                is HttpResult.Failure -> {
                    BadgerLog.w("CollectionApi", "getCollections failed: ${result.errorType}")
                }
            }
            return null;
        }

        suspend fun createCollection(collection: Collection): HttpResult {
            return httpCore.post(PublicApi.serverUrl + "/api/user/collections", headers = PublicApi.authHeaders(), body = Json.encodeToString(collection));
        }

        suspend fun updateCollection(collection: Collection): HttpResult {
            return httpCore.put(url = PublicApi.serverUrl + "/api/user/collections/${collection.uuid}", body = Json.encodeToString(collection), headers = PublicApi.authHeaders());
        }

        suspend fun deleteCollection(collectionId: Uuid): HttpResult {
            return httpCore.delete(url = PublicApi.serverUrl + "/api/user/collections/$collectionId", headers = PublicApi.authHeaders());
        }

        suspend fun addCollectionMember(collectionId: Uuid, personUuid: Uuid): HttpResult {
            return httpCore.post(PublicApi.serverUrl + "/api/user/collections/$collectionId/members", headers = PublicApi.authHeaders(), body = Json.encodeToString(mapOf("personUuid" to personUuid)));
        }

        suspend fun removeCollectionMember(collectionId: Uuid, personUuid: Uuid): HttpResult {
            return httpCore.delete(url = PublicApi.serverUrl + "/api/user/collections/$collectionId/members/$personUuid", headers = PublicApi.authHeaders());
        }
    }


}
