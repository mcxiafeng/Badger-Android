package top.mcxiafeng.badger.ui

fun formatUnreadBadge(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}
