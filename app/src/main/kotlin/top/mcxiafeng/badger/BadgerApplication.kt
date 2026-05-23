package top.mcxiafeng.badger

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import top.mcxiafeng.badger.ui.navigation.NavBarConfig

@HiltAndroidApp(Application::class)
class BadgerApplication : Hilt_BadgerApplication() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        NavBarConfig.initialize(this)
        org.opencv.OpenCV.initOpenCV()
        com.king.wechat.qrcode.WeChatQRCodeDetector.init(this)
    }

    companion object {
        @Volatile
        private var instance: BadgerApplication? = null

        fun getInstance(): BadgerApplication = instance!!
    }
}
