package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonObject
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.utils.SafeLog

/**
 * Server-authoritative identification of an arbitrary user-supplied
 * input string (URL, raw QQ number, vCard snippet, gibberish, ...).
 *
 * Shape mirrors `POST /api/resolve/` (`Badger-Server/docs/api-handover.md` §5.1):
 *   { platform, input, status, name, avatarUrl, description, contacts, ... }
 * The parser reads camelCase fields with legacy-name fallbacks.
 *
 * `kind` is the server's classification (string). Use
 * [kindToContactType] to project it onto a UI [ContactType] when
 * tagging chips, or [SYNCABLE_KINDS]/[kindCanSync] to decide
 * whether the server can re-resolve profile data on demand.
 *
 * `contactMap` is the server's authoritative key → id mapping. The
 * previous client-side flows read `contactMap["qqGroup"]` vs
 * `contactMap["qq"]` based on heuristic URL inspection; that
 * responsibility now lives entirely with the server.
 */
data class IdentifyResponse(
    val kind: String,
    val name: String?,
    val avatarUrl: String?,
    val signature: String?,
    val contactMap: Map<String, String>,
)

/**
 * Backwards-compatible wrapper that mirrors the old
 * `getResultInfo(...)` shape used by several Compose call sites.
 * Internally delegates to [identify] — the client no longer parses
 * URLs.
 */
data class NetworkResolveResult(
    val nickname: String?,
    val description: String?,
    val avatarUrl: String?,
    val contactMap: Map<String, String>,
    val type: ContactType,
)

/**
 * Single entry point for any "what is this input?" question.
 *
 * Replaces the legacy `getResultInfo(content, contactMap, type)`
 * which used to fan out to five per-platform extractor regexes on
 * the client. The client no longer parses URLs — see [identify].
 */
object ContactNetworkResolver {

    private const val TAG = "ContactNetworkResolver"

    /**
     * [修复防御]: 必须走 [ServerApiFactory.get()],而不是自己 `new ServerApi()`。
     *
     * 与全 app 复用同一份 OkHttp + TokenHolder —— 改 baseUrl 立即生效;access token
     * 失效时由 NetworkModule.tokenRefreshInterceptor 自动 refresh + 重试一次。
     */
    private fun api(): ServerApi =
        GlobalContext.get().get<ServerApiFactory>().get()

    /**
     * Authoritative identification delegated entirely to the server.
     *
     * Returns null on network error or empty input; never throws.
     * The `kind` field is the server's verdict — pass it to
     * [kindToContactType] for a [ContactType] chip, or to
     * [kindCanSync] for the per-platform re-resolve decision.
     */
    fun identify(input: String): IdentifyResponse? {
        if (input.isBlank()) return null
        val a = try {
            api()
        } catch (e: Throwable) {
            Log.w(TAG, "api() failed (Hilt/EntryPoint 未就绪?): ${e.javaClass.simpleName}: ${e.message}", e)
            return null
        }
        return identifyWith(a, input)
    }

    /**
     * Batch variant: a single POST `/api/resolve/` with the entire `items` array.
     *
     * Returns a list parallel to [inputs] — same length, each entry null when
     * that URL failed (network error / server returned blank / Koin api()
     * unavailable). Caller filters nulls downstream.
     *
     * [修复防御]: 历史的 [identify] 在多码扫描下走 N 次独立 POST,服务端
     * `RouteScanner` 日志能看到 "POST /api/resolve/" 拉一条一行。该实现改用
     * 一次请求装 N 条 item,服务端日志变成一行,客户端少 N-1 次 TLS 握手 + dispatcher
     * 排队,UI 列表解析也变成单次等待。
     */
    fun identifyBatch(inputs: List<String>): List<IdentifyResponse?> {
        if (inputs.isEmpty()) return emptyList()
        val a = try {
            api()
        } catch (e: Throwable) {
            Log.w(TAG, "identifyBatch: api() failed (Hilt/EntryPoint 未就绪?): ${e.javaClass.simpleName}: ${e.message}", e)
            return List(inputs.size) { null }
        }
        // [修复防御]: 把所有空串筛掉,与批内位置保留一个映射关系以便回填到 inputs 同索引位置。
        // 这条契约单测里被显式断言（identifyBatchWith 空串位置必为 null）。
        val indexed = inputs.withIndex().filter { it.value.isNotBlank() }
        if (indexed.isEmpty()) return List(inputs.size) { null }
        val cleanList = indexed.map { it.value }
        val raws = try {
            a.resolveIdentifyBatch(cleanList)
        } catch (e: Throwable) {
            Log.w(TAG, "identifyBatch size=${cleanList.size} failed: ${e.javaClass.simpleName}: ${e.message}", e)
            List(cleanList.size) { null }
        }
        Log.d(TAG, "identifyBatch: requested=${cleanList.size} got=${raws.count { it != null }}")
        val out = arrayOfNulls<IdentifyResponse?>(inputs.size)
        indexed.forEachIndexed { i, (origIdx, _) ->
            out[origIdx] = parseOne(raws.getOrNull(i))
        }
        return out.toList()
    }

    /**
     * Single-shot result parser used by both [identify] and [identifyBatch].
     * Kept package-private to allow [identifyWith] / [getResultInfoInternal]
     * to share the projection logic.
     *
     * [Phase 4] 字段重映射依据 `Badger-Server/docs/api-handover.md` §5.1
     * （ResolveResult 序列化字段表，2026-08-26 实测）：新契约字段一律 camelCase，
     * `signature` 已改名 `description`、`contact_map` 改名 `contacts`（仍是 JSON 对象）。
     */
    private fun parseOne(obj: JsonObject?): IdentifyResponse? {
        if (obj == null) return null
        // [修复防御]: 新 Java `/api` 契约字段 camelCase（avatarUrl/description/contacts），
        // 兼容两手读:优先新名,找不到再退到旧 Go 契约名（avatar_url/signature/contact_map）。
        val kind = obj.get("platform")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("kind")?.takeIf { !it.isJsonNull }?.asString
            ?: "unknown"
        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
        val sig = obj.get("description")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("signature")?.takeIf { !it.isJsonNull }?.asString
        val avatar = obj.get("avatarUrl")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("avatar_url")?.takeIf { !it.isJsonNull }?.asString
        val contactsElem = obj.get("contacts")?.takeIf { !it.isJsonNull }
            ?: obj.get("contact_map")?.takeIf { !it.isJsonNull }
        val map = contactsElem?.asJsonObject
            ?.entrySet()
            ?.filter { !it.value.isJsonNull }
            ?.associate { it.key to it.value.asString }
            ?: emptyMap()
        // [修复防御]: 新契约带 status(ok/partial/fallback/error)。error 时平台识别失败
        //（platform=null → kind 兜底 "unknown"）。记录日志做可观测,不吞根因。
        val status = obj.get("status")?.takeIf { !it.isJsonNull }?.asString
        if (status == "error") {
            val err = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
            Log.w(TAG, "parseOne: status=error kind=${kind.ifBlank { "unknown" }} error=$err")
        }
        return IdentifyResponse(kind = kind, name = name, avatarUrl = avatar, signature = sig, contactMap = map)
    }

    /**
     * Variant that takes an explicit [ServerApi] — used by tests without Koin setup.
     */
    internal fun identifyWith(api: ServerApi, input: String): IdentifyResponse? {
        val obj = try {
            api.resolveIdentify(input)
        } catch (e: Throwable) {
            Log.w(TAG, "identify failed for ${SafeLog.unknown(input)}: ${e.javaClass.simpleName}: ${e.message}", e)
            return null
        } ?: return null
        // 兼容新老两套字段命名(参见 parseOne 顶部注释)。
        return parseOne(obj)
    }

    /**
     * Test-friendly batch variant taking an explicit [ServerApi].
     * Returns nulls for inputs that returned no JSON, and **preserves input order**
     * including the position of blank strings (blank → null, never shifted).
     */
    internal fun identifyBatchWith(api: ServerApi, inputs: List<String>): List<IdentifyResponse?> {
        if (inputs.isEmpty()) return emptyList()
        val indexed = inputs.withIndex().filter { it.value.isNotBlank() }
        if (indexed.isEmpty()) return List(inputs.size) { null }
        val cleanList = indexed.map { it.value }
        val raws = try {
            api.resolveIdentifyBatch(cleanList)
        } catch (e: Throwable) {
            Log.w(TAG, "identifyBatchWith failed size=${cleanList.size}: ${e.javaClass.simpleName}: ${e.message}", e)
            return List(inputs.size) { null }
        }
        val out = arrayOfNulls<IdentifyResponse?>(inputs.size)
        indexed.forEachIndexed { i, (origIdx, _) ->
            out[origIdx] = parseOne(raws.getOrNull(i))
        }
        return out.toList()
    }

    /**
     * Compatibility shim. Kept because six existing call sites
     * (`App.kt`, `SetupStepPlatforms.kt`, `ContactDetailPage.kt`,
     * `UserProfileDetailPage.kt`, `ContactDetailViewModel.kt`, plus
     * scanner dialogs) already use this signature. Internally it just
     * calls [identify] and re-projects onto [NetworkResolveResult].
     * No client-side URL parsing happens here.
     *
     * @param type optional hint for the caller-known platform kind —
     *             used as a fallback only when [identify] returns
     *             `kind="unknown"`. Prefer to read the new
     *             [IdentifyResponse] directly in new code paths.
     */
    fun getResultInfo(
        content: String,
        @Suppress("UNUSED_PARAMETER") contactMap: Map<String, String>,
        type: ContactType? = null,
    ): NetworkResolveResult? {
        val identified = identify(content) ?: return null
        val detected = type ?: kindToContactType(identified.kind) ?: ContactType.None
        return NetworkResolveResult(
            nickname = identified.name,
            description = identified.signature,
            avatarUrl = identified.avatarUrl,
            contactMap = identified.contactMap,
            type = detected,
        )
    }

    /**
     * Variant of [getResultInfo] that takes an explicit [ServerApi] —
     * same purpose as [identifyWith], for tests that don't have a
     * Hilt-resolved EntryPoint.
     */
    internal fun getResultInfoInternal(
        api: ServerApi,
        content: String,
        type: ContactType? = null,
    ): NetworkResolveResult? {
        val identified = identifyWith(api, content) ?: return null
        val detected = type ?: kindToContactType(identified.kind) ?: ContactType.None
        return NetworkResolveResult(
            nickname = identified.name,
            description = identified.signature,
            avatarUrl = identified.avatarUrl,
            contactMap = identified.contactMap,
            type = detected,
        )
    }
}
