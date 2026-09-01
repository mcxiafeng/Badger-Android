package top.mcxiafeng.badger.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NavigationDirection {
    FORWARD,
    BACKWARD,
    RESET,
}

/**
 * Small synchronized stack navigator used by the app root.
 * Re-selecting the current destination is a no-op so repeated taps cannot create
 * duplicate back-stack entries.
 */
class AppNavigator {
    private val lock = Any()
    private val _currentRoute = MutableStateFlow<Route>(Route.MainTabs)
    val currentRoute: StateFlow<Route> = _currentRoute.asStateFlow()

    private val routeStack = mutableListOf<Route>()

    var navigationDirection: NavigationDirection = NavigationDirection.FORWARD
        private set

    fun navigate(route: Route) {
        synchronized(lock) {
            if (_currentRoute.value == route) return
            routeStack += _currentRoute.value
            navigationDirection = NavigationDirection.FORWARD
            _currentRoute.value = route
        }
    }

    fun navigateBack(): Boolean {
        synchronized(lock) {
            if (routeStack.isEmpty()) return false
            navigationDirection = NavigationDirection.BACKWARD
            _currentRoute.value = routeStack.removeAt(routeStack.lastIndex)
            return true
        }
    }

    fun resetToMain() {
        synchronized(lock) {
            routeStack.clear()
            navigationDirection = NavigationDirection.RESET
            _currentRoute.value = Route.MainTabs
        }
    }
}
