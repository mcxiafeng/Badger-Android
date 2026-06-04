package top.mcxiafeng.badger

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import top.mcxiafeng.badger.network.NetworkConfig
import top.mcxiafeng.badger.ui.navigation.NavBarConfig

@HiltAndroidApp(Application::class)
class BadgerApplication : Hilt_BadgerApplication() {

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
