package top.mcxiafeng.badger.pages.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AccountImportEmpty(state: AccountUiState, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(BadgerSpacing.lg), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xl)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // 内容能放下时无滚动范围；大字号溢出时仍可到达检查按钮，目标卡片保持可见。
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.busy) CircularProgressIndicator(size = BadgerSize.minimumTouchTarget)
                else Icon(Icons.Outlined.FolderOpen, contentDescription = null,
                    modifier = Modifier.size(BadgerSize.minimumTouchTarget), tint = MiuixTheme.colorScheme.primary)
                Text("暂时没有可导入的资料", style = MiuixTheme.textStyles.title4, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
                Text("本地用户有资料后，可在这里预览并导入。", style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
                Button(onClick = onCheck, enabled = !state.busy, colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.heightIn(min = BadgerSize.minimumTouchTarget)) {
                    Text(if (state.busy) "检查中…" else "重新检查")
                }
            }
        }
        ImportScopeInfo(state.name)
    }
}
