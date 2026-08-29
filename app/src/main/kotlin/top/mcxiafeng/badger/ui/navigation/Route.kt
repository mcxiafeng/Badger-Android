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
    // [V2-P9] 同步状态:批量重试 + 电池优化引导(抗 OEM WorkManager 禁用)
    data object SyncStatus : SettingsPage()
    // [B2] 站内通知列表：已读 / 删除 / 未读角标
    data object Notifications : SettingsPage()
    // [B4] 已登录设备管理：列表 / 重命名 / 注销
    data object Devices : SettingsPage()
    // [C1] Dashboard 统计概览：联系人 / 标签 / 名片夹 + 最近添加
    data object Dashboard : SettingsPage()
}
