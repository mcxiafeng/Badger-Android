package top.mcxiafeng.badger.pages.setupguide

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.setServerUrlConfigured
import top.mcxiafeng.badger.network.UserProfileResponse
import top.mcxiafeng.badger.sync.SyncEngine

/**
 * 引导流程专用 VM（[§14.2] Koin `inject()` 字段注入，移除 `@HiltViewModel`）。
 *
 * 职责：
 * 1. **平台信息同步状态** (`isSyncing`)：进入时 true，结束（含异常）置 false，
 *    锁定「下一步」与翻页手势，防止 SetupStepProfile 在 displayName/avatarUrl 尚未落库前被渲染。
 * 2. **服务器地址配置**：封装 [ServerUrlHolder] + [ServerApiFactory] 三步原子序列
 *    （prefs → broadcast → factory hot-update），使 SetupStepServerUrl 仅需一次调用。
 * 3. **每页校验状态** ([pageValidity])：每个 step 把自己的 nextEnabled 上报到本 VM，
 *    SetupGuideScreen 据此锁 HorizontalPager 的 userScrollEnabled。
 * 4. **服务器连通性测试** ([testState])：Step 0 用，HEAD 请求验证 URL 可达。
 * 5. **登录后数据预热** ([bootstrapPostLogin])：登录成功后 fire-and-forget 拉 selfPerson 资料
 *    + Person/Collection/Tag 增量同步，让 Room Flow 触发所有订阅 UI 实时刷新。
 */
class SetupGuideViewModel : ViewModel() {
    val userProfileRepository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val syncEngine: SyncEngine = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val context: Context = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverUrlHolder: ServerUrlHolder = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val httpClient: OkHttpClient = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** 当前 Server URL — 直接代理 [ServerUrlHolder.url]，引导输入框初始值与变更实时刷新。 */
    val currentServerUrl: StateFlow<String> = serverUrlHolder.url

    // ========== 每页校验状态 ==========
    /**
     * page index → 该页 nextEnabled。
     * - SetupGuideScreen 读 `pageValidity[pagerState.currentPage]` 决定 `userScrollEnabled`
     * - 任一页校验失败 → 锁住 pager，必须用「下一步/上一步」按钮走完流程
     * - 进入新页时该 step 需在 LaunchedEffect 入口 + state 变更时调 [setPageValid]
     */
    private val _pageValidity = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val pageValidity: StateFlow<Map<Int, Boolean>> = _pageValidity.asStateFlow()

    fun setPageValid(page: Int, valid: Boolean) {
        if (_pageValidity.value[page] != valid) {
            _pageValidity.value = _pageValidity.value + (page to valid)
        }
    }

    // ========== 服务器连通性测试 ==========
    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    /**
     * 用 HEAD 请求验证 URL 可达性。沿用 OkHttpClient 全局配置 (connectTimeout=15s / readTimeout=15s),
     * 单次最坏等待 15s 后判不可达;任何 HTTP 状态码（含 404）都算可达,仅连接失败 / DNS 失败 / 超时算不可达。
     *
     * [修复输入清洗]: 不在 VM 层 normalize — 调用方 (SetupStepServerUrl) 已经在 next 前调过
     * [cleanServerUrl],这里的 [url] 应该是已清洗的合法 URL。
     *
     * [修复防御 #B3 超时漂移]: 原注释声明 5s 超时,与 NetworkModule 实际 15s 不符。本调用无法
     * 局部覆盖 callTimeout(共用 client) — 把注释与现实对齐,避免未来读者按错预期做 UI 设计。
     */
    fun testServerConnection(url: String) {
        if (_testState.value is TestState.Testing) return
        _testState.value = TestState.Testing
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(url)
                        .head()
                        .build()
                    httpClient.newCall(request).execute().use { it.code }
                }
            }
            _testState.value = result.fold(
                onSuccess = { code ->
                    Log.d(TAG, "testServerConnection: $url → HTTP $code (≤15s)")
                    TestState.Success(code)
                },
                onFailure = { e ->
                    Log.w(TAG, "testServerConnection: $url → ${e.javaClass.simpleName}: ${e.message} (≤15s)")
                    TestState.Failed(e.message ?: "无法连接到服务器")
                },
            )
        }
    }

    /** 重置测试状态 — 用户改了 URL 后需要重新测。 */
    fun resetTestState() {
        _testState.value = TestState.Idle
    }

    /**
     * 持久化新服务器 URL 并热更 ServerApi。
     *
     * 与 [top.mcxiafeng.badger.pages.settings.AccountSettingsViewModel.updateServerUrl]
     * 同构（prefs → broadcast → factory → configured 标记），统一成为引导 + 设置
     * 两个入口的契约，规避「入口不一致导致 banner 永远挂」的隐患。
     *
     * [V2-E2E #1] 默认 URL（emulator 专用 10.0.2.2:8080）不算用户主动配置 ——
     * 这里把"非默认 URL"视为已配置；恢复默认请改走 [resetServerUrlToDefault]。
     */
    fun updateServerUrl(newUrl: String, defaultUrl: String) {
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            Log.w(TAG, "updateServerUrl: blank input ignored")
            return
        }
        // [修复防御]: 三步顺序与 [AccountSettingsViewModel] 完全一致 —— 写 prefs 再广播，
        // 最后 push 到 factory。任何一步被进程杀死都能在下次启动由 prefs 自愈。
        serverUrlHolder.set(normalized)             // 1+2: 写 prefs + 广播 StateFlow
        serverApiFactory.updateBaseUrl(normalized)   // 3: ServerApi 热更 baseUrl
        setServerUrlConfigured(context, normalized != defaultUrl)
        Log.d(TAG, "Server URL updated: $normalized (hot-applied, configured=${normalized != defaultUrl})")
    }

    /** [V2-E2E #1] 用户点击「恢复默认」时调，把 configured 标志回退为 false。 */
    fun resetServerUrlToDefault(defaultUrl: String) {
        serverUrlHolder.set(defaultUrl)
        serverApiFactory.updateBaseUrl(defaultUrl)
        setServerUrlConfigured(context, false)
        Log.d(TAG, "Server URL reset to default: $defaultUrl")
    }

    /**
     * 包裹一个异步同步任务：进入时设置 isSyncing=true，结束时（含异常）置回 false。
     * 在引导页"填入平台 -> 拉取信息"期间阻止用户离开当前页。
     *
     * [修复防御 #B2 DELETE race]: `reason` 用于日志 + 后续把「哪些动作真正锁了闸」做成可观测
     * 指标(避免 ADD/EDIT/DELETE 三路径漂移)。忽略 (no-op) 路径会 Log.w 记录被拒原因,
     * 便于定位 UI 闸失效的根因。
     */
    fun runSync(reason: String = "sync", block: suspend () -> Unit) {
        if (_isSyncing.value) {
            Log.w(TAG, "[SYNC] runSync(reason=$reason) re-entered while syncing, ignored")
            return
        }
        Log.d(TAG, "[SYNC] runSync start reason=$reason")
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "[SYNC] sync block failed reason=$reason", e)
            } finally {
                _isSyncing.value = false
                Log.d(TAG, "[SYNC] runSync end reason=$reason")
            }
        }
    }

    /**
     * 登录/注册成功后的引导期数据预热。fire-and-forget,不阻塞 onNext 翻页。
     *
     * 1) 拉 selfPerson 落本地 [user_profile_cache]（不做直推,也不覆盖 platformsJson——
     *    平台列表由 SetupStepPlatforms 走 PlatformFieldManager 派生）
     * 2) [SyncEngine.syncOnceIfIdle] 先 push 本地未同步再 pull 增量（Person/Collection/Tag）
     *
     * Room Flow 会让所有订阅页面自动 recompose。
     *
     * [修复防御]: runCatching 包裹每一段。任何 IO 异常仅记日志,不抛、不污染引导流程。
     * 不要套 [runSync] —— UI 闸 ([pageValidity]) 已经独立管推进性,这里的同步只做
     * 后台数据预热,不应触发 isSyncing 锁（否则会给未来增加误导）。
     */
    fun bootstrapPostLogin() {
        Log.d(TAG, "[POSTLOGIN] bootstrap start")
        viewModelScope.launch {
            runCatching {
                val resp = withContext(Dispatchers.IO) { serverApiFactory.get().getProfile() }
                mergeProfile(resp)
                Log.d(TAG, "[POSTLOGIN] profile merged")
            }.onFailure { Log.w(TAG, "[POSTLOGIN] profile fetch failed", it) }

            // [修复防御]: syncOnceIfIdle 自带 AtomicBoolean 并发重入保护,与启动期那次幂等。
            runCatching {
                val r = syncEngine.syncOnceIfIdle()
                Log.d(TAG, "[POSTLOGIN] sync result: $r")
            }.onFailure { Log.w(TAG, "[POSTLOGIN] sync failed", it) }

            Log.d(TAG, "[POSTLOGIN] bootstrap done")
        }
    }

    /**
     * 把 [UserProfileResponse] 合并进 [UserProfileCacheEntity]。
     *
     * - 仅刷基础资料字段(name/bio/avatarPath/sex/country/region/birthday/backgroundURL/extra)
     * - 永不覆盖 `platformsJson`(contactMap 缺 jumpLink/avatarUrl 派生信息,平台列表由
     *   SetupStepPlatforms 走 PlatformFieldManager 派生)
     * - 仅当至少一个字段发生变化时才落库 + bumpProfile,避免无意义的 Room 写
     */
    private suspend fun mergeProfile(resp: UserProfileResponse) {
        val now = System.currentTimeMillis()
        val existing = userProfileRepository.getUserProfileOnce()
        val merged = (existing ?: UserProfileCacheEntity(name = "", updateTime = now))
            .copy(
                name = resp.name ?: resp.displayName ?: existing?.name ?: "",
                bio = resp.profile?.description ?: existing?.bio,
                avatarPath = resp.profile?.avatarURL ?: existing?.avatarPath,
                sex = resp.profile?.sex ?: existing?.sex,
                country = resp.profile?.country ?: existing?.country,
                region = resp.profile?.region ?: existing?.region,
                birthday = resp.profile?.birthday ?: existing?.birthday,
                backgroundURL = resp.profile?.backgroundURL ?: existing?.backgroundURL,
                extra = resp.profile?.extra?.toString()?.takeIf { it.isNotBlank() } ?: existing?.extra,
                updateTime = now,
            )
        if (existing != null && existing == merged) {
            Log.d(TAG, "mergeProfile: 无变化,跳过")
            return
        }
        userProfileRepository.saveUserProfile(merged)
    }

    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data class Success(val httpCode: Int) : TestState
        data class Failed(val message: String) : TestState
    }

    private companion object {
        const val TAG = "SetupGuideViewModel"
    }
}
