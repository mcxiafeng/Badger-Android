package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupCard
import top.mcxiafeng.badger.pages.settings.components.SettingsListScaffold
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide

private const val TAG = "NfcSettings"

/**
 * NFC 配置页（重写：VM 驱动，UI 不再直连 [top.mcxiafeng.badger.network.ShortLinkService]）。
 *
 * 内容：短链接开关 / API Key / 域名 / 短链 / 当前指向 / 自定义短链高级配置。
 */
@Composable
internal fun NfcSettingsPage(onBack: () -> Unit) {
    val viewModel: NfcSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    var apiKeyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    BackHandler(
        enabled = showDomainDialog || showLinkDialog || showCreateDialog,
    ) {
        showDomainDialog = false
        showLinkDialog = false
        showCreateDialog = false
    }

    SettingsListScaffold(title = SettingsPage.NfcSettings.title, onBack = onBack) {
        item(key = "nfc_help") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NFC 碰一碰即可将你的名片分享给对方。开启短链接后，NFC 标签上只存储短网址，可随时更新指向的目标。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    lineHeight = 1.5.em,
                    modifier = Modifier.padding(BadgerSpacing.lg),
                )
            }
        }

        item(key = "shortlink_toggle") {
            SettingsGroupCard(
                rows = listOf {
                    SwitchPreference(
                        title = "开启短链接",
                        summary = "启用后 NFC 写入将使用短链接，否则使用原始长链接",
                        checked = state.shortLinkEnabled,
                        onCheckedChange = { viewModel.setShortLinkEnabled(it) },
                    )
                },
            )
        }

        if (state.shortLinkEnabled) {
            item(key = "shortlink_settings") {
                SettingsGroupCard(
                    rows = listOf(
                        {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(text = "API Key", style = MiuixTheme.textStyles.body1)
                                Spacer(Modifier.height(4.dp))
                                TextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = "输入 short.io API Key",
                                    useLabelAsPlaceholder = true,
                                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                            Icon(
                                                imageVector = if (apiKeyVisible) Lucide.EyeOff else Lucide.Eye,
                                                contentDescription = if (apiKeyVisible) "隐藏" else "显示",
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            if (!focusState.isFocused) {
                                                viewModel.saveApiKey(apiKeyInput)
                                            }
                                        },
                                )
                            }
                        },
                        {
                            ArrowPreference(
                                title = "域名",
                                summary = when {
                                    state.domain.isNotBlank() -> state.domain
                                    state.domainsLoading -> "加载中..."
                                    state.domainError != null -> "获取失败"
                                    state.apiKey.isBlank() -> "请先填写 API Key"
                                    else -> "点击选择"
                                },
                                onClick = {
                                    if (state.domains.isNotEmpty()) {
                                        BadgerLog.d(TAG, "Domain dialog opened")
                                        showDomainDialog = true
                                    }
                                },
                            )
                        },
                        {
                            ArrowPreference(
                                title = "短链接",
                                summary = when {
                                    state.shortUrl != null -> state.shortUrl!!
                                    state.linksLoading -> "加载中..."
                                    state.linkError != null -> "获取失败"
                                    state.domain.isBlank() -> "请先选择域名"
                                    else -> "点击选择"
                                },
                                onClick = {
                                    if (state.domain.isNotBlank()) {
                                        BadgerLog.d(TAG, "Link dialog opened")
                                        showLinkDialog = true
                                    }
                                },
                            )
                        },
                        {
                            // 只读状态行用 BasicComponent（无箭头，不暗示可点击）
                            BasicComponent(
                                title = "当前指向",
                                summary = when {
                                    state.detailsLoading -> "更新中..."
                                    state.detailsError != null -> "获取失败: ${state.detailsError}"
                                    state.currentLinkDetails != null &&
                                        state.currentLinkDetails!!.originalURL.isNotBlank() ->
                                        "${state.defaultPlatform ?: ""} ${state.currentLinkDetails!!.originalURL}".trim()
                                    else -> "未设置目标地址"
                                },
                            )
                        },
                    ),
                )
            }
        }

        item(key = "advanced_settings") {
            val rows = buildList<@Composable () -> Unit> {
                add {
                    SwitchPreference(
                        title = "使用其他短链接服务（高级）",
                        summary = "使用自定义短链接平台替代 short.io",
                        checked = state.customEnabled,
                        onCheckedChange = { viewModel.setCustomEnabled(it) },
                    )
                }
                if (state.customEnabled) {
                    AdvancedField.entries.forEach { field ->
                        add { AdvancedFieldRow(field, state) { value -> viewModel.updateAdvanced(field, value) } }
                    }
                }
            }
            SettingsGroupCard(rows = rows)
        }

        if (state.customEnabled) {
            item(key = "advanced_help") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "占位符说明\n{url} → 目标链接\n{linkId} → 链接 ID",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 1.5.em,
                        modifier = Modifier.padding(BadgerSpacing.lg),
                    )
                }
            }
        }
    }

    // ── 弹窗 ──────────────────────────────────────────────────────

    if (showDomainDialog) {
        NfcDomainPickerDialog(
            domains = state.domains,
            loading = state.domainsLoading,
            error = state.domainError,
            selectedHostname = state.domain,
            onPick = { d ->
                viewModel.selectDomain(d)
                showDomainDialog = false
            },
            onRetry = { viewModel.retryDomains() },
            onDismiss = { showDomainDialog = false },
        )
    }

    if (showLinkDialog) {
        NfcLinkPickerDialog(
            links = state.links,
            loading = state.linksLoading,
            error = state.linkError,
            selectedLinkId = state.selectedLinkId,
            domain = state.domain,
            onPick = { l ->
                viewModel.selectLink(l)
                showLinkDialog = false
            },
            onRetry = { viewModel.retryLinks() },
            onCreate = {
                showLinkDialog = false
                showCreateDialog = true
            },
            onDismiss = { showLinkDialog = false },
        )
    }

    if (showCreateDialog) {
        NfcCreateLinkDialog(
            creating = state.creatingLink,
            error = state.createError,
            onConfirm = { url ->
                viewModel.createLink(url)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

/** 高级配置单字段行：label + TextField（onValueChange 直写 VM）。 */
@Composable
private fun AdvancedFieldRow(
    field: AdvancedField,
    state: NfcSettingsUiState,
    onValueChange: (String) -> Unit,
) {
    val (label, value, placeholder) = when (field) {
        AdvancedField.API_URL -> Triple("API 地址", state.apiUrl, "https://api.example.com")
        AdvancedField.UPDATE_PATH -> Triple("更新端点", state.updatePath, "/links/{linkId}")
        AdvancedField.API_METHOD -> Triple("HTTP 方法", state.apiMethod, "POST")
        AdvancedField.AUTH_HEADER -> Triple("认证头名称", state.authHeader, "Authorization")
        AdvancedField.AUTH_PREFIX -> Triple("认证前缀", state.authPrefix, "Bearer ")
        AdvancedField.UPDATE_BODY -> Triple("更新请求体", state.updateBody, """{"originalURL":"{url}"}""")
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = label, style = MiuixTheme.textStyles.body1)
        Spacer(Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = placeholder,
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
