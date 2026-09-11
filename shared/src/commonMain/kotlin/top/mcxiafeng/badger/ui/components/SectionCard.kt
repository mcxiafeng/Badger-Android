package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Cake
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Transgender

/**
 * 通用分区卡片：标题 + 内容，间距符合 Miuix 规范（卡片间 12dp、卡片内 0dp）。
 *
 * **使用场景**（U08 自 `ContactFieldComponents.kt` 下沉）：
 * 联系人详情页各信息分区（社交平台 / 个人介绍 / 基本信息等）的统一容器。
 * 外层 padding(horizontal=12dp, bottom=12dp)，内容行间距交给调用方/InsideMargin 控制。
 *
 * **修复重叠根因（PR2 修复 #3）**：不再对内容加 vertical=4dp 的 Box 包裹——
 * 外层 Box(vertical=4dp) + LongPressArrowPreference 自带 InsideMargin
 * 两层 padding 叠加 = 相邻两行视觉挤在一起（你看到的"重叠"）。
 * 改为 0dp 包裹，所有视觉边界交给 InsideMargin 单层控制，
 * 行与行之间由调用方显式插入 `ThinDivider()` 做视觉分隔。
 *
 * @param title 分区标题（当前不再渲染灰色标题行，参数保留以兼容调用方签名）
 * @param content 卡片内容
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
 * 基础信息 2×2 网格 + 位置整宽行（PR2 / 位置功能）
 *
 * **使用场景**（U08 自 `ContactFieldComponents.kt` 下沉）：
 * 联系人详情页的基本信息区——性别 / 生日 / 国家 / 地区四格卡片 + 位置一格（整宽）。
 *
 * 设计：
 * - 外层 Column，小标题 + 2 个 Row（每 Row 放 2 个 Cell）+ 位置 Row（整宽 Cell）
 * - 每个 Cell 独立 Card（便于点击精度和视觉一致性）
 * - Row 内每个 Cell 用 `Modifier.weight(1f)` 平分宽度，中间留 8dp 间距
 *
 * 每个 Cell 整体可点击 → 弹出对应编辑 Dialog（性别=滚轮，生日=日期，国家=国家选择器，
 * 地区=地区级联选择器（中国省市区），由调用方按 fieldKey 路由）。
 *
 * @param fields 联系人字段列表，按 fieldKey 匹配 Cell
 * @param onCellClick Cell 点击回调，参数为 (fieldKey, currentValue)
 */
@Composable
internal fun BasicInfoCard(
    fields: List<PersonFieldDisplay>,
    onCellClick: (fieldKey: String, currentValue: String?) -> Unit = { _, _ -> },
) {
    val byKey = remember(fields) { fields.associateBy { it.fieldKey } }

    // PR2 修复 #3:严格 2x2 —— [性别, 生日] / [国家, 地区]；位置独占第三行整宽
    val row1 = listOf(
        BasicInfoCellRef("gender", "性别", Lucide.Transgender),
        BasicInfoCellRef("birthday", "生日", Lucide.Cake),
    )
    val row2 = listOf(
        BasicInfoCellRef("country", "国家", Lucide.Flag),
        BasicInfoCellRef("region", "地区", Lucide.MapPin),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // [修复防御]: 删除 SmallTitle("基础信息") 灰色标题;2x2 Cell 直接呈现。
        // 之前注释里提到的灰字起点对齐(28dp)不再适用 —— 现在整页统一无 SmallTitle。
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            BasicInfoRow(cells = row1, byKey = byKey, onCellClick = onCellClick)
            Spacer(modifier = Modifier.height(8.dp))
            BasicInfoRow(cells = row2, byKey = byKey, onCellClick = onCellClick)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 2×2 单行：横向 2 个 Cell，weight=1f 平分宽度，中间 8dp 间距 */
@Composable
private fun BasicInfoRow(
    cells: List<BasicInfoCellRef>,
    byKey: Map<String?, PersonFieldDisplay>,
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
                onClick = { onCellClick(cell.key, byKey[cell.key]?.value) },
                // [修复防御]: 两个 Cell 平分宽度,各 weight=1f
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 基础信息独立小卡片（2×2 单格）
 *
 * 设计：左 icon + 上下两行文字（label + value），紧凑、纯净。
 * 取消 arrow（图标已经在左），让两格并排不拥挤。
 */
@Composable
private fun BasicInfoSmallCard(
    cell: BasicInfoCellRef,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = MiuixIndication(),
                    onClick = onClick,
                )
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
            // 长值（"广东省深圳市南山区"）放不下一行时字号自动缩小（16sp→10sp），
            // 缩到下限仍放不下才省略——替代硬裁切/换行两种都不合格的方案
            BasicText(
                text = value?.takeIf { it.isNotBlank() } ?: "未设置",
                style = MiuixTheme.textStyles.body1.copy(
                    color = if (value.isNullOrBlank()) MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else MiuixTheme.colorScheme.onBackground,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp, stepSize = 1.sp),
                modifier = Modifier.fillMaxWidth(),
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
