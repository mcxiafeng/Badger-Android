package top.mcxiafeng.badger.pages.person.contact

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.IdentifyResponse
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "ImportFromPlatform"

/**
 * [A6] 从平台解析导入我的名片。
 *
 * 流程：平台网格选择 → 粘贴 URL/ID → 调 [ContactNetworkResolver.identify]（统一走
 * `POST /api/resolve/`）→ 预览解析到的 name/bio/avatar → 用户确认后把非空字段
 * 写入我的名片（[onConfirm] 上传入已落盘的 avatar 路径 + name + bio，由调用方持久化）。
 *
 * [修复防御]: 解析失败 / 空结果 / 网络异常均显式降级为错误态 + 可重试，不静默吞错；
 * 服务端返回的 name=="未知" 视为无效昵称过滤掉，避免污染用户名片。
 */
@Composable
fun ImportFromPlatformDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String?, bio: String?, avatarPath: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // [Phase 4 剩余] 复用平台清单（服务端驱动，离线兜底本地）。
    val manifestRepo = remember { KoinComponentBy.get<PlatformManifestRepository>() }
    val addableDefs by manifestRepo.addable.collectAsState()
    LaunchedEffect(show) { if (show) manifestRepo.ensureLoaded() }

    // Phase 状态：true=平台网格, false=输入/预览
    var isGridPhase by remember { mutableStateOf(true) }
    var selectedFieldKey by remember { mutableStateOf("") }

    // 输入 / 解析态
    var mainInput by remember { mutableStateOf("") }
    var isResolving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var resolved by remember { mutableStateOf<IdentifyResponse?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isApplying by remember { mutableStateOf(false) }

    val selectedDef = remember(selectedFieldKey, addableDefs) {
        addableDefs.firstOrNull { it.fieldKey == selectedFieldKey }
            ?: FIELD_DEF_MAP[selectedFieldKey]
    }

    // 解析成功后惰性下载头像用于预览（仅展示，落盘在 onConfirm 内完成）。
    LaunchedEffect(resolved?.avatarUrl) {
        val url = resolved?.avatarUrl?.takeIf { it.isNotBlank() }
        previewBitmap = if (url != null) {
            withContext(Dispatchers.IO) { HttpUtil.downloadBitmap(url) }
        } else null
    }

    if (!show) return
    WindowDialog(
        show = true,
        title = if (isGridPhase) "从平台导入" else "导入 ${selectedDef?.displayName ?: ""}",
        summary = if (isGridPhase) "选择平台后粘贴链接或 ID" else null,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (isGridPhase) {
                PlatformGridSelector(
                    defs = addableDefs,
                    existingPlatformKeys = emptySet(),
                    onSelect = { fieldKey ->
                        selectedFieldKey = fieldKey
                        isGridPhase = false
                        mainInput = ""
                        resolved = null
                        resolveError = null
                        previewBitmap = null
                    },
                    onCustom = {
                        // 自定义平台无可靠识别，仍允许：走 weblink 通用识别
                        selectedFieldKey = "website"
                        isGridPhase = false
                        mainInput = ""
                        resolved = null
                        resolveError = null
                        previewBitmap = null
                    },
                )
            } else {
                // 返回按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    IconButton(onClick = {
                        isGridPhase = true
                        selectedFieldKey = ""
                        resolved = null
                        resolveError = null
                        previewBitmap = null
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "导入 ${selectedDef?.displayName ?: ""}",
                        style = MiuixTheme.textStyles.title3
                    )
                }

                TextField(
                    value = mainInput,
                    onValueChange = { mainInput = it; resolveError = null },
                    label = "链接或 ID",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (resolveError != null) {
                    Text(
                        text = resolveError!!,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote2,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }

                // 解析按钮 / 预览区
                if (resolved == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !isResolving
                        )
                        Button(
                            onClick = {
                                val input = mainInput.trim()
                                if (input.isBlank()) {
                                    resolveError = "请输入链接或 ID"
                                    return@Button
                                }
                                isResolving = true
                                resolveError = null
                                scope.launch(Dispatchers.IO) {
                                    val resp = ContactNetworkResolver.identify(input)
                                    withContext(Dispatchers.Main) {
                                        isResolving = false
                                        if (resp == null) {
                                            resolveError = "解析失败，请检查链接或网络"
                                        } else {
                                            resolved = resp
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isResolving && !isApplying,
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    size = 18.dp,
                                    strokeWidth = 2.dp,
                                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                        foregroundColor = MiuixTheme.colorScheme.onPrimary,
                                        backgroundColor = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                                    )
                                )
                            } else {
                                Text(text = "解析")
                            }
                        }
                    }
                } else {
                    // 预览解析结果
                    Spacer(modifier = Modifier.height(12.dp))
                    PreviewRow(
                        name = resolved!!.name?.takeIf { it.isNotBlank() && it != "未知" },
                        bio = resolved!!.signature?.takeIf { it.isNotBlank() },
                        avatarBitmap = previewBitmap,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !isApplying
                        )
                        Button(
                            onClick = {
                                val name = resolved!!.name?.takeIf { it.isNotBlank() && it != "未知" }
                                val bio = resolved!!.signature?.takeIf { it.isNotBlank() }
                                val avatarUrl = resolved!!.avatarUrl?.takeIf { it.isNotBlank() }
                                isApplying = true
                                scope.launch(Dispatchers.IO) {
                                    // [修复防御]: 头像先落盘再回传路径，与现有 sync/裁剪同策略；
                                    // 下载失败则保留原有头像，不阻断 name/bio 写入。
                                    var avatarPath: String? = null
                                    if (avatarUrl != null) {
                                        val bmp = HttpUtil.downloadBitmap(avatarUrl)
                                        if (bmp != null) {
                                            avatarPath = Methods.saveBitmapAsAvatar(context, bmp, "user_avatar.webp")?.absolutePath
                                        } else {
                                            Log.w(TAG, "头像下载失败，仅写入 name/bio")
                                        }
                                    } else {
                                        Log.d(TAG, "解析结果无头像，仅写入 name/bio")
                                    }
                                    withContext(Dispatchers.Main) {
                                        isApplying = false
                                        onConfirm(name, bio, avatarPath)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isApplying,
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            if (isApplying) {
                                CircularProgressIndicator(
                                    size = 18.dp,
                                    strokeWidth = 2.dp,
                                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                        foregroundColor = MiuixTheme.colorScheme.onPrimary,
                                        backgroundColor = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                                    )
                                )
                            } else {
                                Text(text = "保存到我的名片")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 解析结果预览：头像 + 昵称 + 简介。
 */
@Composable
private fun PreviewRow(
    name: String?,
    bio: String?,
    avatarBitmap: Bitmap?,
) {
    val hasContent = !name.isNullOrBlank() || !bio.isNullOrBlank() || avatarBitmap != null
    if (!hasContent) {
        Text(
            text = "未解析到可导入的信息（昵称 / 简介 / 头像）",
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote2,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = "头像预览",
                modifier = Modifier.size(56.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!name.isNullOrBlank()) {
                Text(
                    text = name,
                    style = MiuixTheme.textStyles.title3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = bio,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
