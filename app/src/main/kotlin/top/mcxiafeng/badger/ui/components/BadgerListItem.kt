package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一列表项组件
 *
 * 基于 miuix [BasicComponent] 封装，提供一致的列表项样式：
 * - 左侧：头像/图标（通过 [startContent] 自定义）
 * - 中间：标题 + 可选摘要
 * - 右侧：可选尾部内容 + 可选箭头
 *
 * 适用于联系人列表、设置项、选择列表等场景。
 *
 * @param title 主标题
 * @param summary 可选摘要文字（显示在标题下方）
 * @param startContent 左侧内容槽（通常放 [ContactAvatar] 或 [Icon]）
 * @param endContent 右侧内容槽（可放 Tag 色点、Badge 等）
 * @param showArrow 是否显示右箭头（默认 false）
 * @param onClick 点击回调（null 表示不可点击）
 * @param onClickLabel 点击动作的无障碍描述
 * @param role 点击语义角色
 * @param modifier Modifier
 */
@Composable
fun BadgerListItem(
    title: String,
    summary: String? = null,
    startContent: (@Composable () -> Unit)? = null,
    endContent: (@Composable () -> Unit)? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    modifier: Modifier = Modifier,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = startContent,
        endActions = if (endContent != null || showArrow) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.xs),
                ) {
                    endContent?.invoke()
                    if (showArrow) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                            modifier = Modifier.size(BadgerSize.iconSm),
                        )
                    }
                }
            }
        } else {
            null
        },
        onClick = onClick,
        onClickLabel = onClickLabel,
        role = role,
        modifier = modifier,
    )
}

/**
 * 带头像的联系人列表项
 *
 * 封装 [BadgerListItem] + [ContactAvatar]，减少调用方样板代码。
 *
 * @param name 联系人名字（用于头像占位符首字母）
 * @param avatarUrl 头像 URL
 * @param avatarPath 头像本地路径
 * @param summary 摘要（如备注、平台信息）
 * @param endContent 右侧内容槽
 * @param showArrow 是否显示箭头
 * @param onClick 点击回调
 * @param onClickLabel 点击动作的无障碍描述
 * @param role 点击语义角色
 */
@Composable
fun BadgerContactListItem(
    name: String,
    avatarUrl: String? = null,
    avatarPath: String? = null,
    summary: String? = null,
    endContent: (@Composable () -> Unit)? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    modifier: Modifier = Modifier,
) {
    BadgerListItem(
        title = name,
        summary = summary,
        startContent = {
            ContactAvatar(
                name = name,
                avatarUrl = avatarUrl,
                avatarPath = avatarPath,
                size = BadgerSize.avatarMd.value.toInt(),
            )
        },
        endContent = endContent,
        showArrow = showArrow,
        onClick = onClick,
        onClickLabel = onClickLabel,
        role = role,
        modifier = modifier,
    )
}

/**
 * 带图标的设置/菜单列表项
 *
 * 封装 [BadgerListItem] + 左侧图标，适用于设置页、菜单列表等场景。
 *
 * @param title 标题
 * @param summary 摘要
 * @param icon 左侧图标
 * @param iconTint 图标颜色（默认 onSurfaceVariantSummary）
 * @param endContent 右侧内容槽
 * @param showArrow 是否显示箭头（默认 true）
 * @param onClick 点击回调
 * @param onClickLabel 点击动作的无障碍描述
 * @param role 点击语义角色
 */
@Composable
fun BadgerIconListItem(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    iconTint: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    endContent: (@Composable () -> Unit)? = null,
    showArrow: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    modifier: Modifier = Modifier,
) {
    BadgerListItem(
        title = title,
        summary = summary,
        startContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(end = BadgerSpacing.md)
                    .size(BadgerSize.iconMd),
            )
        },
        endContent = endContent,
        showArrow = showArrow,
        onClick = onClick,
        onClickLabel = onClickLabel,
        role = role,
        modifier = modifier,
    )
}
