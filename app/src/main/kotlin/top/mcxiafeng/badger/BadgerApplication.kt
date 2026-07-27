package top.mcxiafeng.badger

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Configuration
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
import top.mcxiafeng.badger.di.databaseModule
import top.mcxiafeng.badger.di.imageModule
import top.mcxiafeng.badger.di.networkModule
import top.mcxiafeng.badger.di.repositoryModule
import top.mcxiafeng.badger.di.useCaseModule
import top.mcxiafeng.badger.di.viewModelModule
import top.mcxiafeng.badger.sync.ContactSyncBootstrapper
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import top.mcxiafeng.badger.sync.SyncWorkerFactory
import top.mcxiafeng.badger.ui.navigation.NavBarConfig

/**
 * [§14.2] 移除 `@HiltAndroidApp`,改回普通 [Application]。
 *
 * Koin 不需要 code-gen / plugin / 注解处理 —— 只需在 onCreate 内调用
 * [startKoin] 装载 [databaseModule] / [networkModule] / 等。所有原 Hilt 入口已
 * 等价迁移到 Koin(详见 [KoinModule] 注释)。
 *
 * 与原 Hilt 实现的关键差异:
 * - 不再有 `Hilt_BadgerApplication` 生成父类 —— BadgerApplication 直接继承 [Application]。
 * - 原 `EntryPointAccessors.fromApplication(...)` 全部改为 `koin.get<T>()` 或
 *   `org.koin.android.ext.android.get<T>()` 顶层工具。
 */
class BadgerApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // [V2-P4] WorkManager Configuration.Provider:让 WorkManager 用 [SyncWorkerFactory]
    // 替代默认 factory,从而能在拉起 [PendingUploadWorker] 时从 Koin 解析依赖。
    override val workManagerConfiguration: Configuration
        get() {
            val factory = SyncWorkerFactory(this)
            Log.d(TAG, "workManagerConfiguration: 提供 SyncWorkerFactory (Koin 模式)")
            return Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .setWorkerFactory(factory)
                .build()
        }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NavBarConfig.initialize(this)

        // [§14.2] Koin 容器初始化。装载顺序与原 Hilt 一致:
        // 1. databaseModule 提供 AppDatabase + DAO
        // 2. repositoryModule 把 Impl 绑定到 Interface,依赖已注册的 DAO
        // 3. useCaseModule 注册 UseCase / Snapshotter / Scheduler / Bootstrapper 等
        // 4. networkModule 注册 OkHttpClient + ServerApiFactory,后者在首次 get() 时
        //    通过 baseClient 构造 ServerApi 并 install;NetworkModule.provideOkHttpClient
        //    现在只是 lambda 包装(无 Hilt),可与 Koin 协同启动。
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
                useCaseModule,
                networkModule,
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

        // [V2-P11] 老数据 isLocalOnly=true 启动主动 sync。
        // 与 LegacyTagFixup 同模式:后台跑,失败可忽略(下次启动再来)。
        // 不阻塞 onCreate,不影响首屏 UI。
        appScope.launch {
            try {
                get<ContactSyncBootstrapper>().runOnce()
            } catch (e: Exception) {
                Log.w(TAG, "ContactSyncBootstrapper.runOnce 失败(可忽略)", e)
            }
        }

        // [V2-P4] WorkManager 已通过本类的 Configuration.Provider 接管初始化。
        // 这里启动 PendingUploadScheduler.bootstrap() — 注册 ProcessLifecycle + NetworkCallback
        // 监听器 + 主动 kick 一次(恢复杀后台期间堆积的 op)。
        try {
            get<PendingUploadScheduler>().bootstrap()
        } catch (e: Exception) {
            Log.w(TAG, "PendingUploadScheduler.bootstrap() 失败(可忽略,WorkManager 仍可在外部 kick 触发)", e)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader = get()

    private fun isRobolectric(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    companion object {
        private const val TAG = "Tester"
        @Volatile
        private var instance: BadgerApplication? = null

        fun getInstance(): BadgerApplication = instance
            ?: throw IllegalStateException("BadgerApplication.getInstance() called before onCreate()")
    }
}