package top.mcxiafeng.badger

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
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
        if (!isRobolectric()) {
            org.opencv.OpenCV.initOpenCV()
            com.king.wechat.qrcode.WeChatQRCodeDetector.init(this)
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
        @Volatile
        private var instance: BadgerApplication? = null

        fun getInstance(): BadgerApplication = instance
            ?: throw IllegalStateException("BadgerApplication.getInstance() called before onCreate()")
    }
}
