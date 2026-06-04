package top.mcxiafeng.badger.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NavigationDirection {
    FORWARD,
    BACKWARD,
    RESET
}

class AppNavigator {
    private val _currentRoute = MutableStateFlow<Route>(Route.MainTabs)
    val currentRoute: StateFlow<Route> = _currentRoute.asStateFlow()

    private val _routeStack = java.util.Collections.synchronizedList(mutableListOf<Route>())

    var navigationDirection: NavigationDirection = NavigationDirection.FORWARD
        private set

    fun navigate(route: Route) {
        _routeStack.add(_currentRoute.value)
        navigationDirection = NavigationDirection.FORWARD
        _currentRoute.value = route
    }

    fun navigateBack(): Boolean {
        if (_routeStack.isEmpty()) return false
        navigationDirection = NavigationDirection.BACKWARD
        _currentRoute.value = _routeStack.removeAt(_routeStack.lastIndex)
        return true
    }

    fun resetToMain() {
        _routeStack.clear()
        navigationDirection = NavigationDirection.RESET
        _currentRoute.value = Route.MainTabs
    }

}
