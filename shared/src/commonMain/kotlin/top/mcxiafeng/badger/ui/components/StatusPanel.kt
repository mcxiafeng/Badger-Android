package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Lucide
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 全屏加载 / 错误态面板，供各页面 `when (uiState)` 分支直接复用。 */
class StatusPanel {

    companion object {

        @Composable
        fun onLoading() {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Loading...")
                }
            }
        }

        @Composable
        fun onError(message: String, retryFunction: () -> Unit) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Lucide.CircleX,
                        contentDescription = "错误",
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(160.dp),
                    )
                    Text("出错了！$message", color = MiuixTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        text = "点击重试",
                        onClick = retryFunction,
                    )
                }
            }
        }
    }
}
