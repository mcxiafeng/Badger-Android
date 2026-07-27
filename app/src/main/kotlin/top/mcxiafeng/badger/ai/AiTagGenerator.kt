package top.mcxiafeng.badger.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ServerApi

/**
 * Recommends 1-5 tags for a contact based on its self-introduction.
 *
 * The actual LLM call lives on the Badger-Server (`/v1/proxy/ai/tasks/tag_generate`);
 * the API key never reaches the device. This class is a thin wrapper that
 * matches the existing UI's `TagCandidate` shape — `color`,
 * `matchedExisting`, `existingTagId` — so all call sites compile unchanged.
 *
 * The server only returns tag names + confidence; we map names onto the
 * user's existing tag list locally (cheap matching) to decide whether each
 * candidate reuses an existing tag id or needs a freshly-coloured one.
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::AiTagGenerator)`。
 * Koin 不需要构造注入注解;`@Singleton` 语义在 `singleOf` 里等同。
 */
class AiTagGenerator(
    private val serverApiFactory: ServerApiFactory,
) {

    private companion object {
        const val TAG = "AiTagGenerator"

        val NEW_TAG_PALETTE = longArrayOf(
            0xFF1976D2L, // Material Blue
            0xFF388E3CL, // Material Green
            0xFFE64A19L, // Material Deep Orange
            0xFF7B1FA2L, // Material Purple
            0xFFF57C00L, // Material Orange
        )

        fun colorForConfidence(confidence: Float): Long {
            val idx = (confidence * (NEW_TAG_PALETTE.size - 1)).toInt()
                .coerceIn(0, NEW_TAG_PALETTE.lastIndex)
            return NEW_TAG_PALETTE[idx]
        }
    }

    /** Mirrors the Kotlin data class downstream code expects. */
    data class TagCandidate(
        val name: String,
        val color: Long,
        val matchedExisting: Boolean,
        val existingTagId: Long? = null,
        val confidence: Float = 0.5f,
    )

    /**
     * Invoke the server's tag-suggest endpoint. Throws [AiTagException]
     * on transport errors or upstream non-2xx so callers can fall back to
     * [fallbackLocal].
     */
    suspend fun suggest(bio: String, existingTags: List<Tag>): List<TagCandidate> = withContext(Dispatchers.IO) {
        require(bio.isNotBlank()) { "bio must not be blank" }
        val api = serverApiFactory.get()
        val parsed = try {
            api.tagGenerate(bio, existingTags.map { it.name })
        } catch (e: Throwable) {
            Log.w(TAG, "suggest: server unreachable: ${e.message}")
            throw AiTagException("AI 服务暂时不可用: ${e.message ?: "unknown"}")
        }
        if (parsed.isEmpty()) {
            Log.w(TAG, "suggest: server returned empty list")
            throw AiTagException("AI 未返回有效标签")
        }
        val existingByName = existingTags.associateBy { it.name }
        parsed.map { c ->
            val match = existingByName[c.name]
            TagCandidate(
                name = c.name,
                color = match?.color ?: colorForConfidence(c.confidence),
                matchedExisting = match != null,
                existingTagId = match?.id,
                confidence = c.confidence,
            )
        }
    }

    /**
     * Local substring fallback — used when the server is unreachable. Same
     * algorithm as before: return every existing tag whose name appears
     * as a substring of the bio.
     */
    fun fallbackLocal(bio: String, existingTags: List<Tag>): List<TagCandidate> {
        if (existingTags.isEmpty() || bio.isBlank()) return emptyList()
        val bioLower = bio.lowercase()
        return existingTags
            .filter { bioLower.contains(it.name.lowercase()) }
            .map {
                TagCandidate(
                    name = it.name,
                    color = it.color,
                    matchedExisting = true,
                    existingTagId = it.id,
                    confidence = 0.7f,
                )
            }
    }
}

/**
 * AI tag generation failure; callers fall back to [AiTagGenerator.fallbackLocal].
 */
class AiTagException(message: String) : RuntimeException(message)
