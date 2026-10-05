package top.mcxiafeng.badger.page.collection

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.ui.components.Alert
import top.mcxiafeng.badger.ui.components.SkeletonBox
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.SafeLog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val TAG = "CollectionComponentsTester"

internal val CARD_HEIGHT = 200.dp
private val FOLDER_ICON_SIZE = 32.dp
private const val COVER_FLAT_SCRIM_ALPHA = 0.12f
private const val COVER_TOP_SCRIM_ALPHA = 0.40f
private const val COVER_TOP_SCRIM_END_STOP = 0.28f
private const val COVER_BOTTOM_SCRIM_START_STOP = 0.62f
private const val COVER_BOTTOM_SCRIM_ALPHA = 0.62f
private const val COVER_DARK_OVERLAY_ALPHA = 0.20f
private const val COVER_SUB_TEXT_ALPHA = 0.85f

private const val SKELETON_ROWS = 3

/**
 * 名片夹封面卡（双列网格用）：整卡封面 + 上下双遮罩 + 底部文字压图，
 * 无封面时 surfaceContainer 平涂 + Folder 占位。长按弹出编辑/删除菜单。
 */
@Composable
fun CollectionCard(
    collection: Collection,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    var bgFailed by remember(collection.backgroundURL) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val hasBg = !collection.backgroundURL.isNullOrBlank() && !bgFailed

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT),
            cornerRadius = BadgerRadius.card,
            pressFeedbackType = PressFeedbackType.Sink,
            onClick = onClick,
            onLongPress = { showMenu = true },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Crossfade(
                    targetState = collection.backgroundURL,
                    animationSpec = tween(BadgerMotion.DURATION_BASE),
                    label = "collectionCardBgCrossfade",
                ) { bgUrl ->
                    if (!bgUrl.isNullOrBlank() && !bgFailed) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = bgUrl,
                                contentDescription = collection.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(BadgerRadius.card)),
                                onState = { state ->
                                    if (state is AsyncImagePainter.State.Error) {
                                        BadgerLog.w(TAG, "封面加载失败: ${SafeLog.url(bgUrl)}")
                                        bgFailed = true
                                    }
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = COVER_FLAT_SCRIM_ALPHA))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0f to Color.Black.copy(alpha = COVER_TOP_SCRIM_ALPHA),
                                                COVER_TOP_SCRIM_END_STOP to Color.Transparent,
                                                COVER_BOTTOM_SCRIM_START_STOP to Color.Transparent,
                                                1f to Color.Black.copy(alpha = COVER_BOTTOM_SCRIM_ALPHA),
                                            ),
                                        )
                                    )
                            )
                            if (isDark) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = COVER_DARK_OVERLAY_ALPHA))
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.surfaceContainer)
                        )
                    }
                }

                val textColor = if (hasBg) Color.White else MiuixTheme.colorScheme.onBackground
                val subTextColor =
                    if (hasBg) Color.White.copy(alpha = COVER_SUB_TEXT_ALPHA)
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(BadgerSpacing.lg),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (!hasBg) {
                        Icon(
                            imageVector = Lucide.Folder,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(FOLDER_ICON_SIZE),
                        )
                    }

                    Column {
                        Text(
                            text = collection.name,
                            color = textColor,
                            style = MiuixTheme.textStyles.title3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val description = collection.description
                        if (!description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                            Text(
                                text = description,
                                color = subTextColor,
                                style = MiuixTheme.textStyles.footnote1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                        Text(
                            text = "${collection.personMembers.size} 位联系人",
                            color = subTextColor.copy(alpha = COVER_SUB_TEXT_ALPHA),
                            style = MiuixTheme.textStyles.footnote2,
                        )
                    }
                }
            }
        }

        OverlayListPopup(
            show = showMenu,
            alignment = PopupPositionProvider.Align.TopStart,
            onDismissRequest = { showMenu = false },
        ) {
            ListPopupColumn {
                val items = listOf("编辑", "删除")
                items.forEachIndexed { index, text ->
                    DropdownImpl(
                        text = text,
                        optionSize = items.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            showMenu = false
                            when (index) {
                                0 -> onEdit?.invoke()
                                1 -> onDelete?.invoke()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 加载骨架：与双列封面卡同形状的呼吸占位（呼吸块复用 ui 的 [SkeletonBox]）。 */
@Composable
fun CollectionSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BadgerSpacing.cardPadding, vertical = BadgerSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.cardGap)) {
                repeat(2) {
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(CARD_HEIGHT)
                            .clip(RoundedCornerShape(BadgerRadius.card)),
                    )
                }
            }
        }
    }
}

/** 新建 / 编辑名片夹共用弹窗：壳与按钮行复用 [Alert]，名称为空时确认置灰，输入态随卸载自动复位。 */
@Composable
fun CollectionEditDialog(
    title: String,
    summary: String?,
    confirmText: String,
    initialName: String,
    initialDescription: String?,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameInput by remember(initialName) { mutableStateOf(initialName) }
    var descInput by remember(initialDescription) { mutableStateOf(initialDescription ?: "") }

    Alert(
        show = true,
        title = title,
        summary = summary,
        confirmText = confirmText,
        positiveEnabled = nameInput.isNotBlank(),
        onConfirm = { onConfirm(nameInput, descInput) },
        onDismiss = onDismiss,
        content = {
            TextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = "名称",
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            TextField(
                value = descInput,
                onValueChange = { descInput = it },
                label = "描述（可选）",
                singleLine = true,
            )
        },
    )
}
