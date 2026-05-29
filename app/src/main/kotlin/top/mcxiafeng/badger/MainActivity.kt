package top.mcxiafeng.badger

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * 应用主 Activity
 *
 * 启用边到边（Edge-to-Edge）显示，并设置 Compose 内容为 [App]。
 * NFC 使用 ReaderMode（回调方式），无需在 Activity 中处理 onNewIntent。
 */
@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { 
            AppTheme { App() }
        }
    }
}

/** Android Studio 预览入口 */
@Preview
@Composable
fun AppAndroidPreview() {
    AppTheme { App() }
}
