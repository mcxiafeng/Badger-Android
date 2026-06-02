package top.mcxiafeng.badger.ui.navigation

sealed class Route {
    data object MainTabs : Route()
    data class Scanner(val mode: String? = null, val targetCollectionId: Long? = null) : Route()
    data class ContactDetail(val contactId: Long) : Route()
    data class CollectionDetail(val collectionId: Long) : Route()
    data class CreateContact(val targetCollectionId: Long? = null) : Route()
    data class SettingsSubPage(val page: String) : Route()
}