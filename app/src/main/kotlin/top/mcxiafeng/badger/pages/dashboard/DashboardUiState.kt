package top.mcxiafeng.badger.pages.dashboard

/** UI-facing snapshot for the dashboard screen. */
data class DashboardUiState(
    val contactCount: Int = 0,
    val tagCount: Int = 0,
    val collectionCount: Int = 0,
    val recentContacts: List<DashboardRecentItem> = emptyList(),
    val loading: Boolean = false,
    val isLoggedIn: Boolean = false,
)

data class DashboardRecentItem(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null,
    val avatarPath: String? = null,
)
