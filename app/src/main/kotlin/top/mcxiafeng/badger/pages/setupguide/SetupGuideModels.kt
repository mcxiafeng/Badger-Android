package top.mcxiafeng.badger.pages.setupguide

import top.mcxiafeng.badger.data.prefs.PrefsStore

internal const val TAG = "SetupGuide"

private const val KEY_SETUP_COMPLETED = "hint_shown_setup_guide_completed"

/**
 * [KMP K05] DataStore（经 PrefsStore），原 badger_hints 文件。
 */
fun setSetupGuideCompleted(@Suppress("UNUSED_PARAMETER") context: android.content.Context) {
    PrefsStore.writeBoolean(KEY_SETUP_COMPLETED, true)
}
