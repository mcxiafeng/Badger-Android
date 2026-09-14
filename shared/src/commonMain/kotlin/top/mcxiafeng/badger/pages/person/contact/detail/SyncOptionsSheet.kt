package top.mcxiafeng.badger.pages.person.contact.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun SyncOptionsBottomSheet(
    platformInfo: Pair<String, PlatformEntry>,
    currentProfile: UserProfile?,
    onDismiss: () -> Unit,
    onConfirm: (syncName: Boolean, syncAvatar: Boolean) -> Unit
) {
    val (platformName, entry) = platformInfo
    val displayName = FIELD_DEF_MAP[platformName]?.displayName ?: platformName
    
    
    var syncName by remember { mutableStateOf(true) }
    var syncAvatar by remember { mutableStateOf(true) }
    
    
    val hasDisplayName = !entry.displayName.isNullOrBlank()
    
    val hasAvatar = !entry.avatarUrl.isNullOrBlank()
    
    val canAttemptSync = entry.jumpLink.isNotBlank()
    
    WindowBottomSheet(
        show = true,
        title = "同步设置",
        onDismissRequest = onDismiss,
        defaultWindowInsetsPadding = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            
            Text(
                text = "从 $displayName 同步以下信息：",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            
            if (hasDisplayName) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { syncName = !syncName }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        state = if (syncName) ToggleableState.On else ToggleableState.Off,
                        onClick = { syncName = !syncName }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "同步昵称",
                            style = MiuixTheme.textStyles.body1
                        )
                        Text(
                            text = entry.displayName ?: "",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }
            
            
            if (hasAvatar || canAttemptSync) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { syncAvatar = !syncAvatar }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        state = if (syncAvatar) ToggleableState.On else ToggleableState.Off,
                        onClick = { syncAvatar = !syncAvatar }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "同步头像",
                            style = MiuixTheme.textStyles.body1
                        )
                        Text(
                            text = "将使用 $platformName 的头像",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }
            
            
            if (!hasDisplayName && !hasAvatar && !canAttemptSync) {
                Text(
                    text = "该平台没有可同步的信息",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else if (!hasDisplayName && !hasAvatar) {
                
                Text(
                    text = "将从网络获取该平台的昵称和头像",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确认",
                    onClick = {
                        if (canAttemptSync && !hasDisplayName && !hasAvatar) {
                            
                            onConfirm(true, true)
                        } else {
                            
                            onConfirm(syncName && hasDisplayName, syncAvatar && (hasAvatar || canAttemptSync))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}