package top.mcxiafeng.badger.network

import android.util.Log

/**
 * [Phase 4] Backup endpoints（新 Java `/api` 契约，`Badger-Server/docs/api-handover.md` §4.9）。
 *
 * 与旧 Go `/v1/backups` 差异：
 * - 路径 `/api/backups` → `/api/user/backups`；
 * - list/upload 响应走 ApiResult 壳（`data`），字段仍为 snake_case（`id/name/size/created_at`）；
 * - download 返回**原始字节**（`application/octet-stream`，**不走**壳，保持 [ensureOk]）；
 * - delete 幂等语义保留：404 视为已删（成功），撞他人 403 原样抛出；
 * - 服务端体积上限 ≤6MiB（客户端 CloudSyncSettingsViewModel 自检 4MiB，不在此重复）。
 */
class BackupApi(private val core: ApiCore) {

    /** GET /api/user/backups — 我的备份列表（新→旧）。`data: { backups: [...] }`。 */
    fun listBackups(): List<BackupSummary> {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] listBackups")
        return core.execute(core.buildRequest("GET", "/api/user/backups").build())
            .unwrapApiResult("backups.list", tag) { data ->
                val obj = data.takeIf { it.isJsonObject }?.asJsonObject
                val arr = obj?.get("backups")?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
                if (arr == null) {
                    Log.w(TAG, "[$tag] listBackups: missing data.backups, got ${data.javaClass.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull {
                    val o = it.asJsonObject
                    runCatching {
                        BackupSummary(
                            id = o.get("id").asString,
                            name = o.get("name").asString,
                            size = o.get("size").asLong,
                            createdAt = o.get("created_at").asString,
                        )
                    }.getOrNull()
                }
            }
    }

    /** POST /api/user/backups — 上传备份信封。返回 `data: {id, name, size, created_at}`。 */
    fun uploadBackup(envelopeJson: String): BackupUpload {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] uploadBackup: bytes=${envelopeJson.length}")
        return core.execute(core.buildRequest("POST", "/api/user/backups", envelopeJson).build())
            .unwrapApiResult("backups.upload", tag) { data ->
                if (!data.isJsonObject) {
                    throw ApiException(0, data.toString().take(200), "backups.upload data not object")
                }
                val o = data.asJsonObject
                BackupUpload(
                    id = o.get("id").asString,
                    name = o.get("name").asString,
                    size = o.get("size").asLong,
                    createdAt = o.get("created_at").asString,
                )
            }
    }

    /**
     * GET /api/user/backups/{uuid} — 下载备份，返回**原始字节**（`application/octet-stream`，
     * 不走 ApiResult 壳）。DB 有行但文件丢失 → 404 [ApiException]。
     */
    fun downloadBackup(id: String): ByteArray {
        core.execute(core.buildRequest("GET", "/api/user/backups/$id").build()).use { resp ->
            core.ensureOk(resp, "backups.download")
            return resp.body!!.bytes()
        }
    }

    /**
     * DELETE /api/user/backups/{uuid}
     *
     * 幂等：404（行不存在）视为已删成功；撞他人 403 原样抛出。
     */
    fun deleteBackup(id: String): Boolean {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] deleteBackup: id=$id")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/user/backups/$id").build())
                .unwrapApiResult("backups.delete", tag) { /* data: null */ true }
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
