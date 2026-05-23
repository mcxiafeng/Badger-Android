package top.mcxiafeng.badger.pages.person.contact

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
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 同步选项底部弹窗
 *
 * 用于设置平台头像时，让用户选择同步哪些内容（名字、头像）
 *
 * @param platformInfo 平台信息（平台名, PlatformEntry）
 * @param currentProfile 当前用户资料
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调（是否同步名字, 是否同步头像）
 */
@Composable
internal fun SyncOptionsBottomSheet(
    platformInfo: Pair<String, PlatformEntry>,
    currentProfile: UserProfile?,
    onDismiss: () -> Unit,
    onConfirm: (syncName: Boolean, syncAvatar: Boolean) -> Unit
) {
    val (platformName, entry) = platformInfo
    val displayName = FIELD_DEF_MAP[platformName]?.displayName ?: platformName
    
    // 默认两个都勾选
    var syncName by remember { mutableStateOf(true) }
    var syncAvatar by remember { mutableStateOf(true) }
    
    // 检查是否有可同步的名字
    val hasDisplayName = !entry.displayName.isNullOrBlank()
    // 检查是否有可同步的头像
    val hasAvatar = !entry.avatarUrl.isNullOrBlank()
    // 有跳转链接则始终可尝试同步（即使当前无数据，同步时会走网络解析）
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
            // 平台信息提示
            Text(
                text = "从 $displayName 同步以下信息：",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 同步名字选项
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
            
            // 同步头像选项
            if (hasAvatar) {
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
            
            // 如果两个都没有可同步的内容且无法网络获取
            if (!hasDisplayName && !hasAvatar && !canAttemptSync) {
                Text(
                    text = "该平台没有可同步的信息",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else if (!hasDisplayName && !hasAvatar) {
                // 当前无数据但可尝试网络获取
                Text(
                    text = "将从网络获取该平台的昵称和头像",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 底部按钮：取消、确认两排顶满宽度
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
                            // 当前无数据但可网络获取，两个都同步
                            onConfirm(true, true)
                        } else {
                            onConfirm(syncName && hasDisplayName, syncAvatar && hasAvatar)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}