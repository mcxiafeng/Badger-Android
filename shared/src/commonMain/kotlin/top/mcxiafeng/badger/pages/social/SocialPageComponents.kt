package top.mcxiafeng.badger.pages.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Pencil
import top.mcxiafeng.badger.data.model.PlatformEntry
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/**
 * 「我的名片」顶部卡片（U12 hero 化）
 *
 * 层级：姓名 title1 > 签名 footnote1 > 短链状态 footnote2。
 * 头像加大到 88dp；卡片表面用极浅 tint 做出门面感（V2 已无本地 cardImagePath，
 * 缘渐隐用 onBackground 低透明叠层代替背景图）。
 *
 * @param profileName 姓名
 * @param profileBio 个性签名
 * @param avatarPath 本地头像路径
 * @param linkUpdateState 短链同步状态（轻量指示，不再画红点）
 * @param onEditProfile 进入编辑资料页
 */
@Composable
fun SocialProfileHeader(
    profileName: String?,
    profileBio: String?,
    avatarPath: String?,
    linkUpdateState: LinkUpdateState,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
        cornerRadius = BadgerRadius.card,
        insideMargin = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                .padding(BadgerSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像（点击直接进编辑）
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onEditProfile),
                contentAlignment = Alignment.Center,
            ) {
                ContactAvatar(
                    name = profileName ?: "用户",
                    avatarPath = avatarPath,
                    avatarUrl = null,
                    size = 88,
                )
            }
            Spacer(modifier = Modifier.width(BadgerSpacing.lg))
            // 姓名 + 签名（签名最多 2 行，弱化）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs),
            ) {
                Text(
                    text = profileName?.takeIf { it.isNotBlank() } ?: "未设置昵称",
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profileBio?.takeIf { it.isNotBlank() } ?: "点击右侧编辑完善你的名片",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LinkSyncIndicator(linkUpdateState)
            }
            Spacer(modifier = Modifier.width(BadgerSpacing.sm))
            // 编辑入口（图标按钮 + 箭头）
            Box(
                modifier = Modifier
                    .clip(miuixShape(BadgerRadius.sm))
                    .clickable(onClick = onEditProfile)
                    .padding(BadgerSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Lucide.Pencil,
                        contentDescription = "编辑名片",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(BadgerSpacing.xxs))
                    Icon(
                        imageVector = Lucide.ChevronRight,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * 短链同步状态指示（极简文字态，避免红点式干扰）
 */
@Composable
private fun LinkSyncIndicator(state: LinkUpdateState) {
    val (text, color) = when (state) {
        LinkUpdateState.UPDATING -> "同步中" to MiuixTheme.colorScheme.primary
        LinkUpdateState.SUCCESS -> "已同步" to MiuixTheme.colorScheme.primary.copy(alpha = 0.7f)
        LinkUpdateState.ERROR -> "同步失败" to MiuixTheme.colorScheme.error
        LinkUpdateState.IDLE -> return
    }
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = color,
        maxLines = 1,
    )
}

/**
 * 平台 Chips 横排（替代旧 PlatformSwitchRow）
 *
 * MIUI 风格：选中态由「图标彩色填充 + 描边 + 文字 primary + 底部 indicator」三层叠加表达。
 * 与旧实现差异：去掉 emoji-style 的 36dp 圆角图标 + "灰色淡化" 仅靠 alpha；用描边和 indicator
 * 让用户清楚看到当前选中的是哪一个。
 *
 * @param platforms 平台列表 (fieldKey, entry)
 * @param selectedPlatformIndex 当前选中的平台索引
 * @param onSelectPlatform 选择平台回调
 */
@Composable
fun PlatformChipsRow(
    platforms: List<Pair<String, *>>,
    selectedPlatformIndex: Int,
    onSelectPlatform: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // [滑动切换] Pager 翻页 / 外部选择时，选中 chip 自动滚入视野
    LaunchedEffect(selectedPlatformIndex, platforms.size) {
        if (selectedPlatformIndex in platforms.indices) {
            listState.animateScrollToItem(selectedPlatformIndex)
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = BadgerSpacing.sm),
        contentPadding = PaddingValues(horizontal = BadgerSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(platforms.size) { index ->
            val (fieldKey, _) = platforms[index]
            val isSelected = index == selectedPlatformIndex
            val displayName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey
            PlatformChip(
                fieldKey = fieldKey,
                label = displayName,
                selected = isSelected,
                onClick = { onSelectPlatform(index) },
            )
        }
    }
}

@Composable
private fun PlatformChip(
    fieldKey: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val onSurfaceVariant = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val borderColor = if (selected) primary else onSurfaceVariant.copy(alpha = 0.25f)
    val labelColor = if (selected) primary else onSurfaceVariant
    val iconColor = if (selected) primary else onSurfaceVariant.copy(alpha = 0.4f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(miuixShape(BadgerRadius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = BadgerSpacing.xs, vertical = BadgerSpacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) primary.copy(alpha = 0.12f) else Color.Transparent)
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PlatformIcon(
                fieldKey = fieldKey,
                color = iconColor,
                sizeDp = 22f,
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.xxs))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = labelColor,
            maxLines = 1,
        )
        // 底部 indicator（仅选中时可见）
        Box(
            modifier = Modifier
                .padding(top = BadgerSpacing.xxs)
                .size(width = 14.dp, height = 2.dp)
                .clip(miuixShape(1.dp))
                .background(if (selected) primary else Color.Transparent),
        )
    }
}

/**
 * 当前选中平台的信息卡（显示名 + ID 两个可编辑行）
 *
 * MIUI BasicComponent 风格：左标题 / 中副标题 / 右箭头。
 * 与 chips 行共享「平台图标」信息后，行内不再重复一次 ——
 * 平台已选定的前提下，icon 是冗余信号，只保留文字 + 箭头。
 *
 * @param displayName 当前显示名
 * @param value 当前 ID/链接
 * @param idLabel ID 输入框标签（来自 PlatformFieldDef.inputHint）
 * @param onEditDisplayName 点击编辑显示名
 * @param onEditValue 点击编辑 ID
 */
@Composable
fun PlatformInfoCard(
    displayName: String?,
    value: String?,
    idLabel: String,
    onEditDisplayName: () -> Unit,
    onEditValue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurfaceVariant = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val notSetText = "未设置"
    val notSetColor = onSurfaceVariant.copy(alpha = 0.6f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column {
            // 第一行：平台昵称
            PlatformInfoRow(
                title = "平台昵称",
                subtitle = displayName?.takeIf { it.isNotBlank() } ?: notSetText,
                subtitleColor = if (displayName.isNullOrBlank()) notSetColor else MiuixTheme.colorScheme.onBackground,
                onClick = onEditDisplayName,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BadgerSpacing.lg)
                    .height(0.5.dp)
                    .background(MiuixTheme.colorScheme.dividerLine),
            )
            // 第二行：ID/链接
            PlatformInfoRow(
                title = idLabel,
                subtitle = value?.takeIf { it.isNotBlank() } ?: notSetText,
                subtitleColor = if (value.isNullOrBlank()) notSetColor else MiuixTheme.colorScheme.onBackground,
                onClick = onEditValue,
            )
        }
    }
}

@Composable
private fun PlatformInfoRow(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 中：标题 + 副标题（左侧不再重复 platform icon — chips 行已传达同一信息）
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xxs),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.body2,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 右箭头（暗示可点击）
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 平台为空时的引导卡
 *
 * 保留 [SetupGuideCard] 的语义但采用更"克制"的视觉：图标 + 一行字 + 一个按钮。
 */
@Composable
fun PlatformEmptyCard(onNavigateToProfile: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
        insideMargin = PaddingValues(BadgerSpacing.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        ) {
            Text(
                text = "你还没有添加任何社交平台",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "添加后即可生成二维码分享给朋友",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            TextButton(
                text = "去添加",
                onClick = onNavigateToProfile,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ============================================================
//  平台内容滑动切换（HorizontalPager）
// ============================================================

/** 手机号格式（11位数字），用于区分二维码内容类型 */
private val PHONE_NUMBER_REGEX = Regex("\\d{11}")

/** 非当前页透明度（仅 graphicsLayer alpha，GPU 合成，不触发布局） */
private const val PAGE_DIM_ALPHA = 0.75f

/** 平台 ID 输入标签（微信号/QQ号…；"或"分隔取首个，空值回退"平台名+号"）。 */
internal fun idLabelFor(fieldKey: String): String {
    val def = FIELD_DEF_MAP[fieldKey] ?: return "ID"
    val hint = def.inputHint
    return if (hint.contains("或")) {
        hint.substringBefore("或").trim()
    } else {
        hint.ifBlank { def.displayName + "号" }
    }
}

/** 二维码内容：jumpLink 优先，value 文本兜底（微信/手机号场景）。 */
internal fun qrContentFor(entry: PlatformEntry): String = when {
    entry.jumpLink.isNotBlank() -> entry.jumpLink
    !entry.value.isNullOrBlank() -> {
        val value = entry.value ?: ""
        if (value.matches(PHONE_NUMBER_REGEX)) "手机号：$value" else "微信号：$value"
    }
    else -> ""
}

/**
 * 平台内容横滑容器：左右滑动在多平台间切换，与顶部 chips 双向同步。
 *
 * 同步契约（防反馈环）：
 * - Pager → VM：`settledPage` 落定才提交（拖拽中间态不进 VM，短链同步频率与点按一致）；
 * - VM → Pager：chips 点击 / 默认平台变化时动画翻页（自身发起的翻页经 settled 判等跳过）。
 *
 * 嵌套手势：本 Pager 位于 App 主 Tab Pager（同方向）内——内层可翻页时消费手势，
 * 到边缘继续拖动交给外层切 Tab（Compose 嵌套滚动标准语义），无需额外拦截。
 */
@Composable
internal fun PlatformContentPager(
    platforms: List<Pair<String, PlatformEntry>>,
    selectedPlatformIndex: Int,
    avatarPath: String?,
    onSelectPlatform: (Int) -> Unit,
    onEditDisplayName: (String, PlatformEntry) -> Unit,
    onEditValue: (String, PlatformEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    // [修复防御]: 初始页直接落位到已选平台——否则重进页面会看到从平台1滚到已选位置的开屏动画
    val initialPage = selectedPlatformIndex.coerceIn(0, (platforms.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { platforms.size },
    )

    // VM → Pager
    LaunchedEffect(selectedPlatformIndex, platforms.size) {
        val target = selectedPlatformIndex
        if (target in platforms.indices && target != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }
    // Pager → VM（rememberUpdatedState 防 lambda 陈旧捕获）
    val latestSelected by rememberUpdatedState(selectedPlatformIndex)
    val latestPlatforms by rememberUpdatedState(platforms)
    val latestSelect by rememberUpdatedState(onSelectPlatform)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page in latestPlatforms.indices && page != latestSelected) latestSelect(page)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        pageSpacing = BadgerSpacing.md,
        verticalAlignment = Alignment.Top,
    ) { page ->
        val (fieldKey, entry) = platforms[page]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // [性能] 偏移在绘制期读取（lambda 内状态读取不触发重组），
                // 拖拽期间只有 GPU alpha 合成，杜绝逐帧重组卡顿
                .graphicsLayer {
                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    alpha = 1f - abs(offset).coerceIn(0f, 1f) * (1f - PAGE_DIM_ALPHA)
                },
        ) {
            PlatformInfoCard(
                displayName = entry.displayName,
                value = entry.value,
                idLabel = idLabelFor(fieldKey),
                onEditDisplayName = { onEditDisplayName(fieldKey, entry) },
                onEditValue = { onEditValue(fieldKey, entry) },
            )
            val content = qrContentFor(entry)
            Box(
                modifier = Modifier.padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
            ) {
                if (content.isNotBlank()) {
                    val displayValue = buildString {
                        if (!entry.displayName.isNullOrBlank() && !entry.value.isNullOrBlank()) {
                            append(entry.displayName)
                            append("（")
                            append(entry.value)
                            append("）")
                        } else if (!entry.value.isNullOrBlank()) {
                            append(entry.value)
                        }
                    }
                    QrCodeCard(
                        content = content,
                        userName = entry.displayName ?: FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey,
                        platformName = FIELD_DEF_MAP[fieldKey]?.displayName ?: fieldKey,
                        platformValue = displayValue.ifBlank { null },
                        avatarPath = avatarPath,
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(BadgerSpacing.lg),
                    ) {
                        Text(
                            text = "请先填写「${idLabelFor(fieldKey)}」后再生成二维码",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
