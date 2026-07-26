package top.mcxiafeng.badger.ui.navigation

sealed class Route {
    data object MainTabs : Route()
    data object Login : Route()
    data object Register : Route()
    data class Scanner(val mode: String? = null, val targetCollectionId: Long? = null) : Route()
    data class ContactDetail(val contactId: Long) : Route()
    data class CollectionDetail(val collectionId: Long) : Route()
    data class CreateContact(val targetCollectionId: Long? = null) : Route()
    data class SettingsSubPage(val page: SettingsPage) : Route()
}

sealed class SettingsPage {
    data object NfcSettings : SettingsPage()
    data object UiSettings : SettingsPage()
    data object About : SettingsPage()
    data object OpenSourceLicense : SettingsPage()
    data object AppLog : SettingsPage()
    data object ContactUs : SettingsPage()
    data object TagManager : SettingsPage()
    data object PlatformList : SettingsPage()
    // [V2-P7] 操作历史:撤销 / 重发 / 解决冲突
    data object OperationHistory : SettingsPage()
    // 个人信息页：账号信息 / 昵称简介 / 退出登录
    data object AccountProfile : SettingsPage()
    // 服务器设置一级页：服务器地址 + 修改服务器地址
    data object ServerSettings : SettingsPage()
}
