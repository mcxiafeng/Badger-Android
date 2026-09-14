package top.mcxiafeng.badger.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.MonitorSmartphone
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.UserCog
import com.composables.icons.lucide.UserRound

sealed class Route {
    data object MainTabs : Route()

    
    data object Auth : Route()

    
    data object ForgotPassword : Route()
    data class Scanner(val mode: String? = null, val targetCollectionId: Long? = null) : Route()
    data class ContactDetail(val contactId: Long) : Route()
    data class CollectionDetail(val collectionId: Long) : Route()
    data class CreateContact(val targetCollectionId: Long? = null) : Route()
    data class SettingsSubPage(val page: SettingsPage) : Route()
}

sealed class SettingsPage(val title: String, val icon: ImageVector) {
    
    data object AccountProfile : SettingsPage("账号", Lucide.UserRound)
    data object Dashboard : SettingsPage("统计概览", Lucide.LayoutDashboard)
    data object SyncStatus : SettingsPage("同步状态", Lucide.RefreshCw)
    data object OperationHistory : SettingsPage("操作历史", Lucide.History)
    data object TagManager : SettingsPage("标签管理", Lucide.Tag)
    data object ServerShortLinks : SettingsPage("自建短链", Lucide.Link)
    data object UserSettings : SettingsPage("用户设置", Lucide.UserCog)
    data object UiSettings : SettingsPage("界面与导航", Lucide.Palette)
    data object About : SettingsPage("关于 Badger", Lucide.Info)
    
    data object ChangePassword : SettingsPage("修改密码", Lucide.KeyRound)
    data object Devices : SettingsPage("已登录设备", Lucide.MonitorSmartphone)
    data object OpenSourceLicense : SettingsPage("开源许可", Lucide.Scale)
    data object AppLog : SettingsPage("软件日志", Lucide.ScrollText)
    data object ContactUs : SettingsPage("联系我们", Lucide.MessageCircle)
    
    data object Notifications : SettingsPage("消息中心", Lucide.Bell)
}
