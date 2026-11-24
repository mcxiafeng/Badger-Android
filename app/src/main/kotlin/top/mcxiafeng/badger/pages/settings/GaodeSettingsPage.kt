package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GaodeSettingsPage(onBack: () -> Unit) {
    val viewModel: GaodeSettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val bottomPadding = LocalFloatingBarBottomPadding.current
    Scaffold(topBar = {
        TopAppBar(
            title = "高德地图配置",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )
    }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bottomPadding),
        ) {
            item {
                Card(insideMargin = PaddingValues(16.dp)) {
                    GaodeSettingsForm(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun GaodeSettingsForm(state: GaodeSettingsUiState, viewModel: GaodeSettingsViewModel) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.initialized) {
            GaodeKeyField(state.apiKey, enabled = !state.busy, onChange = viewModel::onKeyChanged)
            Text(
                text = "使用高德开放平台的 Web 服务 Key。清空后保存可移除配置。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Button(
                onClick = viewModel::saveKey,
                enabled = state.dirty && !state.busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("保存") }
            TextButton(
                text = "测试连接",
                enabled = !state.busy && !state.dirty && state.apiKey.isNotBlank(),
                onClick = viewModel::testConnection,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (!state.busy) {
            TextButton(text = "重试读取", onClick = viewModel::loadKey)
        }
        val status = if (state.busy) "处理中…" else state.message
        if (status != null) {
            Text(
                text = status,
                style = MiuixTheme.textStyles.body2,
                color = if (state.hasError) MiuixTheme.colorScheme.error
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun GaodeKeyField(key: String, enabled: Boolean, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    TextField(
        value = key,
        onValueChange = onChange,
        label = "高德 Web 服务 Key",
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "隐藏高德 Key" else "显示高德 Key",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
