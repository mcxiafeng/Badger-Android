package top.mcxiafeng.badger.pages.person.contact

import top.mcxiafeng.badger.ui.glass.scrollTopFade
import top.mcxiafeng.badger.ui.glass.springOverscroll

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.components.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider as Divider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import top.mcxiafeng.badger.ui.glass.GlassActionText
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import top.mcxiafeng.badger.ui.glass.buttonGlass
import top.mcxiafeng.badger.ui.glass.glassPressFeedback
import top.mcxiafeng.badger.ui.glass.rememberButtonTouchFeedback
import top.mcxiafeng.badger.ui.glass.buttonPressContentAlpha
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import top.mcxiafeng.badger.ui.glass.buttonGlassOrBackground
import top.mcxiafeng.badger.ui.glass.glassButtonForeground

/**
 * 自绘水平分割线(0.5dp,使用主题 dividerLine 颜色)。
 *
 * 用自绘不用 Miuix HorizontalDivider 是因为后者在 Card 内经常渲染不出来。
 */
@Composable
internal fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine)
    )
}

/**
 * 联系人详情页内容组件。提取自 ContactDetailPage 以减少单文件体积。
 *
 * "我的名片"编辑入口也经 ContactDetailPage 使用本组件，数据由调用方按 contactId 提供。
 * [showBasicInfo]/[showTags]/[showCreateTime] 控制对应区块的可见性。
 */
@Composable
internal fun ContactDetailPageContent(
    isLoading: Boolean,
    contact: Contact?,
    contentModifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    avatarBitmap: Bitmap?,
    systemFields: List<ContactFieldDisplay>,
    customFields: List<ContactFieldDisplay>,
    platformFields: List<Pair<String, PlatformEntry>>,
    bio: String?,
    tags: List<Tag>,
    onAvatarClick: () -> Unit,
    onEditNameClick: () -> Unit,
    onFieldClick: (ContactFieldDisplay) -> Unit,
    onFieldLongPress: (ContactFieldDisplay) -> Unit,
    onPlatformClick: (String, PlatformEntry) -> Unit,
    onPlatformLongPress: (String, PlatformEntry) -> Unit,
    onAddPlatformClick: () -> Unit,
    onBioClick: () -> Unit,
    onTagsClick: () -> Unit,
    onAiTagsClick: () -> Unit = {},
    onBasicInfoCellClick: (fieldKey: String, currentValue: String?) -> Unit = { _, _ -> },
    showBasicInfo: Boolean = true,
    showTags: Boolean = true,
    showCreateTime: Boolean = true,
    selectedPlatformKey: String? = null,
    onDismissPlatformActions: () -> Unit = {},
    onPlatformCopy: () -> Unit = {},
    onPlatformEdit: () -> Unit = {},
    onPlatformSync: () -> Unit = {},
    onPlatformDelete: () -> Unit = {},
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
    } else if (contact == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text("联系人不存在", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollTopFade().springOverscroll()
                .then(contentModifier),
            contentPadding = PaddingValues(
                // [修复防御]: 删除头图后,需要让 LazyColumn 顶部避开 Scaffold 注入的
                // TopAppBar 区域(top 内含 status bar + TopAppBar 高度)。否则
                // "基础信息" 标题会被 TopAppBar 覆盖,只能看到从"国家"行附近开始。
                top = paddingValues.calculateTopPadding() + BadgerSpacing.scrollContentTopGap,
                bottom = paddingValues.calculateBottomPadding().coerceAtLeast(32.dp)
            )
        ) {
            // 上方：头像 + 姓名区域(从 d83b7f6 还原的 header 块)
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 头像(含相机图标提示)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable { onAvatarClick() }
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap.asImageBitmap(),
                                contentDescription = "头像",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.take(1),
                                    style = MiuixTheme.textStyles.title1,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                        // 相机图标覆盖层
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .buttonGlassOrBackground(12.dp, MiuixTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = "更换头像",
                                modifier = Modifier.size(14.dp),
                                tint = glassButtonForeground(MiuixTheme.colorScheme.onPrimary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val enabledGlass = LocalGlassStyle.current.buttonsEnabled
                    val feedback = rememberButtonTouchFeedback(enabledGlass)
                    Row(
                        modifier = Modifier
                            .glassPressFeedback(enabledGlass, 24.dp, feedback)
                            .clip(BadgerShapes.Capsule)
                            .buttonGlass(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = if (enabledGlass) null else LocalIndication.current,
                            ) { onEditNameClick() }
                            .buttonPressContentAlpha(feedback)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = contact.name,
                            color = glassButtonForeground(MiuixTheme.colorScheme.onBackground),
                            style = MiuixTheme.textStyles.title1,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑姓名",
                            modifier = Modifier.size(16.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                    if (!contact.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = contact.note,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }

            // ========== 基础信息 2x2 网格(PR2) ==========
            if (showBasicInfo) {
                item(key = "basic_info") {
                    BasicInfoCard(
                        fields = systemFields,
                        onCellClick = onBasicInfoCellClick,
                    )
                }
            }

            // 下方：联系方式分组（支持长按）
            // [修复防御]: 删掉"长按联系方式可复制/编辑"灰色 FirstTimeHint 提示语,
            // 整页只保留简洁卡片;卡片之间靠 SectionCard 自带 12dp bottom padding + Card 边框分隔。
            if (systemFields.any { it.fieldKey != null && it.fieldKey !in BASIC_INFO_FIELD_KEYS && it.fieldKey !in PLATFORM_FIELD_KEYS }) {
                item(key = "system_section") {
                    ContactFieldSection(
                        title = "联系方式",
                        fields = systemFields.filter {
                            it.fieldKey != null && it.fieldKey !in BASIC_INFO_FIELD_KEYS && it.fieldKey !in PLATFORM_FIELD_KEYS
                        },
                        onClick = onFieldClick,
                        onLongPress = onFieldLongPress,
                    )
                }
            }

            // 社交平台(老 UI 风格:同一 Card 放平台 + 添加,无外层 Box 叠加,
            // 行间用 ThinDivider() 视觉分隔,与扫描记录一致)
            if (platformFields.isNotEmpty()) {
                item(key = "platforms_section") {
                    SectionCard(title = "社交平台") {
                        // [修复防御]:每两个 LongPressArrowPreference 之间显式插入
                        // ThinDivider(),修复 SectionCard 拆 Box padding 后 border 消失、
                        // 多行视觉重叠的问题。
                        val totalRows = platformFields.size + 1 // 平台项 + "添加社交平台"
                        platformFields.forEachIndexed { index, (fieldKey, entry) ->
                            val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
                            val summary = buildString {
                                if (!entry.displayName.isNullOrBlank()) {
                                    append(entry.displayName)
                                    if (!entry.value.isNullOrBlank()) append("（${entry.value}）")
                                } else if (!entry.value.isNullOrBlank()) {
                                    append(entry.value)
                                } else {
                                    append(entry.jumpLink)
                                }
                            }
                            PlatformInfoRow(
                                fieldKey = fieldKey,
                                entry = entry,
                                displayName = displayName,
                                summary = summary,
                                selectedPlatformKey = selectedPlatformKey,
                                onClick = { onPlatformClick(fieldKey, entry) },
                                onLongClick = { onPlatformLongPress(fieldKey, entry) },
                                onDismissRequest = onDismissPlatformActions,
                                onCopy = onPlatformCopy,
                                onEdit = onPlatformEdit,
                                onSync = onPlatformSync,
                                onDelete = onPlatformDelete,
                            )
                            if (index < totalRows - 1) {
                                ThinDivider()
                            }
                        }
                        LongPressArrowPreference(
                            title = "添加社交平台",
                            summary = "添加对方的社交账号",
                            onClick = onAddPlatformClick,
                            onLongClick = { /* 无长按动作 */ },
                        )
                    }
                }
            } else {
                item(key = "platforms_add") {
                    SectionCard(title = "社交平台") {
                        LongPressArrowPreference(
                            title = "添加社交平台",
                            summary = "添加对方的社交账号",
                            onClick = onAddPlatformClick,
                            onLongClick = { },
                        )
                    }
                }
            }

            if (customFields.isNotEmpty()) {
                item(key = "custom_section") {
                    ContactFieldSection(
                        title = "自定义信息",
                        fields = customFields,
                        onClick = onFieldClick,
                        onLongPress = onFieldLongPress,
                    )
                }
            }

            // ========== 个人介绍 Section ==========
            // [修复防御]: bio 框固定 96dp 最小高度 + 四向 16dp padding,文字 Box 内垂直水平居中。
            // 空态只有"点击添加"四字可点(主色),其余灰字不可点;避免"整张卡片误点编辑"。
            item(key = "bio") {
                SectionCard(title = "个人介绍") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bio.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "该人暂无介绍，",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                GlassActionText("点击添加", onBioClick, style = MiuixTheme.textStyles.body2)
                            }
                        } else {
                            Text(
                                text = bio,
                                style = MiuixTheme.textStyles.body1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onBioClick() },
                            )
                        }
                    }
                }
            }

            // ========== 标签 Section ==========
            if (showTags) {
                item(key = "tags") {
                    ContactTagsCard(
                        tags = tags,
                        onTagsClick = onTagsClick,
                        onAiTagsClick = onAiTagsClick,
                    )
                }
            }

            if (showCreateTime) {
                item(key = "create_time") {
                    ContactCreationTime(contact.createTime)
                }
            }
        }
    }
}

@Composable
private fun ContactCreationTime(createTime: Long) {
    val locale = LocalLocale.current.platformLocale
    val dateStr = remember(createTime, locale) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date(createTime))
    }
    LaunchedEffect(Unit) {
        Log.d("ContactDetailComponentsTester", "创建时间显示于联系人详情末尾")
    }
    Text(
        text = "创建于 $dateStr",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BadgerSpacing.xl, vertical = BadgerSpacing.md),
    )
}

/** 基础信息 4 个 fieldKey(与 SYSTEM_FIELDS 顺序对应) */
private val BASIC_INFO_FIELD_KEYS = setOf("gender", "birthday", "country", "region")
private val PLATFORM_FIELD_KEYS = top.mcxiafeng.badger.ocr.PLATFORM_FIELD_KEYS

/**
 * 联系人详情页"标签"Section 卡片。
 *
 * - 顶部一行：左侧"标签"标题，右侧 ✨ AI 按钮(永远可见;无 bio 时弹错而非置灰,符合"始终可发现")
 * - 空态:ArrowPreference "添加标签"
 * - 非空态:FlowRow 多框(与 TagPicker 排版一致),整体 Section clickable → 调整标签。
 *
 * 设计:整张 Section 卡片作为一个可点击块,内部不再放冗余二级 ArrowPreference。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContactTagsCard(
    tags: List<Tag>,
    onTagsClick: () -> Unit,
    onAiTagsClick: () -> Unit,
) {
    // [修复防御]: SectionCard 不渲染 title,所以这里自绘 header 行(标题左 + ✨ IconButton 右),
    // ✨ 用 IconButton 自带 48dp 触控区,避免与下面 FlowRow 的点击区重叠。
    SectionCard(title = "") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "标签",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onAiTagsClick,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI 推荐标签",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (tags.isEmpty()) {
            ArrowPreference(
                title = "添加标签",
                summary = "暂未添加",
                onClick = onTagsClick,
            )
        } else {
            // 标签内容块使用统一内容轮廓，保留标签语义色与现有点击入口。
            // 用 FlowRow 横向流式换行,每框宽度由内容自然撑开(不填满 maxWidth);
            // 整段 clickable 触发 TagPicker。
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTagsClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = tag.name,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(BadgerShapes.Surface)
                            .background(Color(tag.color).copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
