package top.mcxiafeng.badger.ui

/**
 * [B2] 未读角标文案。0 及以下不展示；超过 99 收成 `99+`，避免撑破 NavBar 图标。
 */
fun formatUnreadBadge(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}
