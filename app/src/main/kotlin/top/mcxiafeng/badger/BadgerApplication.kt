package top.mcxiafeng.badger

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.king.wechat.qrcode.WeChatQRCodeDetector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opencv.OpenCV
import top.mcxiafeng.badger.data.repository.WorldRegionRepository
import top.mcxiafeng.badger.di.DatabaseEntryPoint
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ui.navigation.NavBarConfig

/** Hilt EntryPoint:让 BadgerApplication 拿到 WorldRegionRepository 实例 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RegionRepoEntryPoint {
    fun worldRegionRepository(): WorldRegionRepository
    fun legacyTagFixup(): LegacyTagFixup
}

@HiltAndroidApp(Application::class)
class BadgerApplication : Hilt_BadgerApplication(), SingletonImageLoader.Factory, Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // [V2-P4] WorkManager Configuration.Provider:让 WorkManager 用 Hilt 注入 WorkerFactory,
    // 进而能注入 PendingUploadWorker 所需的 Dao/DeviceIdProvider。详细规约见
    // docs/BADGER_V2_CLIENT_PLAN.md §4.5。当前阶段 Provider 仅注册最小配置;P4 阶段
    // 才接 syncWorkerFactory,这里先 hold 住不让 WorkManager 走默认 initializer 报错。
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        NavBarConfig.initialize(this)
        // Hand a Context to static-object compat layers (ContactNetworkResolver).
        ContactNetworkResolver.setContext(this)
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
                val entry = EntryPointAccessors.fromApplication(
                    this@BadgerApplication, RegionRepoEntryPoint::class.java
                )
                entry.worldRegionRepository().loadCountries()
                Log.d(TAG, "预加载 countries.json 完成")
                // 不主动预加载 states.json(700KB),只在用户第一次点地区时才拉
                // —— countries 是首次必读,states 是按需

                // 一次性 startup 副作用:补齐 v4→v5 迁移遗留 Tag 的 pinyinInitial 提示
                entry.legacyTagFixup().runOnce()
            } catch (e: Exception) {
                Log.w(TAG, "后台启动副作用失败(可忽略)", e)
            }
        }

        // [V2-P4] WorkManager 已通过本类的 Configuration.Provider 接管初始化。
        // 这里不做 enqueue —— P4 阶段 PendingUploadScheduler.kick() 接管触发。
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return EntryPointAccessors.fromApplication(
            context, DatabaseEntryPoint::class.java
        ).imageLoader()
    }

    private fun isRobolectric(): Boolean {
        return "robolectric" in Build.FINGERPRINT.lowercase()
    }

    companion object {
        private const val TAG = "Tester"
        @Volatile
        private var instance: BadgerApplication? = null

        fun getInstance(): BadgerApplication = instance
            ?: throw IllegalStateException("BadgerApplication.getInstance() called before onCreate()")
    }
}
