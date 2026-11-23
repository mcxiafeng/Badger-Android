package top.mcxiafeng.badger.ui.navigation

import android.util.Log
import androidx.annotation.MainThread
import top.yukonga.miuix.kmp.nav.core.NavController

@MainThread
internal fun NavController.pushUnique(route: Route) {
    if (canOpenRoute(route)) push(route)
}

@MainThread
internal fun NavController.replaceUnique(route: Route) {
    if (canOpenRoute(route)) replace(route)
}

private fun NavController.canOpenRoute(route: Route): Boolean {
    // Miuix uses the route value as the content key across the entire back stack.
    if (route !in backStack) return true
    Log.d("RouteNavigationTester", "Ignored duplicate destination: ${route::class.simpleName}")
    return false
}
