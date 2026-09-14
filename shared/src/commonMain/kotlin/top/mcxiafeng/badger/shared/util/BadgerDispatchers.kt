package top.mcxiafeng.badger.shared.util

import kotlinx.coroutines.CoroutineDispatcher

expect object BadgerDispatchers {
    val io: CoroutineDispatcher
}
