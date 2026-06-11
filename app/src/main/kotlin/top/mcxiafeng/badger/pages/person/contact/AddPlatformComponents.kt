package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.network.LinkResolver
import top.mcxiafeng.badger.ocr.LinkSource
import top.mcxiafeng.badger.ocr.PlatformFieldDef
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 编辑模式表单（从 AddPlatformDialog 提取）
 */
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
    val scope = rememberCoroutineScope()

    // 平台名（只读展示）
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

    // 昵称
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

    // ID / 账号
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
            // 编辑模式下同步更新 jumpLink
            if (fieldDef != null && !it.startsWith("http")) {
                val link = buildPlatformLink(fieldKey, it.trim())
                onResolvedJumpLinkChange(link)
            }
        },
        label = idLabel,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    // 主页链接
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

    // LINK_ONLY 平台辅助字段
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

    // 微信特殊提示
    if (fieldKey == "wechat" && mainInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "微信号无法自动跳转，对方需要手动搜索添加",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }

    // 错误提示
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
                // 编辑模式下不允许保存空内容（否则会导致删除该平台）
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

/**
 * 预设平台表单（按 LinkSource 分型）
 */
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
    scope: CoroutineScope,
    fieldKey: String,
    onResolvedJumpLink: (String) -> Unit,
    onResolvedOriginalLink: (String) -> Unit,
    onResolvedValue: (String?) -> Unit,
    onInfoMessage: (String?) -> Unit,
) {
    if (fieldDef == null) return

    val linkSource = fieldDef.linkSource

    // 主输入框提示
    val mainLabel = fieldDef.inputHint

    // 主输入框
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

            val isUrl = input.startsWith("http://") || input.startsWith("https://")

            when (linkSource) {
                LinkSource.AUTO -> {
                    if (!isUrl && input.isNotBlank()) {
                        // 填账号 → 自动生成链接
                        val link = buildPlatformLink(fieldKey, input.trim())
                        onResolvedJumpLink(link)
                        onResolvedOriginalLink("")
                        onResolvedValue(input.trim())
                    } else if (isUrl) {
                        // 粘贴链接 → 异步解析
                        scope.launch(Dispatchers.IO) {
                            val result = LinkResolver.resolve(fieldKey, input.trim())
                            withContext(Dispatchers.Main) {
                                onResolvedJumpLink(result.jumpLink)
                                onResolvedOriginalLink(result.originalLink)
                                onResolvedValue(result.value)
                                if (result.displayName != null) onDisplayNameChange(result.displayName)
                                if (result.errorMessage != null) onInfoMessage(result.errorMessage)
                            }
                        }
                    } else {
                        onResolvedJumpLink("")
                        onResolvedOriginalLink("")
                        onResolvedValue(null)
                    }
                }
                LinkSource.LINK_ONLY -> {
                    if (isUrl) {
                        // 粘贴链接 → 异步解析
                        scope.launch(Dispatchers.IO) {
                            val result = LinkResolver.resolve(fieldKey, input.trim())
                            withContext(Dispatchers.Main) {
                                onResolvedJumpLink(result.jumpLink)
                                onResolvedOriginalLink(result.originalLink)
                                onResolvedValue(result.value)
                                if (result.displayName != null) onDisplayNameChange(result.displayName)
                                if (result.errorMessage != null) onInfoMessage(result.errorMessage)
                            }
                        }
                    } else {
                        // 非 URL 输入（抖音号/小红书号） → 不生成链接
                        onResolvedJumpLink("")
                        onResolvedOriginalLink("")
                        onResolvedValue(input.trim())
                    }
                }
                LinkSource.NO_LINK -> {
                    // 微信：存 ID，不生成链接
                    onResolvedJumpLink("")
                    onResolvedOriginalLink("")
                    onResolvedValue(input.trim())
                }
            }
        },
        label = mainLabel,
        modifier = Modifier.fillMaxWidth()
    )

    // LINK_ONLY 提示
    if (linkSource == LinkSource.LINK_ONLY) {
        Spacer(modifier = Modifier.height(4.dp))
        if (!mainInput.startsWith("http") && mainInput.isNotBlank()) {
            Text(
                text = "${fieldDef.displayName}号仅供App内搜索，请粘贴主页链接生成跳转二维码",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        } else if (mainInput.startsWith("http")) {
            Text(
                text = "请在${fieldDef.displayName}App中复制主页链接后粘贴",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }

    // 微信特殊提示
    if (fieldKey == "wechat" && mainInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "微信号/手机号无法生成跳转链接，他人需复制后手动搜索添加",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }

    // AUTO 模式：显示自动生成的链接
    if (linkSource == LinkSource.AUTO && mainInput.isNotBlank() && !mainInput.startsWith("http")) {
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

    // LINK_ONLY 平台辅助字段（抖音号/小红书号）
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

    // 解析提示
    infoMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
    }

    // 错误提示
    errorMessage?.let { msg ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = msg, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
    }
}

/**
 * 自定义平台表单
 */
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
