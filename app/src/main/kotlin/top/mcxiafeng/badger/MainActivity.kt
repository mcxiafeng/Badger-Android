package top.mcxiafeng.badger

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
 *
 * [C3] Deep Link 支持：`badger://persons/{serverId}` → 跳转联系人详情。
 * [KMP K13c] 解析结果改经 [DeepLinkBus]（AppLinkHandler 契约）喂给 common App composable——
 * Activity 壳层与 UI 解耦。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // [C3] 解析 Deep Link intent（冷启动）
        DeepLinkBus.setPending(parseDeepLink(intent))

        setContent {
            AppTheme { App() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // [C3] 热启动 deep link：经 DeepLinkBus 通知 App composable
        val serverId = parseDeepLink(intent)
        if (serverId != null) {
            DeepLinkBus.emit(serverId)
        }
    }

    /**
     * [C3] 解析 Deep Link intent，提取 serverId。
     *
     * 格式：`badger://persons/{serverId}`
     * - serverId 必须是合法 UUID 格式（防止注入）
     * - 返回 null 表示无有效 deep link
     */
    private fun parseDeepLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null

        // 校验 scheme 和 host
        if (uri.scheme != "badger" || uri.host != "persons") return null

        // 提取 pathSegments：["persons", "{serverId}"] → 取最后一个
        val serverId = uri.lastPathSegment ?: return null

        // 校验 UUID 格式（防止恶意输入）
        return try {
            java.util.UUID.fromString(serverId)
            serverId
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Invalid deep link UUID: $serverId")
            null
        }
    }
}

/** Android Studio 预览入口 */
@Preview
@Composable
fun AppAndroidPreview() {
    AppTheme { App() }
}
