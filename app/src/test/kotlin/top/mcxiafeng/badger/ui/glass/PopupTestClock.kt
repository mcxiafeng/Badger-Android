package top.mcxiafeng.badger.ui.glass

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.robolectric.shadows.ShadowLooper

/** Native Dialog roots also have Android UI work; drain it without advancing animation time. */
internal fun ComposeContentTestRule.flushPopupUi() {
    runOnUiThread {
        Snapshot.sendApplyNotifications()
        ShadowLooper.idleMainLooper()
    }
}

internal fun ComposeContentTestRule.advancePopupFrame() {
    flushPopupUi()
    mainClock.advanceTimeByFrame()
    flushPopupUi()
}

/** Apply caller state, commit the host target, then supply the Animatable's play-time-zero frame. */
internal fun ComposeContentTestRule.startPopupMotionFrames() {
    repeat(3) { advancePopupFrame() }
}
