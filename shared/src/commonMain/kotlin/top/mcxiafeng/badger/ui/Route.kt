package top.mcxiafeng.badger.ui

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {

    //一级
    @Serializable
    data object Main : Route
    //二级
    @Serializable
    data object Sync : Route
}