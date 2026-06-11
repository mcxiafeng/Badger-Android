package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.ui.navigation.SettingsPage

private const val TAG = "SettingsSubPage"

@Composable
fun SettingsSubPage(page: SettingsPage, onBack: () -> Unit, onNavigateToSubPage: (SettingsPage) -> Unit, devMode: Boolean = false, onDevModeChange: (Boolean) -> Unit = {}) {
    Log.d(TAG, "SettingsSubPage: page=$page")
    when (page) {
        is SettingsPage.ShortLink -> ShortLinkSettingsPage(onBack)
        is SettingsPage.AiOcr -> AiOcrSettingsPage(onBack)
        is SettingsPage.UiSettings -> UiSettingsPage(onBack)
        is SettingsPage.CloudSync -> CloudSyncSettingsPage(onBack)
        is SettingsPage.About -> AboutPage(onBack, onNavigateToSubPage, devMode, onDevModeChange)
        is SettingsPage.OpenSourceLicense -> OpenSourceLicensePage(onBack)
        is SettingsPage.AppLog -> LogViewerPage(onBack)
        is SettingsPage.ContactUs -> ContactUsPage(onBack)
    }
}
