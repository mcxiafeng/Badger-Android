package top.mcxiafeng.badger.pages.setupguide

import top.mcxiafeng.badger.data.prefs.PrefsStore

internal const val TAG = "SetupGuide"

private const val KEY_SETUP_COMPLETED = "hint_shown_setup_guide_completed"

fun setSetupGuideCompleted() {
    PrefsStore.writeBoolean(KEY_SETUP_COMPLETED, true)
}
