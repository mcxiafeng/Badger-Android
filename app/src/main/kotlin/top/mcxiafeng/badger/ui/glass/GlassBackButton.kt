package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.Icon

/** Navigation opts into glass independently of the page's flat content buttons. */
@Composable
internal fun GlassBackButton(onBack: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val style = LocalGlassStyle.current
    LaunchedEffect(style.enabled) { Log.d(GLASS_LOG_TAG, "Back button; glass=${style.enabled}; independentOfContentButtons=true") }
    CompositionLocalProvider(LocalGlassStyle provides style.copy(glassButtons = true)) {
        IconButton(onClick = {
            Log.d(GLASS_LOG_TAG, "Back button pressed")
            onBack()
        }, enabled = enabled, modifier = modifier,
            minWidth = BadgerSize.minimumTouchTarget, minHeight = BadgerSize.minimumTouchTarget) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
        }
    }
}
