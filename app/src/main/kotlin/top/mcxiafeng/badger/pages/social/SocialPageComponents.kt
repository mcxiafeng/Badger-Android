package top.mcxiafeng.badger.pages.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
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

/**
 * 「我的名片」顶部卡片
 *
 * MIUI 风格：左头像 + 中姓名/签名 + 右编辑入口，弱化为非全屏色块。
 * 与 [BlueBusinessCard] 的差异：去掉了大面积 primary 色块与"红点指示器"，
 * 让头部回到"展示个人信息"本身，长按换图/写 NFC 等动作外移到 TopAppBar。
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
        insideMargin = PaddingValues(BadgerSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    size = 64,
                )
            }
            Spacer(modifier = Modifier.width(BadgerSpacing.lg))
            // 姓名 + 签名（签名最多 1 行）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xxs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
                ) {
                    Text(
                        text = profileName?.takeIf { it.isNotBlank() } ?: "未设置昵称",
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    LinkSyncIndicator(linkUpdateState)
                }
                Text(
                    text = profileBio?.takeIf { it.isNotBlank() } ?: "点击右侧编辑完善你的名片",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "编辑名片",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(BadgerSpacing.xxs))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        platforms.forEachIndexed { index, (fieldKey, _) ->
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
            imageVector = Icons.Filled.ChevronRight,
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
