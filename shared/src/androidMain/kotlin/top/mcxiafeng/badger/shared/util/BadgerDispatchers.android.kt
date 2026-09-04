package top.mcxiafeng.badger.shared.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * [KMP K08-B] Android actual：Dispatchers.IO（行为零变化）。
 */
actual object BadgerDispatchers {
    actual val io: CoroutineDispatcher = Dispatchers.IO
}
