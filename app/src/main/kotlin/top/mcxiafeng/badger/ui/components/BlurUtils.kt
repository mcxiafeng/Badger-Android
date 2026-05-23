package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.unit.dp

private const val TAG = "BlurUtils"

@Composable
fun BlurredNavBar(
    backdrop: Backdrop?,
    blurEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    Log.d(TAG, "BlurredNavBar: blurEnabled=$blurEnabled, backdrop=$backdrop")
    if (blurEnabled && backdrop != null) {
        val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                },
                onDrawSurface = { drawRect(containerColor) }
            )
        ) {
            content()
        }
    } else {
        content()
    }
}