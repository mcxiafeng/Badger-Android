package top.mcxiafeng.badger.pages.person.contact.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ocr.LinkSource
import top.mcxiafeng.badger.ocr.PlatformFieldDef
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.ocr.isUrlInput
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft

@Composable
internal fun EditForm(
    fieldKey: String,
    fieldDef: PlatformFieldDef?,
    isCustomMode: Boolean,
    customPlatformName: String,
    mainInput: String,
    auxiliaryInput: String,
    displayName: String,
    resolvedJumpLink: String,
    errorMessage: String?,
    infoMessage: String?,
    isSaving: Boolean,
    onMainInputChange: (String) -> Unit,
    onAuxiliaryInputChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onResolvedJumpLinkChange: (String) -> Unit,
    onErrorMessageChange: (String?) -> Unit = {},
    onDismiss: () -> Unit = {},
    onSave: () -> Unit
) {
    
    val platformName = if (isCustomMode) customPlatformName else (fieldDef?.displayName ?: fieldKey)
    Text(
        text = "平台",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = platformName,
        style = MiuixTheme.textStyles.body1,
        color = MiuixTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(8.dp))

    
    Text(
        text = "昵称",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = "平台昵称（选填）",
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    
    val idLabel = fieldDef?.inputHint?.let { hint ->
        if (hint.contains("或")) hint.substringBefore("或").trim() else hint
    } ?: "账号/ID"
    Text(
        text = idLabel,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = mainInput,
        onValueChange = {
            onMainInputChange(it)
            
            if (fieldDef != null && !isUrlInput(it)) {
                val link = buildPlatformLink(fieldKey, it.trim())
                onResolvedJumpLinkChange(link)
            } else if (isUrlInput(it)) {
                
                onResolvedJumpLinkChange(it.trim())
            }
        },
        label = idLabel,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    
    Text(
        text = "主页链接",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = resolvedJumpLink,
        onValueChange = { onResolvedJumpLinkChange(it) },
        label = "主页链接（可修改）",
        modifier = Modifier.fillMaxWidth()
    )

    
    if (fieldDef?.linkSource == LinkSource.LINK_ONLY && auxiliaryInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${fieldDef.displayName}号（仅供App内搜索）",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = auxiliaryInput,
            onValueChange = onAuxiliaryInputChange,
            label = "仅供App内手动搜索",
            modifier = Modifier.fillMaxWidth()
        )
    }

    
    if (fieldKey == "wechat" && mainInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "微信号无法自动跳转，对方需要手动搜索添加",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }

    
    errorMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
    }
    infoMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
    }

    Spacer(modifier = Modifier.height(16.dp))
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            text = "取消",
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            enabled = !isSaving
        )
        Spacer(Modifier.width(20.dp))
        Button(
            onClick = {
                if (isSaving) return@Button
                
                if (mainInput.isBlank() && resolvedJumpLink.isBlank()) {
                    onErrorMessageChange("请输入账号或链接，如需删除请使用删除功能")
                    return@Button
                }
                onSave()
            },
            modifier = Modifier.weight(1f),
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text(text = "保存")
        }
    }
}

@Composable
internal fun PlatformForm(
    fieldDef: PlatformFieldDef?,
    mainInput: String,
    auxiliaryInput: String,
    displayName: String,
    errorMessage: String?,
    infoMessage: String?,
    onMainInputChange: (String) -> Unit,
    onAuxiliaryInputChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    fieldKey: String,
    onResolvedJumpLink: (String) -> Unit,
    onResolvedOriginalLink: (String) -> Unit,
    onResolvedValue: (String?) -> Unit,
    onInfoMessage: (String?) -> Unit,
) {
    if (fieldDef == null) return

    val linkSource = fieldDef.linkSource

    
    val mainLabel = fieldDef.inputHint

    
    Text(
        text = fieldDef.displayName,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = mainInput,
        onValueChange = { input ->
            onMainInputChange(input)
            onInfoMessage(null)

            val isUrl = isUrlInput(input)

            
            if (isUrl) {
                
                onResolvedJumpLink("")
                onResolvedOriginalLink(input.trim())
                onResolvedValue(input.trim())
            } else when (linkSource) {
                LinkSource.AUTO -> {
                    if (input.isNotBlank()) {
                        
                        val link = buildPlatformLink(fieldKey, input.trim())
                        onResolvedJumpLink(link)
                        onResolvedOriginalLink("")
                        onResolvedValue(input.trim())
                    } else {
                        onResolvedJumpLink("")
                        onResolvedOriginalLink("")
                        onResolvedValue(null)
                    }
                }
                LinkSource.LINK_ONLY -> {
                    
                    onResolvedJumpLink("")
                    onResolvedOriginalLink("")
                    onResolvedValue(input.trim())
                }
                LinkSource.NO_LINK -> {
                    
                    onResolvedJumpLink("")
                    onResolvedOriginalLink("")
                    onResolvedValue(input.trim())
                }
            }
        },
        label = mainLabel,
        modifier = Modifier.fillMaxWidth()
    )

    
    if (linkSource == LinkSource.LINK_ONLY) {
        Spacer(modifier = Modifier.height(4.dp))
        if (!isUrlInput(mainInput) && mainInput.isNotBlank()) {
            Text(
                text = "${fieldDef.displayName}号仅供App内搜索，请粘贴主页链接生成跳转二维码",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        } else if (isUrlInput(mainInput)) {
            Text(
                text = "请在${fieldDef.displayName}App中复制主页链接后粘贴",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }

    
    if (fieldKey == "wechat" && mainInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "微信号/手机号无法生成跳转链接，他人需复制后手动搜索添加",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }

    
    if (linkSource == LinkSource.AUTO && mainInput.isNotBlank() && !isUrlInput(mainInput)) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "主页链接（自动生成，可修改）",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        val autoLink = buildPlatformLink(fieldKey, mainInput.trim())
        Text(
            text = autoLink,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }

    
    if (linkSource == LinkSource.LINK_ONLY) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${fieldDef.displayName}号（仅供App内手动搜索，可选）",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = auxiliaryInput,
            onValueChange = onAuxiliaryInputChange,
            label = "对方可在${fieldDef.displayName}App搜索此号找到你",
            modifier = Modifier.fillMaxWidth()
        )
    }

    
    infoMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
    }

    
    errorMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
    }
}

@Composable
internal fun CustomPlatformForm(
    customPlatformName: String,
    onCustomPlatformNameChange: (String) -> Unit,
    mainInput: String,
    onMainInputChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    errorMessage: String?,
) {
    Text(
        text = "平台名称",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = customPlatformName,
        onValueChange = onCustomPlatformNameChange,
        label = "如：Discord、Instagram",
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "账号或链接",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = mainInput,
        onValueChange = onMainInputChange,
        label = "平台账号或主页链接",
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "昵称（选填）",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = "平台昵称",
        modifier = Modifier.fillMaxWidth()
    )

    errorMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
    }
}
