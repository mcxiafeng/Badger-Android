package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.pages.settings.account.AccountProfilePage
import top.mcxiafeng.badger.pages.settings.account.ChangePasswordPage
import top.mcxiafeng.badger.pages.settings.devices.DeviceListPage
import top.mcxiafeng.badger.pages.settings.NfcSettingsPage
import top.mcxiafeng.badger.pages.settings.history.OperationHistoryPage
import top.mcxiafeng.badger.pages.settings.notification.NotificationPage
import top.mcxiafeng.badger.pages.settings.sync.ServerShortLinkPage
import top.mcxiafeng.badger.pages.settings.sync.SyncStatusPage
import top.mcxiafeng.badger.pages.settings.tags.TagManagerSettingsPage
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
        // 修改密码
        is SettingsPage.ChangePassword -> ChangePasswordPage(onBack = onBack)
        // 用户设置同步（占位，Task 2 完善）
        is SettingsPage.UserSettings -> {}
        // 自建短链管理
        is SettingsPage.ServerShortLinks -> ServerShortLinkPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
        )
    }
}
