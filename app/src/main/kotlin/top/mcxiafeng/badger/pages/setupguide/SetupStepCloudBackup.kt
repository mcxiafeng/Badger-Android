package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.network.WebDavConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SetupStepCloudBackup(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf(WebDavConfig.getServerUrl(context)) }
    var username by remember { mutableStateOf(WebDavConfig.getUsername(context)) }
    var password by remember { mutableStateOf(WebDavConfig.getPassword(context)) }
    var passwordVisible by remember { mutableStateOf(false) }

    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            Log.d(TAG, "Cloud backup step skipped")
            onSkip()
        },
        onNext = {
            Log.d(TAG, "Cloud backup step completed")
            onNext()
        },
        nextText = "保存并继续",
        nextEnabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "云端备份",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "保护你的数据不丢失",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "服务器地址", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            WebDavConfig.saveServerUrl(context, it)
                            Log.d(TAG, "WebDAV server URL updated")
                        },
                        label = "如 https://dav.example.com",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "用户名", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = username,
                        onValueChange = {
                            username = it
                            WebDavConfig.saveUsername(context, it)
                            Log.d(TAG, "WebDAV username updated")
                        },
                        label = "WebDAV 用户名",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "密码", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = password,
                        onValueChange = {
                            password = it
                            WebDavConfig.savePassword(context, it)
                            Log.d(TAG, "WebDAV password updated")
                        },
                        label = "WebDAV 密码",
                        useLabelAsPlaceholder = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "隐藏" else "显示",
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}