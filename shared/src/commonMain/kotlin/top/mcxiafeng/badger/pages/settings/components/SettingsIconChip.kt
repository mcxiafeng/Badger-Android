package top.mcxiafeng.badger.pages.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.yukonga.miuix.kmp.basic.Icon
import top.mcxiafeng.badger.ui.navigation.SettingsPage

/** 图标芯片尺寸常量。 */
private val ICON_CHIP_SIZE = 32.dp
private val ICON_CHIP_ICON_SIZE = 18.dp

/**
 * 设置行左侧彩色图标芯片（MIUI 风格）。
 *
 * 32dp 圆角方色底 + 18dp 白图标，色底由 [SettingsChipColors.colorFor] 按 [SettingsPage] 取色。
 * 替代原先各页裸 `Icon(tint=onSurfaceVariantSummary)` 的朴素行图标，给设置域统一视觉性格。
 *
 * @param icon 行图标（来自 [SettingsPage.icon]）
 * @param container 色底颜色
 */
@Composable
internal fun SettingsIconChip(
    icon: ImageVector,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ICON_CHIP_SIZE)
            .clip(RoundedCornerShape(BadgerRadius.chip))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(ICON_CHIP_ICON_SIZE),
        )
    }
}

/**
 * 设置图标芯片配色板（单一来源）。
 *
 * 按 [SettingsPage] 类型返回固定色相；明暗两态共用同一色（白图标在中等亮度色底上对比达标）。
 * 新增设置页只需在此补一行。
 */
internal object SettingsChipColors {
    fun colorFor(page: SettingsPage): Color = when (page) {
        SettingsPage.Dashboard -> Color(0xFF3B82F6)
        SettingsPage.SyncStatus -> Color(0xFF22C55E)
        SettingsPage.OperationHistory -> Color(0xFF8B5CF6)
        SettingsPage.TagManager -> Color(0xFFF59E0B)
        SettingsPage.ServerShortLinks -> Color(0xFF06B6D4)
        SettingsPage.UserSettings -> Color(0xFFEC4899)
        SettingsPage.UiSettings -> Color(0xFF6366F1)
        SettingsPage.About -> Color(0xFF64748B)
        SettingsPage.AccountProfile -> Color(0xFF0EA5E9)
        SettingsPage.ChangePassword -> Color(0xFFEF4444)
        SettingsPage.Devices -> Color(0xFF8B5CF6)
        SettingsPage.OpenSourceLicense -> Color(0xFF64748B)
        SettingsPage.AppLog -> Color(0xFF6B7280)
        SettingsPage.ContactUs -> Color(0xFFEC4899)
        SettingsPage.Notifications -> Color(0xFFF59E0B)
    }
}
