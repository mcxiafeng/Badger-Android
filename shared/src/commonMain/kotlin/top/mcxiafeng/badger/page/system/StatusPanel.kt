package top.mcxiafeng.badger.page.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Lucide
import kotlinx.atomicfu.TraceBase.None.append
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

class StatusPanel {

    companion object{
        @Composable
        fun onLoading(){
            Box(
                modifier = Modifier.fillMaxSize(),          // 关键：容器先占满
                contentAlignment = Alignment.Center         // 关键：内容居中
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,   // 组内水平居中
                    verticalArrangement = Arrangement.spacedBy(12.dp)     // 间距
                ) {
                    CircularProgressIndicator()
                    Text("Loading...")
                }
            }
        }


        @Composable
        fun onError(message: String, retryFunction: () -> Unit){
            Box(
                modifier = Modifier.fillMaxSize(),          // 关键：容器先占满
                contentAlignment = Alignment.Center         // 关键：内容居中
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Lucide.CircleX,
                        contentDescription = "错误",
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(160.dp)
                    )
                    Text("出错了！$message", color = MiuixTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { retryFunction() }) {
                        Text("点击重试")
                    }
                }
            }
        }

    }


}