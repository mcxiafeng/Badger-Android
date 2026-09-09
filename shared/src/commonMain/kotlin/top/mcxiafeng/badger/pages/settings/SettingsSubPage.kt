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

/**
 * 设置二级路由分发。
 *
 * 标题 / 图标不再在此硬编码——子页 TopBar 直接取 `page.title`，
 * 入口图标取 `page.icon`（见 [SettingsPage] 元数据）。
 *
 * 注：`onNavigateToMyProfile` 在 B1 清理死参数链后移除（PlatformList 已删，无消费者）。
 */
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
        // [B2] 站内通知列表
        is SettingsPage.Notifications -> NotificationPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToContact = onNavigateToContact,
        )
        // [B4] 已登录设备管理
        is SettingsPage.Devices -> DeviceListPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
        )
        // [C1] Dashboard 统计概览
        is SettingsPage.Dashboard -> DashboardPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToContact = onNavigateToContact,
        )
        // 修改密码
        is SettingsPage.ChangePassword -> ChangePasswordPage(onBack = onBack)
        // 用户设置（云端偏好：语言/主题/通知邮件/短链配置）
        is SettingsPage.UserSettings -> UserSettingsPage(onBack = onBack, onNavigateToSubPage = onNavigateToSubPage)
        // 自建短链管理
        is SettingsPage.ServerShortLinks -> ServerShortLinkPage(
            onBack = onBack,
            onNavigateToLogin = onNavigateToLogin,
        )
    }
}
