package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.repository.GaodeDistrict
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 列表/加载区域的统一高度,保证弹窗尺寸稳定不跳动。 */
internal val LIST_HEIGHT = 260.dp

/** 加载态:撑满宽度后居中转圈(不撑满宽度会让转圈靠左偏移)。 */
@Composable
internal fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(LIST_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

/**
 * 搜索框:过滤当前层级列表。
 *
 * 复用 miuix [TextField];非空时在尾部显示清空按钮(可点击 Box + Icon,
 * 带读屏标签,避免引入额外依赖)。
 */
@Composable
internal fun RegionSearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = keyword,
        onValueChange = { onKeywordChange(it) },
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clickable(
                            onClickLabel = "清空搜索",
                            role = Role.Button,
                            onClick = { onKeywordChange("") },
                        )
                        .size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清空搜索",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * 国家行:点选即确认,当前项在行尾显示勾号。
 *
 * 用 miuix [BasicComponent] 统一行排版、按压反馈与无障碍语义。
 */
@Composable
internal fun CountryRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = name,
        onClick = onClick,
        onClickLabel = name,
        role = Role.Button,
        endActions = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "当前选择",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/** 行政区行:地点图标 + 名称,点击下钻。 */
@Composable
internal fun DistrictRow(
    district: GaodeDistrict,
    onClick: () -> Unit,
) {
    BasicComponent(
        startAction = {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        },
        title = district.name,
        onClick = onClick,
        onClickLabel = district.name,
        role = Role.Button,
    )
}
