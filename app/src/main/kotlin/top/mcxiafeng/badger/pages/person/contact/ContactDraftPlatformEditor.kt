package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.LinkSource
import top.mcxiafeng.badger.ui.glass.TextButton
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val CUSTOM_DRAFT_PLATFORM = "__custom_draft_platform__"

/** Reuses the detail platform grid and custom form; validation stays in the draft ViewModel. */
@Composable
internal fun ContactDraftPlatformEditor(
    initialKey: String, state: ContactDraftUiState, viewModel: CreateContactViewModel, onDismiss: () -> Unit,
) {
    val entry = state.draft.platforms[initialKey]
    var selected by rememberSaveable(initialKey) { mutableStateOf(initialKey) }
    var customName by rememberSaveable(initialKey) { mutableStateOf("") }
    var account by rememberSaveable(initialKey) {
        mutableStateOf(entry?.originalLink?.takeIf(String::isNotBlank) ?: entry?.jumpLink?.takeIf(String::isNotBlank) ?: entry?.value.orEmpty())
    }
    var displayName by rememberSaveable(initialKey) { mutableStateOf(entry?.displayName.orEmpty()) }
    var auxiliary by rememberSaveable(initialKey) {
        mutableStateOf(if (FIELD_DEF_MAP[initialKey]?.linkSource == LinkSource.LINK_ONLY) entry?.value.orEmpty() else "")
    }
    WindowDialog(show = true, title = if (selected.isEmpty()) "选择平台" else "填写平台账号", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected.isEmpty()) {
                PlatformGridSelector(state.draft.platforms.keys, onSelect = { selected = it; viewModel.clearError() },
                    onCustom = { selected = CUSTOM_DRAFT_PLATFORM; viewModel.clearError() })
                TextButton("取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            } else {
                ContactDraftPlatformFields(selected, customName, { customName = it }, account,
                    { account = it; viewModel.clearError() }, auxiliary, { auxiliary = it }, displayName,
                    { displayName = it }, state.error)
                ContactDraftDialogButtons(onDismiss, account.isNotBlank() || auxiliary.isNotBlank()) {
                    val key = if (selected == CUSTOM_DRAFT_PLATFORM) customName else selected
                    if (viewModel.stagePlatform(key, account, displayName, auxiliary, initialKey.takeIf(String::isNotBlank))) onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ContactDraftPlatformFields(
    selected: String, customName: String, onCustomName: (String) -> Unit,
    account: String, onAccount: (String) -> Unit, auxiliary: String, onAuxiliary: (String) -> Unit,
    displayName: String, onDisplayName: (String) -> Unit, error: String?,
) {
    if (selected == CUSTOM_DRAFT_PLATFORM) {
        CustomPlatformForm(customName, onCustomName, account, onAccount, displayName, onDisplayName, error)
    } else {
        val definition = FIELD_DEF_MAP[selected]
        Text(definition?.displayName ?: selected, style = MiuixTheme.textStyles.title4)
        TextField(account, onAccount, label = definition?.inputHint?.takeIf(String::isNotBlank) ?: "账号或链接",
            modifier = Modifier.fillMaxWidth())
        if (definition?.linkSource == LinkSource.LINK_ONLY) {
            TextField(auxiliary, onAuxiliary, label = "平台账号（选填，供手动搜索）", modifier = Modifier.fillMaxWidth())
        }
        TextField(displayName, onDisplayName, label = "平台昵称（选填）", modifier = Modifier.fillMaxWidth())
        if (selected == "wechat") Text("微信号需要对方手动搜索添加", style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
    }
}
