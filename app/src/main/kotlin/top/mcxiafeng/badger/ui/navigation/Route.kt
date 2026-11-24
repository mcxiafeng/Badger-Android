package top.mcxiafeng.badger.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

// Miuix Nav：Route 需实现 NavKey；@Serializable 是 rememberNavBackStack 的硬性要求，
// 路由栈在配置变更/进程重建后经 kotlinx-serialization 恢复。
@Serializable
sealed class Route : NavKey {
    @Serializable data object MainTabs : Route()
    @Serializable data class Scanner(val mode: String? = null, val targetCollectionId: Long? = null) : Route()
    @Serializable data object ContactSearch : Route()
    @Serializable data class ContactDetail(val contactId: Long) : Route()
    @Serializable data class AddPlatform(val contactId: Long) : Route()
    @Serializable data object Collections : Route()
    @Serializable data class CollectionDetail(val collectionId: Long) : Route()
    @Serializable data class CreateContact(val targetCollectionId: Long? = null) : Route()
    @Serializable data object Settings : Route()
    @Serializable data class SettingsSubPage(val page: SettingsPage) : Route()
    @Serializable data class Accounts(val section: String = "overview") : Route()
    @Serializable data object AccountSwitch : Route()
    @Serializable data object Auth : Route()
    @Serializable data object ForgotPassword : Route()
    @Serializable data object ChoiceServer : Route()
    @Serializable data object ServerAddresses : Route()
}

@Serializable
sealed class SettingsPage {
    @Serializable data object NfcSettings : SettingsPage()
    @Serializable data object AiOcr : SettingsPage()
    @Serializable data object Gaode : SettingsPage()
    @Serializable data object UiSettings : SettingsPage()
    @Serializable data object About : SettingsPage()
    @Serializable data object OpenSourceLicense : SettingsPage()
    @Serializable data object AppLog : SettingsPage()
    @Serializable data object ContactUs : SettingsPage()
    @Serializable data object TagManager : SettingsPage()
}
