package top.mcxiafeng.badger.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.pages.settings.components.NotLoggedInState
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupCard
import top.mcxiafeng.badger.pages.settings.components.SettingsListScaffold
import top.mcxiafeng.badger.pages.settings.components.SettingsMessageEffect
import top.mcxiafeng.badger.ui.components.BadgerDialog
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.WindowDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide

private const val TAG = "UserSettingsPage"

/** 服务端主题字符串选项 → 中文 label。 */
private val THEME_OPTIONS = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
private val LANGUAGE_OPTIONS = listOf("system" to "跟随系统", "zh-CN" to "简体中文")

/**
 * 短链提供商（值 = 服务端约定的 shortLinkProvider 字符串，UI 显示中文 label）。
 * 服务端契约（Badger-Server UserSettings.java / ShortLinkService.effectiveProvider）：
 * - "server"  → 本服务器自建短链（/api/shortlinks/，不依赖 short.io API Key）
 * - "shortio" → short.io 代理（/api/proxy/shortio/，需在服务端配置 API Key）
 * - null/空   → 未选择（服务端默认走 short.io，若管理员关掉则走 server）
 */
private val SHORT_LINK_PROVIDERS = listOf("server" to "服务端", "shortio" to "short.io")

/**
 * 用户设置页（新实现）：云端偏好（语言 / 主题 / 通知邮件 / 短链配置）。
 *
 * 主题写穿本地 ThemeConfig（立即生效 + 云端留存）。短链区：总开关门控「短链提供商」
 * 与「短链列表」入口（仅短链服务开启后显示）。
 */
@Composable
internal fun UserSettingsPage(onBack: () -> Unit, onNavigateToSubPage: (SettingsPage) -> Unit) {
    val viewModel: UserSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsMessageEffect(snackbarHostState, viewModel.messages)

    SettingsListScaffold(
        title = SettingsPage.UserSettings.title,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        when (val s = state) {
            is UserSettingsUiState.Loading -> item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            is UserSettingsUiState.Error -> item(key = "error") {
                Column(
                    modifier = Modifier.fillMaxSize().padding(BadgerSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "加载失败：${s.message}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(text = "重试", onClick = { viewModel.load() })
                }
            }

            is UserSettingsUiState.Success -> {
                if (!s.isLoggedIn) {
                    item(key = "not_logged_in") {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NotLoggedInState(
                                onLogin = onBack,
                                title = "未登录",
                                subtitle = "登录后可同步云端偏好设置",
                            )
                        }
                    }
                } else {
                    item(key = "settings") {
                        UserSettingsBody(s, viewModel, onNavigateToSubPage)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSettingsBody(
    s: UserSettingsUiState.Success,
    viewModel: UserSettingsViewModel,
    onNavigateToSubPage: (SettingsPage) -> Unit,
) {
    val settings = s.settings
    val themeEntry = remember(settings.theme) {
        DropdownEntry(
            items = THEME_OPTIONS.map { (value, label) ->
                DropdownItem(
                    text = label,
                    selected = settings.theme.equals(value, ignoreCase = true),
                    onClick = {
                        BadgerLog.d(TAG, "Theme: $value")
                        viewModel.updateTheme(value)
                    },
                )
            },
        )
    }
    val languageEntry = remember(settings.language) {
        DropdownEntry(
            items = LANGUAGE_OPTIONS.map { (value, label) ->
                DropdownItem(
                    text = label,
                    selected = settings.language.equals(value, ignoreCase = true),
                    onClick = {
                        BadgerLog.d(TAG, "Language: $value")
                        viewModel.updateLanguage(value)
                    },
                )
            },
        )
    }

    val themeLabel = THEME_OPTIONS.firstOrNull { it.first.equals(settings.theme, ignoreCase = true) }?.second
        ?: settings.theme ?: "未设置"
    val langLabel = LANGUAGE_OPTIONS.firstOrNull { it.first.equals(settings.language, ignoreCase = true) }?.second
        ?: settings.language ?: "未设置"

    SettingsGroupCard(
        rows = listOf(
            {
                WindowDropdownMenu(
                    title = "主题（云端同步）",
                    summary = themeLabel,
                    entry = themeEntry,
                )
            },
            {
                WindowDropdownMenu(
                    title = "语言",
                    summary = langLabel,
                    entry = languageEntry,
                )
            },
            {
                SwitchPreference(
                    title = "通知邮件",
                    summary = "接收站内通知的邮件提醒",
                    checked = settings.notifyEmail,
                    onCheckedChange = { v ->
                        BadgerLog.d(TAG, "notifyEmail: $v")
                        viewModel.updateNotifyEmail(v)
                    },
                )
            },
        ),
    )

    Spacer(Modifier.height(BadgerSpacing.md))

    // 短链服务总开关：开启后才展示「短链提供商」与「短链列表」入口
    val shortLinkRows = buildList<@Composable () -> Unit> {
        add {
            SwitchPreference(
                title = "短链服务",
                summary = if (s.shortLinkEnabled) "NFC 写入使用短链接" else "关闭后 NFC 使用原始长链接",
                checked = s.shortLinkEnabled,
                onCheckedChange = { viewModel.setShortLinkEnabled(it) },
            )
        }
        if (s.shortLinkEnabled) {
            add {
                val selectedIndex = SHORT_LINK_PROVIDERS.indexOfFirst { it.first.equals(settings.shortLinkProvider, ignoreCase = true) }
                val providerLabel = SHORT_LINK_PROVIDERS.getOrNull(selectedIndex)?.second ?: "未选择"
                OverlayDropdownPreference(
                    items = SHORT_LINK_PROVIDERS.map { it.second },
                    selectedIndex = selectedIndex,
                    title = "短链提供商",
                    summary = providerLabel,
                    showValue = false,
                    onSelectedIndexChange = { index ->
                        val value = SHORT_LINK_PROVIDERS[index].first
                        BadgerLog.d(TAG, "短链提供商: $value")
                        viewModel.updateShortLinkProvider(value)
                    },
                )
            }
            // short.io 提供商：额外展示 API Key 行（仅 short.io）
            if (settings.shortLinkProvider.equals("shortio", ignoreCase = true)) {
                add { ShortioApiKeyRow(settings.shortioApiKeySet, viewModel) }
            }
            // 短链列表：服务端模式可直接进入；short.io 需先配置 API Key 才能查看
            if (settings.shortLinkProvider.equals("server", ignoreCase = true) || settings.shortioApiKeySet) {
                add {
                    ArrowPreference(
                        title = "短链列表",
                        summary = "管理自建短链",
                        onClick = { onNavigateToSubPage(SettingsPage.ServerShortLinks) },
                    )
                }
            }
        }
    }
    SettingsGroupCard(rows = shortLinkRows)
}

@Composable
private fun ShortioApiKeyRow(
    apiKeySet: Boolean,
    viewModel: UserSettingsViewModel,
) {
    var showInput by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    ArrowPreferenceLike(
        title = "short.io API Key",
        summary = if (apiKeySet) "已配置" else "未配置",
        onClick = { if (apiKeySet) showClear = true else showInput = true },
    )
    if (showInput) {
        var input by remember { mutableStateOf("") }
        var visible by remember { mutableStateOf(false) }
        BadgerDialog(
            show = true,
            title = "设置 short.io API Key",
            onDismissRequest = { showInput = false },
            onPositive = {
                val trimmed = input.trim()
                if (trimmed.isNotBlank()) viewModel.updateShortioApiKey(trimmed)
                showInput = false
            },
        ) {
            Text(
                text = "仅写不回传：服务端不会明文返回此 Key。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(BadgerSpacing.xs))
            TextField(
                value = input,
                onValueChange = { input = it },
                label = "API Key",
                useLabelAsPlaceholder = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Lucide.EyeOff else Lucide.Eye,
                            contentDescription = if (visible) "隐藏" else "显示",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (showClear) {
        BadgerDialog(
            show = true,
            title = "清除 API Key",
            onDismissRequest = { showClear = false },
            isDestructive = true,
            positiveText = "清除",
            onPositive = {
                viewModel.clearShortioApiKey()
                showClear = false
            },
        ) {
            Text(
                text = "清除后服务端将不再持有 short.io API Key，短链功能会受影响。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}

/** ArrowPreference 的本包等价别名（空 summary 传 null，避免显示空串）。 */
@Composable
private fun ArrowPreferenceLike(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary.ifBlank { null },
        onClick = onClick,
    )
}
