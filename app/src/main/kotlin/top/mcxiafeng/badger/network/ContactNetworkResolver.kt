package top.mcxiafeng.badger.network

import android.util.Log
import org.koin.core.context.GlobalContext
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.utils.SafeLog

/**
 * Server-authoritative identification of an arbitrary user-supplied
 * input string (URL, raw QQ number, vCard snippet, gibberish, ...).
 *
 * Shape mirrors `POST /v1/resolver/identify`:
 *   { kind, name, avatar_url, signature, contact_map }
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
     * Variant that takes an explicit [ServerApi] — used by tests without Koin setup.
     */
    internal fun identifyWith(api: ServerApi, input: String): IdentifyResponse? {
        val obj = try {
            api.resolveIdentify(input)
        } catch (e: Throwable) {
            Log.w(TAG, "identify failed for ${SafeLog.unknown(input)}: ${e.javaClass.simpleName}: ${e.message}", e)
            return null
        } ?: return null
        val kind = obj.get("kind")?.takeIf { !it.isJsonNull }?.asString ?: "unknown"
        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
        val sig = obj.get("signature")?.takeIf { !it.isJsonNull }?.asString
        val avatar = obj.get("avatar_url")?.takeIf { !it.isJsonNull }?.asString
        val map = obj.getAsJsonObject("contact_map")
            ?.entrySet()
            ?.filter { !it.value.isJsonNull }
            ?.associate { it.key to it.value.asString }
            ?: emptyMap()
        Log.d(TAG, "identify: input=${SafeLog.url(input)} kind=$kind map=${map.keys}")
        return IdentifyResponse(kind = kind, name = name, avatarUrl = avatar, signature = sig, contactMap = map)
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

    fun toContactAndInfo(
        @Suppress("UNUSED_PARAMETER") result: NetworkResolveResult?,
        @Suppress("UNUSED_PARAMETER") rawContent: String,
    ): Pair<Any, Any>? = null

}
