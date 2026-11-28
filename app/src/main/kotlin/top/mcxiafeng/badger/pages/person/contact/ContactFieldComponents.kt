package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.compose.foundation.Image
import top.mcxiafeng.badger.ui.designsystem.BadgerAlpha
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.continuousClip as clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.ContactFieldDisplay
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider as Divider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.designsystem.BadgerShapes
import top.yukonga.miuix.kmp.utils.MiuixIndication
import kotlin.collections.iterator
import top.mcxiafeng.badger.ui.glass.LocalGlassStyle
import androidx.compose.foundation.LocalIndication

@Composable
internal fun ContactFieldSection(
    title: String,
    fields: List<ContactFieldDisplay>,
    onClick: (ContactFieldDisplay) -> Unit,
    onLongPress: (ContactFieldDisplay) -> Unit,
) {
    // [修复防御]: 删除 SmallTitle 灰色分组标题,只保留简洁卡片。title 参数保留
    // 是为了对外不破坏调用方签名(可能有外部引用),实际不再渲染。
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        // 按 fieldKey 分组，同组多值时编号显示（QQ1、QQ2、手机1、手机2...）
        val grouped = fields.groupBy { it.fieldKey ?: it.valueId }
        for ((_, group) in grouped) {
            if (group.size == 1) {
                val first = group.first()
                LongPressArrowPreference(
                    title = first.fieldName,
                    summary = first.value,
                    onClick = { onClick(first) },
                    onLongClick = { onLongPress(first) },
                )
            } else {
                group.forEachIndexed { index, field ->
                    val numberedName = "${field.fieldName}${index + 1}"
                    LongPressArrowPreference(
                        title = numberedName,
                        summary = field.value,
                        onClick = { onClick(field) },
                        onLongClick = { onLongPress(field) },
                    )
                }
            }
        }
    }
}

/**
 * 支持长按的 ArrowPreference（带 Miuix 点击反馈效果）
 */
@Composable
internal fun LongPressArrowPreference(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: ImageVector? = null,
    showArrow: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = MiuixIndication(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(BasicComponentDefaults.InsideMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline1,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (showArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            val layoutDirection = LocalLayoutDirection.current
            Image(
                modifier = Modifier
                    .size(width = 10.dp, height = 16.dp)
                    .graphicsLayer {
                        scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    }
                    .align(Alignment.CenterVertically),
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
            )
        }
    }
}

/**
 * 功能栏中的带文字操作按钮；置灰替代隐藏，保证功能栏进入模式即成形
 */
@Composable
fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MiuixTheme.colorScheme.onBackground,
    enabled: Boolean = true,
) {
    val foreground = if (enabled) tint else tint.copy(alpha = BadgerAlpha.DISABLED_FOREGROUND_ALPHA)
    Column(
        modifier = Modifier
            .clip(BadgerShapes.rounded(12.dp))
            .combinedClickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = foreground,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = foreground,
        )
    }
}

// ========== PR2:沉浸头图 + 分区信息卡组件 ==========

/**
 * 通用分区卡片:标题 + 内容,间距符合 Miuix 规范(卡片间 12dp、卡片内 0dp)。
 *
 * **修复重叠根因(PR2 修复 #3)**:不再对内容加 vertical=4dp 的 Box 包裹——
 * 外层 Box(vertical=4dp) + LongPressArrowPreference 自带 InsideMargin
 * 两层 padding 叠加 = 相邻两行视觉挤在一起(你看到的"重叠")。
 * 改为 0dp 包裹,所有视觉边界交给 InsideMargin 单层控制,
 * 行与行之间由调用方显式插入 `ThinDivider()` 做视觉分隔。
 */
@Composable
internal fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    // [修复防御]: 删除 SmallTitle 灰色标题,只保留简洁卡片。title 参数保留
    // 是为了对外不破坏调用方签名(可能有外部引用),实际不再渲染。
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        content()
    }
}

/**
 * 基础信息 2x2 网格(PR2)
 *
 * 设计:
 * - 外层 Column,小标题 + 2 个 Row(每 Row 放 2 个 Cell)
 * - 每个 Cell 独立 Card(便于点击精度和视觉一致性)
 * - Row 内每个 Cell 用 `Modifier.weight(1f)` 平分宽度,中间留 8dp 间距
 *
 * 每个 Cell 整体可点击 → 弹出对应编辑 Dialog(性别=滚轮,生日=日期,国家/地区=选择器)。
 */
@Composable
internal fun BasicInfoCard(
    fields: List<ContactFieldDisplay>,
    enabled: Boolean = true,
    onCellClick: (fieldKey: String, currentValue: String?) -> Unit = { _, _ -> },
) {
    val byKey = remember(fields) { fields.associateBy { it.fieldKey } }

    // PR2 修复 #3:严格 2x2 —— [性别, 生日] / [国家, 地区]
    val row1 = listOf(
        BasicInfoCellRef("gender", "性别", Icons.Default.Transgender),
        BasicInfoCellRef("birthday", "生日", Icons.Default.Cake),
    )
    val row2 = listOf(
        BasicInfoCellRef("country", "国家", Icons.Default.Flag),
        BasicInfoCellRef("region", "地区", Icons.Default.LocationOn),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // [修复防御]: 删除 SmallTitle("基础信息") 灰色标题;2x2 Cell 直接呈现。
        // 之前注释里提到的灰字起点对齐(28dp)不再适用 —— 现在整页统一无 SmallTitle。
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            BasicInfoRow(cells = row1, byKey = byKey, enabled = enabled, onCellClick = onCellClick)
            Spacer(modifier = Modifier.height(8.dp))
            BasicInfoRow(cells = row2, byKey = byKey, enabled = enabled, onCellClick = onCellClick)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 2x2 单行:横向 2 个 Cell,weight=1f 平分宽度,中间 8dp 间距 */
@Composable
private fun BasicInfoRow(
    cells: List<BasicInfoCellRef>,
    byKey: Map<String?, ContactFieldDisplay>,
    enabled: Boolean,
    onCellClick: (fieldKey: String, currentValue: String?) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(8.dp))
            }
            BasicInfoSmallCard(
                cell = cell,
                value = byKey[cell.key]?.value,
                enabled = enabled,
                onClick = { onCellClick(cell.key, byKey[cell.key]?.value) },
                // [修复防御]: 两个 Cell 平分宽度,各 weight=1f
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 基础信息独立小卡片(2x2 单格)
 *
 * 设计:左 icon + 上下两行文字(label + value),紧凑、纯净。
 * 取消 arrow(图标已经在左),让两格并排不拥挤。
 */
@Composable
private fun BasicInfoSmallCard(
    cell: BasicInfoCellRef,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = if (enabled) { {
            Log.d("ContactFieldComponentsTester", "Basic info edit requested; field=${cell.key}")
            onClick()
        } } else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // [修复防御]: horizontal=16dp 让 Cell 内文字起点 = 12(Card 外距) + 16 = 28dp,
                // 与 SmallTitle("基础信息"灰字)InsideMargin(28dp)完全居左对齐;
                // 之前 12dp,Cell 内文字起点 24dp,比灰字偏左 4dp 导致"基础信息"分组视觉不对齐。
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 左 icon
                Icon(
                    imageVector = cell.icon,
                    contentDescription = cell.label,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = cell.label,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: "未设置",
                style = MiuixTheme.textStyles.body1,
                color = if (value.isNullOrBlank())
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                else MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
    }
}

/** BasicInfoCard 的单个格子数据。提到顶层以避免在 Composable 内嵌 data class。 */
internal data class BasicInfoCellRef(
    val key: String,
    val label: String,
    val icon: ImageVector,
)
