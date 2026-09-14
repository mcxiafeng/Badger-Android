package top.mcxiafeng.badger.pages.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ui.components.ContactAvatar
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect

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
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.xs),
            ) {
                
                BasicText(
                    text = profileName?.takeIf { it.isNotBlank() } ?: "未设置昵称",
                    style = MiuixTheme.textStyles.title1.copy(color = MiuixTheme.colorScheme.onBackground),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 32.sp, stepSize = 1.sp),
                    modifier = Modifier.fillMaxWidth(),
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

@Composable
fun PlatformChipsRow(
    platforms: List<Pair<String, *>>,
    selectedPlatformIndex: Int,
    onSelectPlatform: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    
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
        
        Box(
            modifier = Modifier
                .padding(top = BadgerSpacing.xxs)
                .size(width = 14.dp, height = 2.dp)
                .clip(miuixShape(1.dp))
                .background(if (selected) primary else Color.Transparent),
        )
    }
}

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
        
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

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

private val VALUE_URL_REGEX = Regex("(?i)^(https?://|www\\.)\\S+$")

internal fun idLabelFor(fieldKey: String): String {
    val def = FIELD_DEF_MAP[fieldKey] ?: return "ID"
    val hint = def.inputHint
    return if (hint.contains("或")) {
        hint.substringBefore("或").trim()
    } else {
        hint.ifBlank { def.displayName + "号" }
    }
}

internal fun platformShareUrl(entry: PlatformEntry): String? = when {
    entry.jumpLink.isNotBlank() -> entry.jumpLink
    entry.value.isNullOrBlank() -> null
    VALUE_URL_REGEX.matches(entry.value.trim()) -> entry.value.trim()
    else -> null
}

internal fun qrContentFor(entry: PlatformEntry, idLabel: String): String = when {
    entry.jumpLink.isNotBlank() -> entry.jumpLink
    entry.value.isNullOrBlank() -> ""
    VALUE_URL_REGEX.matches(entry.value.trim()) -> entry.value.trim()
    else -> "$idLabel：${entry.value}"
}

@Composable
internal fun PlatformContent(
    platforms: List<Pair<String, PlatformEntry>>,
    selectedPlatformIndex: Int,
    avatarPath: String?,
    userName: String?,
    onEditDisplayName: (String, PlatformEntry) -> Unit,
    onEditValue: (String, PlatformEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeIndex = selectedPlatformIndex.coerceIn(0, platforms.lastIndex.coerceAtLeast(0))
    AnimatedContent(
        modifier = modifier,
        targetState = safeIndex,
        transitionSpec = {
            
            fadeIn(tween(BadgerMotion.DURATION_BASE)) togetherWith
                fadeOut(tween(BadgerMotion.DURATION_BASE))
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
