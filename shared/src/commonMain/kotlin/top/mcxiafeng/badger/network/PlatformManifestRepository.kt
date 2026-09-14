package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ocr.PLATFORM_FIELDS
import top.mcxiafeng.badger.ocr.PlatformFieldDef
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

data class ServerPlatform(
    val fieldKey: String,
    val displayName: String,
    val custom: Boolean,
    val hasDetect: Boolean,
    val enabled: Boolean,
) {
    companion object {
        
        fun parse(obj: JsonObject?): ServerPlatform? {
            if (obj == null) return null
            
            
            return try {
                val key = (obj["name"] as? JsonPrimitive)?.content
                    ?.takeIf { it.isNotBlank() } ?: return null
                val display = (obj["displayName"] as? JsonPrimitive)?.content.orEmpty()
                val enabled = boolOr(obj["enabled"], true)
                val custom = boolOr(obj["custom"], false)
                val hasDetect = boolOr(obj["hasDetect"], false)
                ServerPlatform(fieldKey = key, displayName = display, custom = custom, hasDetect = hasDetect, enabled = enabled)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "platforms parse skip: ${e::class.simpleName}: ${e.message}")
                null
            }
        }

        private const val TAG = "ServerApi"
    }
}

fun mergeServerPlatforms(server: List<ServerPlatform>?): List<PlatformFieldDef> {
    if (server.isNullOrEmpty()) return PLATFORM_FIELDS
    val enabled = server.filter { it.enabled }
    val enabledKeys = enabled.map { it.fieldKey }.toSet()
    val localPart = PLATFORM_FIELDS.filter { it.fieldKey in enabledKeys }
    val localKeys = PLATFORM_FIELDS.map { it.fieldKey }.toSet()
    val remotePart = enabled.mapNotNull { sp ->
        if (sp.fieldKey in localKeys) {
            null
        } else {
            
            PlatformFieldDef(
                fieldKey = sp.fieldKey,
                displayName = sp.displayName.ifBlank { sp.fieldKey },
                contactType = ContactType.None,
                iconName = "ic_website",
            )
        }
    }
    return localPart + remotePart
}

fun serverDetectableKinds(server: List<ServerPlatform>?): Set<String> =
    server.orEmpty()
        .filter { it.enabled && it.hasDetect }
        .map { it.fieldKey.lowercase() }
        .toSet()

fun String.canSyncViaManifest(): Boolean =
    KoinComponentBy.get<PlatformManifestRepository>().canSync(this)

class PlatformManifestRepository(private val serverApi: ServerApi) {

    private val _addable = MutableStateFlow<List<PlatformFieldDef>>(PLATFORM_FIELDS)
    val addable: StateFlow<List<PlatformFieldDef>> = _addable.asStateFlow()

    
    private val _detectableKinds = MutableStateFlow(SYNCABLE_KINDS)
    val detectableKinds: StateFlow<Set<String>> = _detectableKinds.asStateFlow()

    

    fun canSync(kind: String): Boolean = kind.lowercase() in _detectableKinds.value

    @kotlin.concurrent.Volatile
    private var lastFetchMs = 0L

    

    suspend fun ensureLoaded() {
        val now = nowMs()
        if (now - lastFetchMs < TTL_MS) return
        lastFetchMs = now
        refresh()
    }

    
    suspend fun refresh() {
        
        
        val raw = try {
            withContext(BadgerDispatchers.io) { serverApi.platforms() }
        } catch (e: Throwable) {
            BadgerLog.w(TAG, "platforms fetch failed: ${e::class.simpleName}: ${e.message}")
            null
        }
        if (raw.isNullOrEmpty()) {
            if (raw == null) BadgerLog.w(TAG, "platforms fetch empty, keep local fallback")
            return
        }
        val parsed = raw.mapNotNull { ServerPlatform.parse(it) }
        val merged = mergeServerPlatforms(parsed)
        if (merged.isNotEmpty()) _addable.value = merged
        val detectable = serverDetectableKinds(parsed)
        if (detectable.isNotEmpty()) _detectableKinds.value = detectable
    }

    private companion object {
        const val TAG = "PlatformManifest"
        const val TTL_MS = 30_000L
    }
}
