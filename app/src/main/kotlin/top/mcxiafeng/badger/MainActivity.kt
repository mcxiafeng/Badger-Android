package top.mcxiafeng.badger

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * 应用主 Activity
 *
 * [§14.2] 移除 `@AndroidEntryPoint` — 普通 ComponentActivity 即可,所有依赖走
 * Koin `koinViewModel<>` / `GlobalContext.get()` 解析。
 *
 * 启用边到边（Edge-to-Edge）显示，并设置 Compose 内容为 [App]。
 * NFC 使用 ReaderMode（回调方式），无需在 Activity 中处理 onNewIntent。
 */
class MainActivity : ComponentActivity() {
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