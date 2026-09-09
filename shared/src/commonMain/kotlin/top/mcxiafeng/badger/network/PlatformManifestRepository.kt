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

/**
 * [Phase 4 剩余] 服务端平台清单（`GET /api/resolve/platforms`）→ UI 可渲染列表的桥。
 *
 * 归属权契约（2026-09-10 双端出入复盘）：**能力归服务端，呈现归客户端**。
 * 服务端清单决定「哪些平台可添加（enabled）、哪些可自动检测（hasDetect）、
 * 服务端独有/自定义平台有哪些」；已知平台的展示顺序与 displayName 由本地
 * [PLATFORM_FIELDS] 固定，保证在线/离线同一网格。本地清单同时是服务端
 * 不可达时的全量兜底。
 *
 * 契约字段（`Badger-Server` ResolverModule.platforms，ApiResult 壳 `data:[...]`，已过滤 enabled）：
 * `name`（fieldKey）/ `displayName` / `icon` / `custom` / `hasDetect` / `version` / `enabled`。
 */
data class ServerPlatform(
    val fieldKey: String,
    val displayName: String,
    val custom: Boolean,
    val hasDetect: Boolean,
    val enabled: Boolean,
) {
    companion object {
        /** 解析单条；缺 `name`（无法做 fieldKey 关联）→ null。布尔缺省 enabled=true（服务端已过滤）。 */
        fun parse(obj: JsonObject?): ServerPlatform? {
            if (obj == null) return null
            // [修复防御]: 整条 try/catch —— 服务端字段类型异常（如 enabled 传字符串）时跳过该条，
            // 不炸整批 manifest 解析（有日志，不吞根因）。
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

/**
 * 服务端清单 → UI 用 [PlatformFieldDef] 列表（纯函数，可单测）。
 *
 * 归属权契约（对照双端出入复盘 + SDUI 混合模式通则）：
 * - **能力归服务端**：哪些平台可添加（enabled）、能否自动检测（hasDetect）、
 *   服务端独有/自定义平台清单——全部以服务端注册表为准。
 * - **呈现归客户端**：已知平台的展示顺序与 displayName 由本地 [PLATFORM_FIELDS]
 *   固定（中文文案与网格序是客户端策划资产）——修复在线/离线两套顺序、
 *   在线覆盖出 "Bilibili"/"QQ 群" 等漂移。
 * - `null`/空 → 本地 [PLATFORM_FIELDS]（离线 / 未登录 / 拉取失败兜底）。
 * - 服务端独有平台：保服务端序追加在本地平台之后，`ContactType.None` + `ic_website` 兜底。
 */
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
            // 服务端独有/自定义平台：不用 FIELD_DEF_MAP 兜底（会误中系统字段同名的 def）
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

/** 服务端声明有自动检测能力的 kind 集合（已过滤 enabled；kind 小写归一）。空清单 → 空集。 */
fun serverDetectableKinds(server: List<ServerPlatform>?): Set<String> =
    server.orEmpty()
        .filter { it.enabled && it.hasDetect }
        .map { it.fieldKey.lowercase() }
        .toSet()

/**
 * 全局便捷判定（Koin 单例入口，同 [top.mcxiafeng.badger.network.ContactNetworkResolver] 惯例）：
 * 优先服务端 manifest 的 hasDetect 能力集，离线回退静态 [SYNCABLE_KINDS]。
 */
fun String.canSyncViaManifest(): Boolean =
    KoinComponentBy.get<PlatformManifestRepository>().canSync(this)

/**
 * 服务端平台清单的内存缓存（Koin 单例，`useCaseModule`）。
 *
 * 初值 = 本地 [PLATFORM_FIELDS]：网络未就绪 / 未登录 / 拉取失败时网格照常渲染，不阻塞。
 * 成功后原子替换为服务端合并列表（`StateFlow` 驱动 Compose 重组）。
 */
class PlatformManifestRepository(private val serverApi: ServerApi) {

    private val _addable = MutableStateFlow<List<PlatformFieldDef>>(PLATFORM_FIELDS)
    val addable: StateFlow<List<PlatformFieldDef>> = _addable.asStateFlow()

    /** 服务端声明的可自动检测 kind 集；初值 = 静态 [SYNCABLE_KINDS]（离线兜底），成功拉取后原子替换。 */
    private val _detectableKinds = MutableStateFlow(SYNCABLE_KINDS)
    val detectableKinds: StateFlow<Set<String>> = _detectableKinds.asStateFlow()

    /**
     * 自动同步资格判定（替代旧静态 `kindCanSync` 直查）：
     * 服务端清单已加载 → 以 hasDetect 能力集为准；未加载/离线 → 静态兜底集。
     * kind 大小写归一（服务端 kind 如 "qqNapcat"）。
     */
    fun canSync(kind: String): Boolean = kind.lowercase() in _detectableKinds.value

    @kotlin.concurrent.Volatile
    private var lastFetchMs = 0L

    /**
     * 幂等惰性加载：30s TTL 防抖，避免每次打开对话框都打网络。
     * 失败静默保留兜底（有 Log，不吞根因）。
     */
    suspend fun ensureLoaded() {
        val now = nowMs()
        if (now - lastFetchMs < TTL_MS) return
        lastFetchMs = now
        refresh()
    }

    /** 强制重拉。成功且非空 → 原子替换合并列表；失败 / 空 → 保留现有兜底。 */
    suspend fun refresh() {
        // [修复防御]: ServerApi.platforms() 是阻塞式 OkHttp 调用，必须在 IO 线程执行，
        // 避免调用方（Compose LaunchedEffect 跑在 Main）触发 ANR。
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
