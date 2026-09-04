package top.mcxiafeng.badger

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.king.wechat.qrcode.WeChatQRCodeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.opencv.OpenCV
import top.mcxiafeng.badger.data.LegacyTagFixup
import top.mcxiafeng.badger.di.appStateModule
import top.mcxiafeng.badger.di.databaseModule
import top.mcxiafeng.badger.di.imageModule
import top.mcxiafeng.badger.di.networkModule
import top.mcxiafeng.badger.di.repositoryModule
import top.mcxiafeng.badger.di.useCaseModule
import top.mcxiafeng.badger.di.viewModelModule
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.navigation.ThemeConfig

/**
 * [§14.2] 移除 `@HiltAndroidApp`,改回普通 [Application]。
 *
 * Koin 不需要 code-gen / plugin / 注解处理 —— 只需在 onCreate 内调用
 * [startKoin] 装载 [databaseModule] / [networkModule] / 等。所有原 Hilt 入口已
 * 等价迁移到 Koin(详见 [KoinModule] 注释)。
 *
 * [Phase 3] 移除 `Configuration.Provider`(SyncWorkerFactory 随 PendingUpload 队列
 * 一起退役,WorkManager 回归默认 factory)。
 *
 * 与原 Hilt 实现的关键差异:
 * - 不再有 `Hilt_BadgerApplication` 生成父类 —— BadgerApplication 直接继承 [Application]。
 */
class BadgerApplication : Application(), SingletonImageLoader.Factory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // [KMP K05] DataStore：旧 SharedPreferences 一次性搬迁 + 灌内存缓存（阻塞，
        // 键少值小 <10ms；必须在 NavBarConfig/ThemeConfig.initialize 之前完成）
        top.mcxiafeng.badger.data.prefs.PrefsMigrator.migrateAll(this)
        top.mcxiafeng.badger.data.prefs.PrefsStore.initialize()
        // [KMP K07] Room KMP：注入 Application Context 供 shared 平台 builder 使用
        top.mcxiafeng.badger.shared.db.PlatformContextHolder.inject(this)
        NavBarConfig.initialize(this)
        ThemeConfig.initialize(this)

        // [§14.2] Koin 容器初始化。装载顺序与原 Hilt 一致:
        // 1. databaseModule 提供 AppDatabase + DAO
        // 2. repositoryModule 把 Impl 绑定到 Interface,依赖已注册的 DAO
        // 3. useCaseModule 注册 6 个 UseCase(纯用例,不混入单例)
        // 4. networkModule 注册 OkHttpClient + ServerApiFactory
        // 5. appStateModule 注册 Repository / StateHolder / 后台轮询等 Application 级单例
        // 6. imageModule 注册 Coil ImageLoader
        // 7. viewModelModule 注册 20 个 ViewModel
        // [修复防御]: Robolectric 单元测试有时序竞争:BadgerApplication.onCreate 会先于
        // 测试 setUp 触发,若其它用例已经 startKoin,这里必须 stop 后再 start,否则会抛
        // KoinApplicationAlreadyStartedException 拖崩整批用例。
        if (org.koin.core.context.GlobalContext.getOrNull() != null) {
            org.koin.core.context.GlobalContext.stopKoin()
        }
        startKoin {
            androidContext(this@BadgerApplication)
            modules(
                databaseModule,
                repositoryModule,
                networkModule,
                useCaseModule,
                appStateModule,
                imageModule,
                viewModelModule,
            )
        }

        // Hand a Context to static-object compat layers (ContactNetworkResolver).
        // [§14.2] 已迁移到 Koin,Context 不再需要注入到静态 compat 层。
        // 同步初始化 OpenCV + WeChatQRCodeDetector（CameraX ImageAnalysis 在独立线程池跑分析器，
        // 不等 ViewModel 懒加载，所以必须保证 CameraPreview 启动前二者都就绪）。
        // 跳过测试环境（Robolectric 无 native 库），避免 IllegalStateException 拖崩单测。
        if (!isRobolectric()) {
            try {
                OpenCV.initOpenCV()
                Log.d(TAG, "OpenCV 同步初始化完成")
            } catch (e: Throwable) {
                Log.w(TAG, "OpenCV 同步初始化失败，将由 ScannerViewModel 懒加载兜底", e)
            }
            try {
                WeChatQRCodeDetector.init(this)
                Log.d(TAG, "WeChatQRCodeDetector 同步初始化完成")
            } catch (e: Throwable) {
                Log.w(TAG, "WeChatQRCodeDetector 同步初始化失败，将由 ScannerViewModel 懒加载兜底", e)
            }
        } else {
            Log.d(TAG, "检测到 Robolectric 测试环境，跳过 OpenCV.initOpenCV() 和 WeChatQRCodeDetector.init()")
        }

        // 异步预加载行政区划数据(80KB + 700KB)。后台跑,不阻塞启动。
        // 用户进入联系人详情页点「国家/地区」时数据已就绪,避免 1-3s 等待。
        appScope.launch {
            try {
                get<top.mcxiafeng.badger.data.repository.WorldRegionRepository>().loadCountries()
                Log.d(TAG, "预加载 countries.json 完成")

                // 一次性 startup 副作用:补齐 v4→v5 迁移遗留 Tag 的 pinyinInitial 提示
                get<LegacyTagFixup>().runOnce()
            } catch (e: Exception) {
                Log.w(TAG, "后台启动副作用失败(可忽略)", e)
            }
        }

        // [Phase 3/T16c] 启动完整同步：先扫描存量 isLocalOnly 行补建 CREATE（一次性回填），
        // 再 push（离线创建的联系人/标签/名片夹上云），最后 pull 增量。
        // 未登录(无 token)时 401 由 pull 内部降级 Failed,不阻塞首屏。
        // 与 LegacyTagFixup 同模式:后台跑,失败可忽略(下次启动再来)。
        appScope.launch {
            try {
                val result = get<SyncEngine>().syncOnceIfIdle()
                Log.d(TAG, "启动同步完成: $result")
            } catch (e: Exception) {
                Log.w(TAG, "启动同步失败(可忽略,下次启动重试)", e)
            }
        }

        // [KMP K09] OutboxWorker（shared androidMain）与 SyncEngine（app）的解耦点：
        // 注入重放回调，Worker doWork 时经注册表取用
        val syncEngine = get<SyncEngine>()
        top.mcxiafeng.badger.sync.OutboxReplayRegistry.pushOnceProvider = { includeBackoff ->
            val o = syncEngine.pushOnce(includeBackoff)
            top.mcxiafeng.badger.sync.OutboxReplayRegistry.ReplayOutcome(o.pushedOps, o.failedOps)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader = get()

    private fun isRobolectric(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    companion object {
        private const val TAG = "BadgerApplication"
        @Volatile
        private var instance: BadgerApplication? = null

        fun getInstance(): BadgerApplication = instance
            ?: throw IllegalStateException("BadgerApplication.getInstance() called before onCreate()")
    }
}