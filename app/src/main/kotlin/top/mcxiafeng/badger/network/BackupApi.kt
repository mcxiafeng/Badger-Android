package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonParser

/**
 * [§15 #19] Backup endpoints (v1 WebDAV-era replaced by server-side envelope store).
 */
class BackupApi(private val core: ApiCore) {

    /** GET /v1/backups */
    fun listBackups(): List<BackupSummary> {
        core.execute(core.buildRequest("GET", "/v1/backups").build()).use { resp ->
            core.ensureOk(resp, "backups.list")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            val arr = obj.getAsJsonArray("backups") ?: return emptyList()
            return arr.mapNotNull {
                val o = it.asJsonObject
                BackupSummary(
                    id = o.get("id").asString,
                    name = o.get("name").asString,
                    size = o.get("size").asLong,
                    createdAt = o.get("created_at").asString,
                )
            }
        }
    }

    /** POST /v1/backups with a JSON envelope. */
    fun uploadBackup(envelopeJson: String): BackupUpload {
        core.execute(core.buildRequest("POST", "/v1/backups", envelopeJson).build()).use { resp ->
            core.ensureOk(resp, "backups.upload")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            return BackupUpload(
                id = obj.get("id").asString,
                name = obj.get("name").asString,
                size = obj.get("size").asLong,
                createdAt = obj.get("created_at").asString,
            )
        }
    }

    /** GET /v1/backups/{id} returns raw bytes. */
    fun downloadBackup(id: String): ByteArray {
        core.execute(core.buildRequest("GET", "/v1/backups/$id").build()).use { resp ->
            core.ensureOk(resp, "backups.download")
            return resp.body!!.bytes()
        }
    }

    /**
     * DELETE /v1/backups/{id}
     *
     * Returns true on 2xx and on 404 (idempotent: server already removed).
     */
    fun deleteBackup(id: String): Boolean {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] deleteBackup: id=$id")
        return try {
            core.execute(core.buildRequest("DELETE", "/v1/backups/$id").build())
                .useNot2xxOrOk("backups.delete", tag) { resp ->
                    Log.d(TAG, "[$tag] deleteBackup OK: code=${resp.code}")
                    true
                }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w(TAG, "[$tag] deleteBackup 404: server already removed, treating as idempotent success")
                true
            } else throw e
        }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
