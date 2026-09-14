package top.mcxiafeng.badger.pages.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BadgerSwipeRow(
    content: @Composable () -> Unit,
    onDelete: () -> Unit,
    deleteText: String = "删除",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled) {
        content()
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 2.dp)
                    .background(
                        color = MiuixTheme.colorScheme.error,
                        shape = miuixShape(12.dp),
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = deleteText,
                    color = MiuixTheme.colorScheme.onError,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    ) {
        content()
    }
}
