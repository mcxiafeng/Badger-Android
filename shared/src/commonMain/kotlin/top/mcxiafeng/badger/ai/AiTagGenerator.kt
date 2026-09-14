package top.mcxiafeng.badger.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity as Tag
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

class AiTagGenerator(
    private val serverApiFactory: ServerApiFactory,
) {

    private companion object {
        const val TAG = "AiTagGenerator"

        val NEW_TAG_PALETTE = longArrayOf(
            0xFF1976D2L, 
            0xFF388E3CL, 
            0xFFE64A19L, 
            0xFF7B1FA2L, 
            0xFFF57C00L, 
        )

        fun colorForConfidence(confidence: Float): Long {
            val idx = (confidence * (NEW_TAG_PALETTE.size - 1)).toInt()
                .coerceIn(0, NEW_TAG_PALETTE.lastIndex)
            return NEW_TAG_PALETTE[idx]
        }
    }

    
    data class TagCandidate(
        val name: String,
        val color: Long,
        val matchedExisting: Boolean,
        val existingTagId: Long? = null,
        val confidence: Float = 0.5f,
    )

    

    suspend fun suggest(bio: String, existingTags: List<Tag>): List<TagCandidate> = withContext(BadgerDispatchers.io) {
        require(bio.isNotBlank()) { "bio must not be blank" }
        val api = serverApiFactory.get()
        val parsed = try {
            api.tagGenerate(bio, existingTags.map { it.name })
        } catch (e: Throwable) {
            BadgerLog.w(TAG, "suggest: server unreachable: ${e.message}")
            throw AiTagException("AI 服务暂时不可用: ${e.message ?: "unknown"}")
        }
        if (parsed.isEmpty()) {
            BadgerLog.w(TAG, "suggest: server returned empty list")
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

class AiTagException(message: String) : RuntimeException(message)
