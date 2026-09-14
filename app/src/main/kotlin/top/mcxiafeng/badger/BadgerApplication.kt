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

class BadgerApplication : Application(), SingletonImageLoader.Factory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        
        top.mcxiafeng.badger.data.prefs.PrefsMigrator.migrateAll(this)
        top.mcxiafeng.badger.data.prefs.PrefsStore.initialize()
        
        top.mcxiafeng.badger.shared.db.PlatformContextHolder.inject(this)
        NavBarConfig.initialize()
        ThemeConfig.initialize()

        
        
        
        
        
        
        
        
        
        
        
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

        
        
        
        
        
        top.mcxiafeng.badger.utils.HttpUtil.clientProvider = {
            org.koin.core.context.GlobalContext.get().get<okhttp3.OkHttpClient>()
        }

        
        
        
        
        
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

        
        
        appScope.launch {
            try {
                get<top.mcxiafeng.badger.data.repository.WorldRegionRepository>().loadCountries()
                Log.d(TAG, "预加载 countries.json 完成")

                
                get<LegacyTagFixup>().runOnce()
            } catch (e: Exception) {
                Log.w(TAG, "后台启动副作用失败(可忽略)", e)
            }
        }

        
        
        
        
        

        
        
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