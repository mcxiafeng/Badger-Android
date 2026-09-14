package top.mcxiafeng.badger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Info

@Composable
fun FirstTimeHint(
    text: String,
    hintKey: String,
    modifier: Modifier = Modifier
) {
    var shown by remember(hintKey) {
        mutableStateOf(PrefsStore.readBoolean("hint_shown_$hintKey", false))
    }

    if (!shown) {
        Row(
            modifier = modifier
                .clickable {
                    shown = true
                    PrefsStore.writeBoolean("hint_shown_$hintKey", true)
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Lucide.Info,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 1.33.em
            )
        }
    }
}
