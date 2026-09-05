package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.network.PlatformAdapterRegistry
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.mcxiafeng.badger.utils.Methods
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.pages.person.contact.detail.LongPressArrowPreference
import top.mcxiafeng.badger.pages.person.contact.detail.ToolbarAction
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.User
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.platform.showToast
import top.mcxiafeng.badger.platform.ImageFiles
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

/**
 * 用户名片详情页内容组件。
 */
@Composable
internal fun UserProfileDetailContent(
    isLoading: Boolean,
    profile: UserProfile?,
    platformFields: List<Pair<String, PlatformEntry>>,
    avatarVersion: Int,
    contentModifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    onAvatarClick: () -> Unit,
    onEditNameClick: () -> Unit = {},
    onPlatformClick: (String, PlatformEntry) -> Unit,
    onPlatformLongClick: (String, PlatformEntry) -> Unit,
    onAddPlatformClick: () -> Unit,
    // [A5] 基础信息字段编辑入口
    onBasicInfoCellClick: (String, String?) -> Unit = { _, _ -> },
    onBackgroundUrlClick: () -> Unit = {},
    // [A6] 从平台解析导入入口
    onImportFromPlatformClick: () -> Unit = {},
) {
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(contentModifier),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 32.dp + LocalFloatingBarBottomPadding.current
            )
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var avatarImageBitmap by remember(profile?.avatarPath, avatarVersion) {
                        mutableStateOf<ImageBitmap?>(null)
                    }
                    val localAvatarPath = profile?.avatarPath
                    LaunchedEffect(localAvatarPath, avatarVersion) {
                        avatarImageBitmap = ImageFiles.loadImageBytes(localAvatarPath)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
                    }

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable { onAvatarClick() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val avBmp = avatarImageBitmap
                            if (avBmp != null) {
                                Image(
                                    bitmap = avBmp,
                                    contentDescription = "头像",
                                    modifier = Modifier.size(80.dp)
                                )
                            } else {
                                val name = profile?.name ?: ""
                                Text(
                                    text = name.take(1).ifBlank { "?" },
                                    style = MiuixTheme.textStyles.title1,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Camera,
                                contentDescription = "更换头像",
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // [修复防御]: 编辑入口已迁到这里 —— 点击名字/简介即可触发编辑 dialog。
                    // 用 Modifier.clickable 而非 CombinedClickable（无需长按），
                    // 整个 name+bio 区为一个 clickable 区，用户点哪里都能进入编辑。
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { onEditNameClick() }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                    ) {
                        Text(
                            text = profile?.name ?: "未设置",
                            style = MiuixTheme.textStyles.title1,
                        )

                        val currentProfile = profile
                        if (currentProfile != null && !currentProfile.bio.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentProfile.bio!!,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                    }
                }
            }

            item(key = "platform_long_press_hint") {
                FirstTimeHint(
                    text = "长按社交平台可复制/编辑/同步",
                    hintKey = "long_press_platform",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            item(key = "basic_info") {
                // [A5] 基础信息编辑区：性别/生日/国家/地区，点击进入对应 picker
                SmallTitle(text = "基本信息")
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    ArrowPreference(
                        title = "性别",
                        summary = profile?.sex?.takeIf { it.isNotBlank() } ?: "男 / 女 / 其他",
                        onClick = { onBasicInfoCellClick("gender", profile?.sex) }
                    )
                    ArrowPreference(
                        title = "生日",
                        summary = profile?.birthday?.takeIf { it.isNotBlank() } ?: "未设置",
                        onClick = { onBasicInfoCellClick("birthday", profile?.birthday) }
                    )
                    ArrowPreference(
                        title = "国家",
                        summary = profile?.country?.takeIf { it.isNotBlank() } ?: "未设置",
                        onClick = { onBasicInfoCellClick("country", profile?.country) }
                    )
                    ArrowPreference(
                        title = "地区",
                        summary = profile?.region?.takeIf { it.isNotBlank() } ?: "未设置",
                        onClick = { onBasicInfoCellClick("region", profile?.region) }
                    )
                    ArrowPreference(
                        title = "背景图",
                        summary = profile?.backgroundURL?.takeIf { it.isNotBlank() } ?: "未设置",
                        onClick = onBackgroundUrlClick
                    )
                    // [A6] 从平台解析导入：选平台 + 粘贴链接/ID → 解析 → 预览 → 保存
                    ArrowPreference(
                        title = "从平台导入",
                        summary = "从社交平台解析昵称/简介/头像",
                        onClick = onImportFromPlatformClick
                    )
                }
            }
            item(key = "platforms") {
                SmallTitle(text = "社交平台")
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    platformFields.forEach { (fieldKey, entry) ->
                        val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                        val summary = buildString {
                            if (!entry.displayName.isNullOrBlank()) {
                                append(entry.displayName)
                                if (!entry.value.isNullOrBlank()) {
                                    append("（${entry.value}）")
                                }
                            } else if (!entry.value.isNullOrBlank()) {
                                append(entry.value)
                            } else {
                                append(entry.jumpLink)
                            }
                        }
                        LongPressArrowPreference(
                            title = displayName,
                            summary = summary,
                            onClick = { onPlatformClick(fieldKey, entry) },
                            onLongClick = { onPlatformLongClick(fieldKey, entry) }
                        )
                    }
                    ArrowPreference(
                        title = "添加社交平台",
                        summary = "添加你的社交账号",
                        onClick = {
                                                        onAddPlatformClick()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 用户名片社交平台长按浮动工具栏。
 */
@Composable
internal fun UserProfileFloatingToolbar(
    show: Boolean,
    selectedPlatform: Pair<String, PlatformEntry>?,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onSync: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val currentPlatform = selectedPlatform ?: return
    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        val (fieldKey, pEntry) = currentPlatform
        val pDisplayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
        Box(modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current)) {
            FloatingToolbar(cornerRadius = 16.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    ToolbarAction(
                        icon = Lucide.Copy,
                        label = "复制",
                        onClick = onCopy
                    )
                    ToolbarAction(
                        icon = Lucide.Pencil,
                        label = "编辑",
                        onClick = onEdit
                    )
                    if (onSync != null) {
                        ToolbarAction(
                            icon = Lucide.User,
                            label = "同步信息",
                            onClick = onSync
                        )
                    }
                    ToolbarAction(
                        icon = Lucide.Trash2,
                        label = "删除",
                        tint = MiuixTheme.colorScheme.error,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}
