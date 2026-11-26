package top.mcxiafeng.badger.ui.glass

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion

private val LocalRetainedPopups = staticCompositionLocalOf<RetainedPopupRegistry?> { null }
private val LocalPopupParentMotion = staticCompositionLocalOf<PopupMotionState?> { null }

@Stable
private class RetainedPopupEntry {
    val motion = PopupMotionState()
    var locals by mutableStateOf<CompositionLocalContext?>(null)
    var render by mutableStateOf<(@Composable (PopupMotionState) -> Unit)?>(null)
    var onFinished: (() -> Unit)? = null
}

@Stable
private class RetainedPopupRegistry {
    val entries = mutableStateListOf<RetainedPopupEntry>()
}

/** Lives above page/dialog `if` branches, so their removal can start an exit instead of cutting it. */
@Composable
internal fun RetainedPopupHost(content: @Composable () -> Unit) {
    val registry = remember { RetainedPopupRegistry() }
    CompositionLocalProvider(LocalRetainedPopups provides registry, content = content)
    registry.entries.toList().forEach { entry ->
        key(entry) { RetainedPopupEntry(registry, entry) }
    }
}

@Composable
private fun RetainedPopupEntry(registry: RetainedPopupRegistry, entry: RetainedPopupEntry) {
    val visible = entry.motion.visible
    LaunchedEffect(visible) {
        Log.d("PopupTransitionTester", "Popup transition start; entering=$visible; durationMs=${BadgerMotion.DURATION_TOUCH_FEEDBACK}; rebound=true")
        entry.motion.progress.animateTo(if (visible) 1f else 0f, popupMotionSpec(visible))
        Log.d("PopupTransitionTester", "Popup transition complete; entering=$visible; progress=${entry.motion.progress.value}")
        if (!visible && !entry.motion.visible) {
            val finished = entry.onFinished
            entry.onFinished = null
            Snapshot.withMutableSnapshot {
                registry.entries.remove(entry)
                entry.render = null
                entry.locals = null
            }
            finished?.invoke()
            Log.d("PopupTransitionTester", "Popup released after exit")
        }
    }
    val locals = entry.locals
    val render = entry.render
    if (locals != null && render != null) {
        CompositionLocalProvider(locals) {
            CompositionLocalProvider(LocalPopupParentMotion provides entry.motion) { render(entry.motion) }
        }
    }
}

/** Keeps the caller's last registered content configuration until the host's 239ms exit finishes. */
@Composable
internal fun RetainedPopup(
    show: Boolean,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable (PopupMotionState) -> Unit,
) {
    val registry = LocalRetainedPopups.current
    if (registry == null) {
        // Isolated previews/tests can mount locally; the app supplies the persistent outer host.
        RetainedPopupHost { RetainedPopup(show, onDismissFinished, content) }
        return
    }
    val entry = remember(registry) { RetainedPopupEntry() }
    val locals = currentCompositionLocalContext
    val visible = show && LocalPopupParentMotion.current?.visible != false
    SideEffect {
        entry.onFinished = onDismissFinished
        Snapshot.withMutableSnapshot {
            if (visible) {
                entry.locals = locals
                entry.render = content
                if (entry !in registry.entries) registry.entries.add(entry)
            }
            if (entry.motion.visible != visible) Log.d("PopupTransitionTester", "Popup target changed; visible=$visible")
            entry.motion.visible = visible
        }
    }
    DisposableEffect(registry, entry) {
        onDispose {
            Log.d("PopupTransitionTester", "Popup caller disposed; visible=${entry.motion.visible}; progress=${entry.motion.progress.value}")
            Snapshot.withMutableSnapshot { entry.motion.visible = false }
        }
    }
}
