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

@Composable
internal fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    
    
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        content()
    }
}

@Composable
internal fun BasicInfoCard(
    fields: List<PersonFieldDisplay>,
    onCellClick: (fieldKey: String, currentValue: String?) -> Unit = { _, _ -> },
) {
    val byKey = remember(fields) { fields.associateBy { it.fieldKey } }

    
    val row1 = listOf(
        BasicInfoCellRef("gender", "性别", Lucide.Transgender),
        BasicInfoCellRef("birthday", "生日", Lucide.Cake),
    )
    val row2 = listOf(
        BasicInfoCellRef("country", "国家", Lucide.Flag),
        BasicInfoCellRef("region", "地区", Lucide.MapPin),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        
        
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            BasicInfoRow(cells = row1, byKey = byKey, onCellClick = onCellClick)
            Spacer(modifier = Modifier.height(8.dp))
            BasicInfoRow(cells = row2, byKey = byKey, onCellClick = onCellClick)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

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
                
                modifier = Modifier.weight(1f),
            )
        }
    }
}

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
                
                
                
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                
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

internal data class BasicInfoCellRef(
    val key: String,
    val label: String,
    val icon: ImageVector,
)
