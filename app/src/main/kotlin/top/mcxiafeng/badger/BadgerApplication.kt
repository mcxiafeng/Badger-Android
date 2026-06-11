package top.mcxiafeng.badger

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.king.wechat.qrcode.WeChatQRCodeDetector
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import org.opencv.OpenCV
import top.mcxiafeng.badger.di.DatabaseEntryPoint
import top.mcxiafeng.badger.network.NetworkConfig
import top.mcxiafeng.badger.ui.navigation.NavBarConfig

@HiltAndroidApp(Application::class)
class BadgerApplication : Hilt_BadgerApplication(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        NavBarConfig.initialize(this)
        NetworkConfig.initialize(this)
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
