package top.mcxiafeng.badger.pages.person.contact


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.LinkSource
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AUTOMATIC_MIN_LINES = 4
private const val AUTOMATIC_MAX_LINES = 8

@Composable
internal fun AutomaticPlatformContent(
    state: AddPlatformUiState,
    onInputChange: (String) -> Unit,
    onManual: () -> Unit,
    onRemovePending: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier, contentPadding = PaddingValues(top = BadgerSpacing.lg + BadgerSpacing.scrollContentTopGap, bottom = BadgerSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap)) {
        item(key = "input") { AutomaticPlatformInput(state, onInputChange) }
        item(key = "manual") {
            Card {
                ArrowPreference(title = "手动添加", summary = "按平台名称或首字母查找", onClick = onManual)
            }
        }
        if (state.pending.isNotEmpty()) {
            item(key = "pending_title") {
                PlatformSectionTitle("手动添加 · ${state.pending.size}", Modifier.padding(top = BadgerSpacing.md))
            }
            items(state.pending.entries.toList(), key = { "pending_${it.key}" }, contentType = { "platform" }) { (key, entry) ->
                PlatformPreviewRow(key, entry, modifier = platformItemMotion(), onRemove = { onRemovePending(key) })
            }
        }
        if (state.candidates.isNotEmpty()) {
            item(key = "results_title") {
                PlatformSectionTitle("识别结果", Modifier.padding(top = BadgerSpacing.md))
            }
            items(state.candidates, key = { "line_${it.lineNumber}" },
                contentType = { if (it.message == null) "platform" else "issue" }) { candidate ->
                PlatformCandidateRow(candidate, platformItemMotion())
            }
        }
    }
}

@Composable
private fun AutomaticPlatformInput(state: AddPlatformUiState, onInputChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
            Box(Modifier.size(BadgerSize.coverSmallAvatarSize)
                .background(MiuixTheme.colorScheme.surfaceContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
            }
            Text("自动获取", style = MiuixTheme.textStyles.title4, modifier = Modifier.semantics { heading() })
        }
        TextField(
            value = state.input,
            onValueChange = onInputChange,
            enabled = !state.isSaving,
            label = "账号或主页链接",
            minLines = AUTOMATIC_MIN_LINES,
            maxLines = AUTOMATIC_MAX_LINES,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("每行一条，账号需注明平台，如 QQ：123456", style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun PlatformCandidateRow(candidate: PlatformCandidate, modifier: Modifier) {
    val key = candidate.fieldKey
    if (key != null && candidate.entry != null && candidate.message == null) {
        PlatformPreviewRow(key, candidate.entry, modifier)
    } else {
        Row(modifier.fillMaxWidth().heightIn(min = BadgerSize.coverIconSize)
            .padding(vertical = BadgerSpacing.sm).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(BadgerSize.coverSmallAvatarSize), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MiuixTheme.colorScheme.error)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
                Text(
                    text = "第 ${candidate.lineNumber} 行${key?.let { " · ${FIELD_DEF_MAP[it]?.displayName ?: it}" }.orEmpty()}",
                    style = MiuixTheme.textStyles.body1,
                )
                Text(candidate.message ?: "未识别平台", style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun PlatformPreviewRow(
    fieldKey: String,
    entry: PlatformEntry,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = BadgerSize.coverIconSize)
            .padding(vertical = BadgerSpacing.sm).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)
    ) {
        PlatformListAvatar(fieldKey)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs)) {
            Text(FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey, style = MiuixTheme.textStyles.body1)
            entry.displayName?.let { Text(it, style = MiuixTheme.textStyles.body2) }
            Text(entry.value ?: entry.jumpLink, style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        onRemove?.let {
            IconButton(onClick = it, modifier = Modifier.size(BadgerSize.minimumTouchTarget)) {
                Icon(Icons.Default.Close, contentDescription = "移除待添加的${FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey}")
            }
        }
    }
}

@Composable
internal fun PlatformListAvatar(fieldKey: String) {
    // 行文字已经给出平台名称，避免读屏重复朗读内部英文 key。
    Box(Modifier.clearAndSetSemantics {}) {
        PlatformIcon(fieldKey, MiuixTheme.colorScheme.primary,
            sizeDp = BadgerSize.coverSmallAvatarSize.value, shape = CircleShape)
    }
}

@Composable
internal fun PlatformSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier.semantics { heading() })
}

@Composable
internal fun PlatformInputError(message: String, modifier: Modifier = Modifier) {
    Text(message, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun ManualPlatformForm(
    state: AddPlatformUiState,
    onAccountChange: (String) -> Unit,
    onAuxiliaryChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onCustomNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val def = FIELD_DEF_MAP[state.selectedKey]
    LazyColumn(modifier, contentPadding = PaddingValues(top = BadgerSpacing.lg + BadgerSpacing.scrollContentTopGap, bottom = BadgerSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.lg)) {
        item {
            if (def == null) {
                TextField(state.customName, onCustomNameChange, label = "平台名称",
                    enabled = !state.isSaving, modifier = Modifier.fillMaxWidth())
            } else {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
                    PlatformListAvatar(def.fieldKey)
                    Text(def.displayName, style = MiuixTheme.textStyles.title4)
                }
            }
        }
        item {
            TextField(state.account, onAccountChange, label = def?.inputHint ?: "账号或主页链接",
                enabled = !state.isSaving, modifier = Modifier.fillMaxWidth())
        }
        if (def?.linkSource == LinkSource.LINK_ONLY && def.fieldKey != "website") {
            item {
                TextField(state.auxiliaryAccount, onAuxiliaryChange,
                    label = "${def.displayName}号（选填）", enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            TextField(state.displayName, onNameChange, label = "平台昵称（选填）",
                enabled = !state.isSaving, modifier = Modifier.fillMaxWidth())
        }
        if (def?.linkSource == LinkSource.NO_LINK) {
            item { Text("账号仅供复制后搜索", style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        }
    }
}
