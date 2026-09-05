package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.PLATFORM_FIELDS
import top.mcxiafeng.badger.ocr.PlatformFieldDef

/**
 * [Phase 4 剩余] 服务端平台清单（`GET /api/resolve/platforms`）→ UI 可渲染列表的桥。
 *
 * 交接决策（`docs/api-handover-migration-plan.md` §0）：「以服务端为主」—— 客户端放弃本地
 * 平台枚举，用服务端清单决定「哪些平台可添加、以什么顺序展示」；本地 [PLATFORM_FIELDS] 降级
 * 为服务端清单的 **UI 标签映射**（图标 / linkTemplate / inputHint / linkSource / contactType），
 * 按 `fieldKey` 关联。
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
                Log.w(TAG, "platforms parse skip: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }

        private const val TAG = ApiCore.TAG
    }
}

/**
 * 服务端清单 → UI 用 [PlatformFieldDef] 列表（纯函数，可单测）。
 *
 * - `null`/空 → 本地 [PLATFORM_FIELDS]（离线 / 未登录 / 拉取失败兜底；含群组，与「全量显示」一致）。
 * - 非空 → 过滤 `enabled`，**保服务端注册表序**：
 *   - 本地 `FIELD_DEF_MAP[key]` 命中 → 复用本地 UI 元数据，仅 `displayName` 以服务端为准
 *     （空则退回本地）。
 *   - 未命中（服务端独有 / 自定义平台）→ 动态构造默认 def（`ContactType.None` + `ic_website` 图标兜底）。
 */
fun mergeServerPlatforms(server: List<ServerPlatform>?): List<PlatformFieldDef> {
    if (server.isNullOrEmpty()) return PLATFORM_FIELDS
    return server.filter { it.enabled }.mapNotNull { sp ->
        val local = FIELD_DEF_MAP[sp.fieldKey]
        if (local != null) {
            local.copy(displayName = sp.displayName.ifBlank { local.displayName })
        } else {
            PlatformFieldDef(
                fieldKey = sp.fieldKey,
                displayName = sp.displayName.ifBlank { sp.fieldKey },
                contactType = ContactType.None,
                iconName = "ic_website",
            )
        }
    }
}

/**
 * 服务端平台清单的内存缓存（Koin 单例，`useCaseModule`）。
 *
 * 初值 = 本地 [PLATFORM_FIELDS]：网络未就绪 / 未登录 / 拉取失败时网格照常渲染，不阻塞。
 * 成功后原子替换为服务端合并列表（`StateFlow` 驱动 Compose 重组）。
 */
class PlatformManifestRepository(private val serverApi: ServerApi) {

    private val _addable = MutableStateFlow<List<PlatformFieldDef>>(PLATFORM_FIELDS)
    val addable: StateFlow<List<PlatformFieldDef>> = _addable.asStateFlow()

    @Volatile
    private var lastFetchMs = 0L

    /**
     * 幂等惰性加载：30s TTL 防抖，避免每次打开对话框都打网络。
     * 失败静默保留兜底（有 Log，不吞根因）。
     */
    suspend fun ensureLoaded() {
        val now = System.currentTimeMillis()
        if (now - lastFetchMs < TTL_MS) return
        lastFetchMs = now
        refresh()
    }

    /** 强制重拉。成功且非空 → 原子替换合并列表；失败 / 空 → 保留现有兜底。 */
    suspend fun refresh() {
        // [修复防御]: ServerApi.platforms() 是阻塞式 OkHttp 调用，必须在 IO 线程执行，
        // 避免调用方（Compose LaunchedEffect 跑在 Main）触发 ANR。
        val raw = try {
            withContext(Dispatchers.IO) { serverApi.platforms() }
        } catch (e: Throwable) {
            Log.w(TAG, "platforms fetch failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
        if (raw.isNullOrEmpty()) {
            if (raw == null) Log.w(TAG, "platforms fetch empty, keep local fallback")
            return
        }
        val merged = mergeServerPlatforms(raw.mapNotNull { ServerPlatform.parse(it) })
        if (merged.isNotEmpty()) _addable.value = merged
    }

    private companion object {
        const val TAG = "PlatformManifest"
        const val TTL_MS = 30_000L
    }
}
