package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.BadgerLog

class V2DomainApi(private val core: ApiCore) {

    

    
    fun patchProfile(name: String?, profile: ProfileDto?) {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            profile?.let { put("profile", it.toJsonObject()) }
        }
        BadgerLog.d(TAG, "[$tag] patchProfile: bytes=${payload.toString().length}")
        core.execute(core.request("PUT", "/api/user/profile", payload.toString()))
            .unwrapApiResult("profile.patch", tag) {  }
    }

    
    fun getProfile(): UserProfileResponse {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] getProfile")
        return core.execute(core.request("GET", "/api/user/profile"))
            .unwrapApiResult("profile.get", tag) { data ->
                val obj = data as? JsonObject
                if (obj == null) {
                    BadgerLog.w(TAG, "[$tag] getProfile: expected object, got ${data::class.simpleName}")
                    return@unwrapApiResult UserProfileResponse(null, null, null)
                }
                UserProfileResponse.from(obj)
            }
    }

    

    
    fun listTags(): List<TagDto> {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] listTags")
        return core.execute(core.request("GET", "/api/user/tags"))
            .unwrapApiResult("tags.list", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    BadgerLog.w(TAG, "[$tag] listTags: expected array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el -> runCatching { TagDto.from(el as JsonObject) }.getOrNull() }
            }
    }

    

    fun createTag(name: String, colorHash: String?, personMembers: List<String>?, uuid: String? = null): String {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            put("name", name)
            colorHash?.takeIf { it.isNotBlank() }?.let { put("colorHash", it) }
            personMembers?.takeIf { it.isNotEmpty() }?.let { put("personMembers", toStrArr(it)) }
            uuid?.let { put("uuid", it) }
        }
        BadgerLog.d(TAG, "[$tag] createTag: name=$name members=${personMembers?.size ?: 0} uuid=${uuid?.take(8)}")
        return core.execute(core.request("POST", "/api/user/tags", payload.toString()))
            .unwrapApiResult("tags.create", tag) { data ->
                uuidFromData(data, tag, "tags.create")
            }
    }

    
    fun patchTag(uuid: String, name: String?, colorHash: String?) {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            colorHash?.let { put("colorHash", it) }
        }
        BadgerLog.d(TAG, "[$tag] patchTag: uuid=${uuid.take(8)}")
        core.execute(core.request("PUT", "/api/user/tags/$uuid", payload.toString()))
            .unwrapApiResult("tags.patch", tag) {  }
    }

    
    fun deleteTag(uuid: String): Boolean {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] deleteTag: uuid=${uuid.take(8)}")
        return try {
            core.execute(core.request("DELETE", "/api/user/tags/$uuid"))
                .unwrapApiResult("tags.delete", tag) { true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                BadgerLog.w(TAG, "[$tag] deleteTag 404: server already removed, idempotent success")
                true
            } else throw e
        }
    }

    
    fun addTagMember(uuid: String, personUuid: String) {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] addTagMember: tag=${uuid.take(8)} person=${personUuid.take(8)}")
        core.execute(core.request("POST", "/api/user/tags/$uuid/members/$personUuid"))
            .unwrapApiResult("tags.member.add", tag) {  }
    }

    
    fun removeTagMember(uuid: String, personUuid: String) {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] removeTagMember: tag=${uuid.take(8)} person=${personUuid.take(8)}")
        core.execute(core.request("DELETE", "/api/user/tags/$uuid/members/$personUuid"))
            .unwrapApiResult("tags.member.remove", tag) {  }
    }

    

    
    fun listCollections(): List<CollectionDto> {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] listCollections")
        return core.execute(core.request("GET", "/api/user/collections"))
            .unwrapApiResult("collections.list", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    BadgerLog.w(TAG, "[$tag] listCollections: expected array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el -> runCatching { CollectionDto.from(el as JsonObject) }.getOrNull() }
            }
    }

    

    fun createCollection(
        name: String,
        description: String?,
        backgroundURL: String?,
        personMembers: List<String>?,
        uuid: String? = null,
    ): String {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            put("name", name)
            description?.let { put("description", it) }
            backgroundURL?.let { put("backgroundURL", it) }
            personMembers?.takeIf { it.isNotEmpty() }?.let { put("personMembers", toStrArr(it)) }
            uuid?.let { put("uuid", it) }
        }
        BadgerLog.d(TAG, "[$tag] createCollection: name=$name members=${personMembers?.size ?: 0} uuid=${uuid?.take(8)}")
        return core.execute(core.request("POST", "/api/user/collections", payload.toString()))
            .unwrapApiResult("collections.create", tag) { data ->
                uuidFromData(data, tag, "collections.create")
            }
    }

    
    fun patchCollection(uuid: String, name: String?, description: String?, backgroundURL: String?) {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            description?.let { put("description", it) }
            backgroundURL?.let { put("backgroundURL", it) }
        }
        BadgerLog.d(TAG, "[$tag] patchCollection: uuid=${uuid.take(8)}")
        core.execute(core.request("PUT", "/api/user/collections/$uuid", payload.toString()))
            .unwrapApiResult("collections.patch", tag) {  }
    }

    
    fun deleteCollection(uuid: String): Boolean {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] deleteCollection: uuid=${uuid.take(8)}")
        return try {
            core.execute(core.request("DELETE", "/api/user/collections/$uuid"))
                .unwrapApiResult("collections.delete", tag) { true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                BadgerLog.w(TAG, "[$tag] deleteCollection 404: server already removed, idempotent success")
                true
            } else throw e
        }
    }

    
    fun addCollectionMember(uuid: String, personUuid: String) {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] addCollectionMember: col=${uuid.take(8)} person=${personUuid.take(8)}")
        core.execute(core.request("POST", "/api/user/collections/$uuid/members/$personUuid"))
            .unwrapApiResult("collections.member.add", tag) {  }
    }

    
    fun removeCollectionMember(uuid: String, personUuid: String) {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] removeCollectionMember: col=${uuid.take(8)} person=${personUuid.take(8)}")
        core.execute(core.request("DELETE", "/api/user/collections/$uuid/members/$personUuid"))
            .unwrapApiResult("collections.member.remove", tag) {  }
    }

    

    private fun toStrArr(list: List<String>): JsonArray = JsonArray(list.map { JsonPrimitive(it) })

    private fun uuidFromData(data: JsonElement, tag: String, what: String): String {
        val uuid = if (data is JsonObject) {
            stringOrNull(data, "uuid").orEmpty()
        } else {
            BadgerLog.w(ApiCore.TAG, "[$tag] $what: data not object, uuid empty")
            ""
        }
        if (uuid.isBlank()) throw ApiException(0, "$what missing uuid", what)
        return uuid
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
