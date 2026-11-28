package top.mcxiafeng.badger

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
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
import top.mcxiafeng.badger.data.account.AccountContext
import top.mcxiafeng.badger.data.account.AccountStore
import top.mcxiafeng.badger.data.repository.GaodeConfig
import top.mcxiafeng.badger.di.DatabaseEntryPoint
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.network.auth.AuthSessionStore
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.utils.EncryptedPrefs

/** Hilt EntryPoint:让 BadgerApplication 拿到启动期一次性副作用 LegacyTagFixup */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppStartupEntryPoint {
    fun legacyTagFixup(): LegacyTagFixup
    fun accountStore(): AccountStore
}

@HiltAndroidApp(Application::class)
class BadgerApplication : Hilt_BadgerApplication(), SingletonImageLoader.Factory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        SingletonImageLoader.setSafe { newImageLoader(this@BadgerApplication) }
        Log.d("BadgerApplicationTester", "已注册账号图片加载器工厂")
        NavBarConfig.initialize(this)
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

        // 一次性 startup 副作用:补齐 v4→v5 迁移遗留 Tag 的 pinyinInitial 提示。
        // 行政区划数据已改为高德 API 按需拉取,无静态数据集可预加载,故不在启动期拉取。
        appScope.launch {
            try {
                val entry = EntryPointAccessors.fromApplication(
                    this@BadgerApplication, AppStartupEntryPoint::class.java
                )
                entry.legacyTagFixup().runOnce()
            } catch (e: Exception) {
                Log.w(TAG, "后台启动副作用失败(可忽略)", e)
            }
        }

        // 后台预热加密凭据存储：EncryptedSharedPreferences 首次创建要走 Keystore/Tink（实测约 300ms），
        // 提前在 IO 线程完成，设置页等 UI 路径读 Key 时直接命中缓存，不再阻塞主线程吃掉转场动画。
        appScope.launch {
            try {
                val store = EntryPointAccessors.fromApplication(
                    this@BadgerApplication, AppStartupEntryPoint::class.java
                ).accountStore()
                val scoped = AccountContext(this@BadgerApplication, store.current())
                EncryptedPrefs.warmUp(scoped, WARM_UP_ENCRYPTED_PREFS)
                Log.d(TAG, "加密凭据存储预热完成：${WARM_UP_ENCRYPTED_PREFS.size} 个文件")
            } catch (e: Exception) {
                Log.w(TAG, "加密凭据存储预热失败（UI 路径会退回首次同步创建）", e)
            }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return EntryPointAccessors.fromApplication(
            this, DatabaseEntryPoint::class.java
        ).imageLoader()
    }

    private fun isRobolectric(): Boolean {
        return "robolectric" in Build.FINGERPRINT.lowercase()
    }

    companion object {
        private const val TAG = "Tester"

        /** 启动预热清单：全部加密凭据存储，UI 与后台路径都会读到的四个文件。 */
        private val WARM_UP_ENCRYPTED_PREFS = listOf(
            AiOcrConfig.ENCRYPTED_PREFS_NAME,
            ShortLinkService.ENCRYPTED_PREFS_NAME,
            GaodeConfig.ENCRYPTED_PREFS_NAME,
            AuthSessionStore.PREFS_NAME,
        )

        @Volatile
        private var instance: BadgerApplication? = null

        fun getInstance(): BadgerApplication = instance
            ?: throw IllegalStateException("BadgerApplication.getInstance() called before onCreate()")
    }
}
