package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ocr.ADDABLE_PLATFORMS
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "Tester"

/**
 * 平台图标网格选择器（Phase 1）
 *
 * 展示所有可添加平台的图标网格，包括底部的"自定义"选项。
 * 已添加的平台会灰显禁用。
 *
 * @param existingPlatformKeys 已添加平台的 fieldKey 集合（灰显禁用）
 * @param onSelect 选择预设平台的回调，参数为 fieldKey
 * @param onCustom 选择自定义平台的回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformGridSelector(
    existingPlatformKeys: Set<String>,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ADDABLE_PLATFORMS.forEach { def ->
            val isExisting = def.fieldKey in existingPlatformKeys
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(enabled = !isExisting) {
                        onSelect(def.fieldKey)
                        Log.d(TAG, "选择平台: ${def.fieldKey}")
                    }
                    .padding(8.dp)
            ) {
                PlatformIcon(
                    fieldKey = def.fieldKey,
                    color = if (isExisting) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f) else MiuixTheme.colorScheme.primary,
                    sizeDp = 32f
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = def.displayName,
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (isExisting) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f) else MiuixTheme.colorScheme.onBackground,
                    maxLines = 1
                )
            }
        }
        // + 自定义
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable {
                    onCustom()
                    Log.d(TAG, "选择自定义平台")
                }
                .padding(8.dp)
        ) {
            PlatformIcon(
                fieldKey = "website",
                color = MiuixTheme.colorScheme.primary,
                sizeDp = 32f
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "自定义",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1
            )
        }
    }
}
