package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.ui.navigation.SettingsPage

private const val TAG = "SettingsSubPage"

@Composable
fun SettingsSubPage(
    page: SettingsPage,
    onBack: () -> Unit,
    onNavigateToSubPage: (SettingsPage) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToMyProfile: () -> Unit = {},
    devMode: Boolean = false,
    onDevModeChange: (Boolean) -> Unit = {},
) {
    Log.d(TAG, "SettingsSubPage: page=$page")
    when (page) {
        // 合并页：所有入口统一收敛到这三个 Composable
        is SettingsPage.AccountAndBackup -> AccountAndBackupPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
        )
        is SettingsPage.GeneralSettings -> GeneralSettingsPage(
            onBack = onBack,
            onNavigateToSubPage = onNavigateToSubPage,
        )
        is SettingsPage.PlatformList -> PlatformListPage(
            onBack = onBack,
            onNavigateToAdd = onNavigateToMyProfile,
        )

        // 独立页（保留各自唯一入口）
        is SettingsPage.NfcSettings -> NfcSettingsPage(onBack)
        is SettingsPage.UiSettings -> UiSettingsPage(onBack)
        is SettingsPage.About -> AboutPage(onBack, onNavigateToSubPage, devMode, onDevModeChange)
        is SettingsPage.OpenSourceLicense -> OpenSourceLicensePage(onBack)
        is SettingsPage.AppLog -> LogViewerPage(onBack)
        is SettingsPage.TagManager -> TagManagerSettingsPage(onBack)
    }
}