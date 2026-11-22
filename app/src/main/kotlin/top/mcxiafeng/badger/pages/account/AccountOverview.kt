package top.mcxiafeng.badger.pages.account

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import top.mcxiafeng.badger.data.sync.SyncProgress
import top.mcxiafeng.badger.ui.designsystem.BadgerDesignColors
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AccountOverview(
    state: AccountUiState,
    progress: SyncProgress,
    onLogin: () -> Unit,
    onSection: (String) -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onManageTags: (() -> Unit)?,
    onReauthenticate: () -> Unit = onLogin,
) {
    val status = remember(state.local, progress) { presentAccountSync(state.local, progress) }
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xl)) {
        AccountSyncCard(status, progress.running, state.busy) {
            when (status.action) {
                AccountSyncAction.LOGIN -> if (state.local) onLogin() else onReauthenticate()
                AccountSyncAction.CONFLICTS -> onSection("conflicts")
                AccountSyncAction.SYNC -> onSync()
            }
        }
        if (!state.local) AccountOverviewLinks(state, progress, onLogin, onSection, onLogout, onManageTags)
    }
}

@Composable
private fun AccountSyncCard(status: AccountSyncPresentation, running: Boolean, busy: Boolean, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(BadgerSpacing.cardPadding)) {
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
            AccountSectionTitle("同步状态")
            Row(horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                AccountSyncIndicator(status.tone, running)
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
                    Text(status.title, style = MiuixTheme.textStyles.title3)
                    status.detail?.let { Text(it, style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                }
            }
            Button(onClick = onAction, enabled = !busy && !running, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary()) { Text(status.actionLabel) }
        }
    }
}

@Composable
private fun AccountSyncIndicator(tone: AccountSyncTone, running: Boolean) {
    if (running) {
        CircularProgressIndicator(size = BadgerSpacing.xl)
        return
    }
    val icon = when (tone) {
        AccountSyncTone.SUCCESS -> Icons.Outlined.CloudDone
        AccountSyncTone.ATTENTION -> Icons.Outlined.ErrorOutline
        AccountSyncTone.NEUTRAL -> Icons.Outlined.CloudSync
    }
    val color = when (tone) {
        AccountSyncTone.SUCCESS -> if (isSystemInDarkTheme()) BadgerDesignColors.successDark else BadgerDesignColors.successLight
        AccountSyncTone.ATTENTION -> MiuixTheme.colorScheme.error
        AccountSyncTone.NEUTRAL -> MiuixTheme.colorScheme.primary
    }
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(BadgerSpacing.xl))
}

@Composable
private fun AccountOverviewLinks(state: AccountUiState, progress: SyncProgress, onLogin: () -> Unit,
    onSection: (String) -> Unit, onLogout: () -> Unit, onManageTags: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues()) {
        if (progress.tagNameConflict && onManageTags != null) {
            ArrowPreference(title = "调整标签名称", summary = "处理同名标签后继续同步",
                enabled = !state.busy, onClick = onManageTags)
        }
        ArrowPreference(title = "本地资料导入", summary = "预览并导入本地用户的资料",
            enabled = !state.busy, onClick = { onSection("import") })
        ArrowPreference(title = "冲突处理",
            summary = if (progress.conflicts > 0) "${progress.conflicts} 项待处理" else "核对本机与云端的资料差异",
            enabled = !state.busy, onClick = { onSection("conflicts") })
        ArrowPreference(title = "登录与切换账号", summary = state.name, enabled = !state.busy, onClick = onLogin)
        ArrowPreference(title = "退出账号", enabled = !state.busy, onClick = onLogout)
    }
}
