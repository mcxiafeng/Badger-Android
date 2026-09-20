package top.mcxiafeng.badger.ui

import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

class Navigator(
    val backStack: NavBackStack,
) {
    fun push(key: NavKey) {
        if (key !in backStack) {
            backStack.add(key)
        }
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun current() = backStack.lastOrNull()

    fun backStackSize() = backStack.size
}