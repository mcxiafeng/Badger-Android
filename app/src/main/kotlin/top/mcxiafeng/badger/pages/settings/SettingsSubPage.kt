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
    onNavigateToContact: (Long) -> Unit = {},
    devMode: Boolean = false,
    onDevModeChange: (Boolean) -> Unit = {},
) {
    Log.d(TAG, "SettingsSubPage: page=$page")
    when (page) {
        is SettingsPage.AccountProfile -> AccountProfilePage(onBack, onNavigateToSubPage)
        is SettingsPage.ServerSettings -> ServerSettingsPage(onBack)
        is SettingsPage.NfcSettings -> NfcSettingsPage(onBack)
        is SettingsPage.UiSettings -> UiSettingsPage(onBack)
        is SettingsPage.About -> AboutPage(onBack, onNavigateToSubPage, devMode, onDevModeChange)
        is SettingsPage.OpenSourceLicense -> OpenSourceLicensePage(onBack)
        is SettingsPage.AppLog -> LogViewerPage(onBack)
        is SettingsPage.ContactUs -> ContactUsPage(onBack)
        is SettingsPage.TagManager -> TagManagerSettingsPage(onBack)
        is SettingsPage.OperationHistory -> OperationHistoryPage(onBack)
        is SettingsPage.SyncStatus -> SyncStatusPage(onBack)
        // [B2] 站内通知列表
        is SettingsPage.Notifications -> NotificationPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToContact = onNavigateToContact,
        )
        is SettingsPage.PlatformList -> PlatformListPage(
            onBack = onBack,
            onNavigateToAdd = onNavigateToMyProfile,
        )
        // [B4] 已登录设备管理
        is SettingsPage.Devices -> DeviceListPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
        )
        // [C1] Dashboard 统计概览
        is SettingsPage.Dashboard -> top.mcxiafeng.badger.pages.dashboard.DashboardPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
        )
    }
}
