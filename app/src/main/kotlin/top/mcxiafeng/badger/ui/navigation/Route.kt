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
    data object AiOcr : SettingsPage()
    data object UiSettings : SettingsPage()
    data object CloudSync : SettingsPage()
    data object About : SettingsPage()
    data object OpenSourceLicense : SettingsPage()
    data object AppLog : SettingsPage()
    data object ContactUs : SettingsPage()
    data object TagManager : SettingsPage()
}