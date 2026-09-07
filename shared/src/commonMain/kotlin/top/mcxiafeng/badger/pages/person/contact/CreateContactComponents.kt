package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.network.IdentifyResponse
import top.mcxiafeng.badger.ocr.PlatformFieldDef
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft

/**
 * 自动获取模式内容（U18 自 CreateContactPage 下沉）。
 */
@Composable
internal fun AutoFetchModeContent(
    isGridPhase: Boolean,
    selectedFieldKey: String,
    selectedDef: PlatformFieldDef?,
    addableDefs: List<PlatformFieldDef>,
    mainInput: String,
    isResolving: Boolean,
    resolveError: String?,
    resolved: IdentifyResponse?,
    previewImageBitmap: ImageBitmap?,
    editableName: String,
    isCreating: Boolean,
    onGridSelect: (String) -> Unit,
    onCustomSelect: () -> Unit,
    onInputChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onResolve: () -> Unit,
    onCreate: () -> Unit,
    onBackToGrid: () -> Unit,
) {
    if (isGridPhase) {
        Text(
            text = "选择平台后粘贴链接或 ID",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        top.mcxiafeng.badger.pages.person.contact.dialogs.PlatformGridSelector(
            defs = addableDefs,
            existingPlatformKeys = emptySet(),
            onSelect = onGridSelect,
            onCustom = onCustomSelect,
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            IconButton(onClick = onBackToGrid) {
                Icon(
                    imageVector = Lucide.ArrowLeft,
                    contentDescription = "返回平台选择",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = selectedDef?.displayName ?: selectedFieldKey,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        TextField(
            value = mainInput,
            onValueChange = onInputChange,
            label = selectedDef?.inputHint ?: "链接或 ID",
            modifier = Modifier.fillMaxWidth(),
        )
        if (resolveError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = resolveError,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (resolved == null) {
            Button(
                onClick = onResolve,
                modifier = Modifier.fillMaxWidth(),
                enabled = mainInput.isNotBlank() && !isResolving,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (isResolving) {
                    CircularProgressIndicator(
                        size = 18.dp,
                        strokeWidth = 2.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = MiuixTheme.colorScheme.onPrimary,
                            backgroundColor = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                        ),
                    )
                } else {
                    Text(text = "解析")
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            ResolvePreviewRow(
                bio = resolved.signature?.takeIf { it.isNotBlank() },
                avatarBitmap = previewImageBitmap,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = editableName,
                onValueChange = onNameChange,
                label = "姓名",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onBackToGrid,
                    modifier = Modifier.weight(1f),
                    enabled = !isCreating,
                )
                Button(
                    onClick = onCreate,
                    modifier = Modifier.weight(1f),
                    enabled = editableName.trim().isNotBlank() && !isCreating,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            size = 18.dp,
                            strokeWidth = 2.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.onPrimary,
                                backgroundColor = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                            ),
                        )
                    } else {
                        Text(text = "创建")
                    }
                }
            }
        }
    }
}

/**
 * 解析结果预览：头像 + 简介（姓名可编辑，不在此处展示）。
 */
@Composable
internal fun ResolvePreviewRow(
    bio: String?,
    avatarBitmap: ImageBitmap?,
) {
    val hasContent = !bio.isNullOrBlank() || avatarBitmap != null
    if (!hasContent) {
        Text(
            text = "未解析到可预览的信息（简介 / 头像）",
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap,
                contentDescription = "头像预览",
                modifier = Modifier.size(56.dp).clip(CircleShape),
            )
            Spacer(modifier = Modifier.size(12.dp))
        }
        if (!bio.isNullOrBlank()) {
            Text(
                text = bio,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
