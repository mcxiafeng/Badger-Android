package top.mcxiafeng.badger.network

import android.content.Context
import top.mcxiafeng.badger.data.AuthPrefs

/**
 * Compat shim around the old `LinkResolver.resolve(...)` API. Now that the
 * heavy lifting (short-link redirect + platform regex) lives server-side,
 * this stub returns the input URL as the only candidate. UI callers should
 * already route to `/v1/resolver/link` upstream when they need real work
 * done; the placeholder keeps the type alive until those UI calls are
 * themselves moved.
 */
object LinkResolver {

    data class LinkResolveResult(
        val jumpLink: String?,
        val originalLink: String?,
        val value: String?,
        val displayName: String?,
        val avatarUrl: String?,
        val errorMessage: String?,
    ) {
        companion object {
            fun toPlatformEntry(@Suppress("UNUSED_PARAMETER") r: LinkResolveResult, @Suppress("UNUSED_PARAMETER") displayNameOverride: String?): Any? = null
        }
    }

    suspend fun resolve(@Suppress("UNUSED_PARAMETER") fieldKey: String, rawInput: String): LinkResolveResult {
        return LinkResolveResult(
            jumpLink = null,
            originalLink = rawInput,
            value = rawInput,
            displayName = null,
            avatarUrl = null,
            errorMessage = null,
        )
    }

    fun toPlatformEntry(@Suppress("UNUSED_PARAMETER") result: LinkResolveResult, @Suppress("UNUSED_PARAMETER") displayNameOverride: String?): Any? = null
}

/** Stub replacement for the deleted PlatformIdExtractor. */
object PlatformIdExtractor {
    fun extractByKey(@Suppress("UNUSED_PARAMETER") key: String, link: String): Any? =
        link.takeIf { it.isNotBlank() }
    fun normalizeToKey(@Suppress("UNUSED_PARAMETER") name: String): String = name
    fun detectFieldKeyFromUrl(url: String): String? = null
}

/** Tiny helper so the compat layer compiles without unused warnings. */
@Suppress("unused")
private fun ctxOf(@Suppress("UNUSED_PARAMETER") c: Context): String = AuthPrefs.readServerUrl(c)
