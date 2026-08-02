package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * [§15 #19] Contact CRUD endpoints (V2 §5.1 protocol).
 *
 * Owns createContact / getContact / patchContact / deleteContact / mergeContact /
 * listContacts. Data classes for the request / response bodies live at the
 * top level of the [top.mcxiafeng.badger.network] package so call sites don't
 * have to qualify with `ContactApi.X`.
 */
class ContactApi(private val core: ApiCore) {

    /** POST /v1/contacts  {contact}  创建联系人。 */
    fun createContact(payload: JsonObject, ifMatch: Long? = null): ContactResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] createContact: ifMatch=$ifMatch bytes=${payload.toString().length}")
        return core.execute(core.buildRequestWithIfMatch("POST", "/v1/contacts", ifMatch, payload.toString()).build())
            .useNot2xxOrOk("contacts.create", tag) { resp ->
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val r = ContactResponse.from(obj)
                Log.d(TAG, "[$tag] createContact OK: code=${resp.code} version=${r.version}")
                r
            }
    }

    /** GET /v1/contacts/{id} 单条详情（id 是服务端 id string）。 */
    fun getContact(serverId: String): ContactResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] getContact: id=$serverId")
        return core.execute(core.buildRequest("GET", "/v1/contacts/$serverId").build())
            .useNot2xxOrOk("contacts.get", tag) { resp ->
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                ContactResponse.from(obj)
            }
    }

    /**
     * PATCH /v1/contacts/{id} 任意字段子集修改。
     *
     * [ifMatch] 是乐观锁的客户端期望版本号；服务端校验失败返回 409 + [ConflictResponse]。
     */
    fun patchContact(serverId: String, payload: JsonObject, ifMatch: Long?): ContactResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] patchContact: id=$serverId ifMatch=$ifMatch bytes=${payload.toString().length}")
        return core.execute(core.buildRequestWithIfMatch("PATCH", "/v1/contacts/$serverId", ifMatch, payload.toString()).build())
            .useNot2xxOrConflict("contacts.patch", tag) { resp ->
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val r = ContactResponse.from(obj)
                Log.d(TAG, "[$tag] patchContact OK: code=${resp.code} version=${r.version}")
                r
            }
    }

    /**
     * DELETE /v1/contacts/{id}
     *
     * 返回 2xx → 删除成功；404 → 服务端已删（幂等成功，详见 §5.5.2）。
     */
    fun deleteContact(serverId: String, ifMatch: Long?): Boolean {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] deleteContact: id=$serverId ifMatch=$ifMatch")
        return try {
            core.execute(core.buildRequestWithIfMatch("DELETE", "/v1/contacts/$serverId", ifMatch, null).build())
                .useNot2xxOrOk("contacts.delete", tag) { resp ->
                    Log.d(TAG, "[$tag] deleteContact OK: code=${resp.code}")
                    true
                }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w(TAG, "[$tag] deleteContact 404: server already removed, treating as idempotent success")
                true
            } else throw e
        }
    }

    /** POST /v1/contacts/{id}/merge 合并联系人（详见 S7）。 */
    fun mergeContact(targetServerId: String, mergedIds: List<String>, ifMatch: Long?): ContactResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] mergeContact: target=$targetServerId merged=${mergedIds.size} ifMatch=$ifMatch")
        val payload = JsonObject().apply {
            addProperty("target_id", targetServerId)
            val arr = JsonArray()
            mergedIds.forEach { arr.add(it) }
            add("merged_ids", arr)
        }
        return core.execute(core.buildRequestWithIfMatch("POST", "/v1/contacts/$targetServerId/merge", ifMatch, payload.toString()).build())
            .useNot2xxOrConflict("contacts.merge", tag) { resp ->
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                ContactResponse.from(obj)
            }
    }

    fun listContacts(since: Long? = null, limit: Int = 50): ContactPage {
        val tag = core.nextCallTag()
        val path = "/v1/contacts?limit=$limit" + (since?.let { "&since=$it" } ?: "")
        Log.d(TAG, "[$tag] listContacts: since=$since limit=$limit")
        return core.execute(core.buildRequest("GET", path).build())
            .useNot2xxOrOk("contacts.list", tag) { resp ->
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val items = obj.getAsJsonArray("items")?.mapNotNull {
                    runCatching { ContactResponse.from(it.asJsonObject) }.getOrNull()
                } ?: emptyList()
                val nextSince = obj.get("next_since")?.asLong ?: since ?: 0L
                Log.d(TAG, "[$tag] listContacts OK: items=${items.size} nextSince=$nextSince")
                ContactPage(items, nextSince)
            }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
