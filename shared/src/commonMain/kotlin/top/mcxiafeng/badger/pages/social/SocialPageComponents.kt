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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
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
                    text = profileBio?.takeIf { it.isNotBlank() } ?: "还没有个性签名",
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

/** URL 形态的 value（http(s)/www 开头）直接编码为链接，不再套"ID：值"文本前缀。 */
private val VALUE_URL_REGEX = Regex("(?i)^(https?://|www\\.)\\S+$")

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

/**
 * 平台条目的可分享 URL：jumpLink 优先；URL 形态的 value 直用；纯 ID 返回 null。
 * NFC 写入与二维码共用该判定——NFC 写 URI record，纯 ID 扫出来既不能跳转也不可读。
 */
internal fun platformShareUrl(entry: PlatformEntry): String? = when {
    entry.jumpLink.isNotBlank() -> entry.jumpLink
    entry.value.isNullOrBlank() -> null
    VALUE_URL_REGEX.matches(entry.value.trim()) -> entry.value.trim()
    else -> null
}

/**
 * 二维码内容：jumpLink 优先；URL 形态的 value 直用（历史上被加"微信号："前缀变成
 * 扫不出链接的纯文本）；普通 ID 用平台自己的 idLabel 前缀（QQ 号不再被误标成手机号）。
 */
internal fun qrContentFor(entry: PlatformEntry, idLabel: String): String = when {
    entry.jumpLink.isNotBlank() -> entry.jumpLink
    entry.value.isNullOrBlank() -> ""
    VALUE_URL_REGEX.matches(entry.value.trim()) -> entry.value.trim()
    else -> "$idLabel：${entry.value}"
}

/** 拖拽提交阈值（占内容宽度比例）。 */
private const val DRAG_COMMIT_FRACTION = 0.3f
/** 边缘橡皮筋阻力（0~1，越小越弹）。 */
private const val DRAG_EDGE_RESISTANCE = 0.25f

/**
 * 平台内容横滑容器：左右手势拖拽切换平台，与顶部 chips 同步。
 *
 * [重构原因] 原 HorizontalPager 与 App 主 Tab Pager 同方向嵌套——拖到边界时两层
 * Pager 争抢手势导致"卡在中间不动"；改用 AnimatedContent + 自绘拖拽偏移，
 * 不参与嵌套滚动链，手势完全自洽，无争抢。
 *
 * 手感：拖拽偏移实时跟随手指（graphicsLayer translationX，绘制期读，零重组）；
 * 松手超阈值→弹簧滑出 + 切换 VM；未超→弹簧回弹；边界橡皮筋防生硬死墙。
 */
@Composable
internal fun PlatformContentPager(
    platforms: List<Pair<String, PlatformEntry>>,
    selectedPlatformIndex: Int,
    avatarPath: String?,
    userName: String?,
    onSelectPlatform: (Int) -> Unit,
    onEditDisplayName: (String, PlatformEntry) -> Unit,
    onEditValue: (String, PlatformEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var contentWidthPx by remember { mutableStateOf(0f) }

    val canSwipeLeft = selectedPlatformIndex < platforms.size - 1
    val canSwipeRight = selectedPlatformIndex > 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { contentWidthPx = it.width.toFloat() }
            .pointerInput(canSwipeLeft, canSwipeRight) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val candidate = dragOffset.value + dragAmount
                        val resisted = when {
                            candidate > 0f && !canSwipeRight -> candidate * DRAG_EDGE_RESISTANCE
                            candidate < 0f && !canSwipeLeft -> candidate * DRAG_EDGE_RESISTANCE
                            else -> candidate
                        }
                        scope.launch { dragOffset.snapTo(resisted) }
                    },
                    onDragEnd = {
                        val threshold = contentWidthPx * DRAG_COMMIT_FRACTION
                        val offset = dragOffset.value
                        scope.launch {
                            when {
                                offset > threshold && canSwipeRight -> {
                                    dragOffset.animateTo(contentWidthPx, spring(dampingRatio = 0.8f))
                                    onSelectPlatform(selectedPlatformIndex - 1)
                                    dragOffset.snapTo(0f)
                                }
                                offset < -threshold && canSwipeLeft -> {
                                    dragOffset.animateTo(-contentWidthPx, spring(dampingRatio = 0.8f))
                                    onSelectPlatform(selectedPlatformIndex + 1)
                                    dragOffset.snapTo(0f)
                                }
                                else -> {
                                    dragOffset.animateTo(0f, spring(dampingRatio = 0.9f))
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffset.animateTo(0f, spring(dampingRatio = 0.9f)) }
                    },
                )
            }
            // [性能] translationX 在绘制期读 dragOffset.value（Animatable backed State），
            // graphicsLayer block 独立执行不触发外层重组
            .graphicsLayer { translationX = dragOffset.value },
    ) {
        val safeIndex = selectedPlatformIndex.coerceIn(0, platforms.lastIndex.coerceAtLeast(0))
        AnimatedContent(
            targetState = safeIndex,
            transitionSpec = {
                val forward = targetState > initialState
                val slidePx = (contentWidthPx * 0.3f).toInt().coerceAtLeast(1)
                val enterFrom = if (forward) slidePx else -slidePx
                val exitTo = if (forward) -slidePx / 3 else slidePx / 3
                (slideInHorizontally(initialOffsetX = { enterFrom }, animationSpec = spring(dampingRatio = 0.85f)) +
                    fadeIn(animationSpec = spring(dampingRatio = 0.85f)))
                    .togetherWith(
                        slideOutHorizontally(targetOffsetX = { exitTo }, animationSpec = spring(dampingRatio = 0.85f)) +
                            fadeOut(animationSpec = spring(dampingRatio = 0.85f))
                    )
            },
            label = "platform_content",
        ) { index ->
            if (index !in platforms.indices) return@AnimatedContent
            val (fieldKey, entry) = platforms[index]
            val idLabel = idLabelFor(fieldKey)
            val content = qrContentFor(entry, idLabel)
            Column(modifier = Modifier.fillMaxWidth()) {
                PlatformInfoCard(
                    displayName = entry.displayName,
                    value = entry.value,
                    idLabel = idLabel,
                    onEditDisplayName = { onEditDisplayName(fieldKey, entry) },
                    onEditValue = { onEditValue(fieldKey, entry) },
                )
                Box(modifier = Modifier.padding(horizontal = BadgerSpacing.lg)) {
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
                            userName = userName?.takeIf { it.isNotBlank() }
                                ?: entry.displayName?.takeIf { it.isNotBlank() }
                                ?: "我的名片",
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
                                text = "请先填写「$idLabel」后再生成二维码",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}
