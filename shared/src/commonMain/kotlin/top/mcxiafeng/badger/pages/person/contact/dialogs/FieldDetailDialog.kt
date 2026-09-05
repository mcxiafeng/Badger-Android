package top.mcxiafeng.badger.pages.person.contact.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.data.model.PersonFieldDisplay
import top.mcxiafeng.badger.ocr.LaunchAction
import top.mcxiafeng.badger.ocr.buildLaunchAction
import top.mcxiafeng.badger.ui.components.LaunchActionButtons
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "FieldDetailDialog"

/**
 * 联系方式详情弹窗
 *
 * 显示字段值，根据 LaunchAction 提供跳转/扫码添加/复制并打开等操作。
 */
@Composable
fun FieldDetailDialog(
    field: PersonFieldDisplay,
    show: Boolean,
    onDismiss: () -> Unit
) {
    val fieldKey = field.fieldKey
    val launchAction = remember(fieldKey, field.value) {
        if (fieldKey.isNullOrBlank()) LaunchAction.None
        else buildLaunchAction(fieldKey, field.value)
    }

    if (show) WindowDialog(
        show = true,
        title = field.fieldName,
        summary = "长按可复制",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            DetailInfoRow(
                label = field.fieldName,
                value = field.value,

            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                LaunchActionButtons(
                    launchAction = launchAction,
                    onDismiss = onDismiss
                )
            }
        }
    }
}
