package top.mcxiafeng.badger.shared.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object BadgerDispatchers {
    actual val io: CoroutineDispatcher = Dispatchers.IO
}
