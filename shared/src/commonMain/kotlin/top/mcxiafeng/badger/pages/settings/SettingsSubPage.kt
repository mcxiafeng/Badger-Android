package top.mcxiafeng.badger.pages.settings

import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.pages.settings.account.AccountProfilePage
import top.mcxiafeng.badger.pages.settings.account.ChangePasswordPage
import top.mcxiafeng.badger.pages.dashboard.DashboardPage
import top.mcxiafeng.badger.pages.settings.devices.DeviceListPage
import top.mcxiafeng.badger.pages.settings.history.OperationHistoryPage
import top.mcxiafeng.badger.pages.settings.notification.NotificationPage
import top.mcxiafeng.badger.pages.settings.sync.ServerShortLinkPage
import top.mcxiafeng.badger.pages.settings.sync.SyncStatusPage
import top.mcxiafeng.badger.pages.settings.tags.TagManagerSettingsPage
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.BadgerLog

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
    BadgerLog.d(TAG, "SettingsSubPage: page=$page")
    when (page) {
        is SettingsPage.AccountProfile -> AccountProfilePage(onBack, onNavigateToSubPage)
        is SettingsPage.UiSettings -> UiSettingsPage(onBack)
        is SettingsPage.About -> AboutPage(onBack, onNavigateToSubPage, devMode, onDevModeChange)
        is SettingsPage.OpenSourceLicense -> OpenSourceLicensePage(onBack)
        is SettingsPage.AppLog -> LogViewerPage(onBack)
        is SettingsPage.ContactUs -> ContactUsPage(onBack)
        is SettingsPage.TagManager -> TagManagerSettingsPage(onBack)
        is SettingsPage.OperationHistory -> OperationHistoryPage(onBack)
        is SettingsPage.SyncStatus -> SyncStatusPage(onBack)
        
        is SettingsPage.Notifications -> NotificationPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToContact = onNavigateToContact,
        )
        
        is SettingsPage.Devices -> DeviceListPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
        )
        
        is SettingsPage.Dashboard -> DashboardPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToContact = onNavigateToContact,
        )
        
        is SettingsPage.ChangePassword -> ChangePasswordPage(onBack = onBack)
        
        is SettingsPage.UserSettings -> UserSettingsPage(onBack = onBack, onNavigateToSubPage = onNavigateToSubPage)
        
        is SettingsPage.ServerShortLinks -> ServerShortLinkPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
        )
    }
}
