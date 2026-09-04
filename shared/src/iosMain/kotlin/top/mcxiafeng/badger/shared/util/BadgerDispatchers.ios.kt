package top.mcxiafeng.badger.shared.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * [KMP K08-B] iOS actual：Dispatchers.Default（kotlinx-coroutines native 无 IO 池；
 * 挂起式 DB/网络调用在 Default 下语义等价）。
 */
actual object BadgerDispatchers {
    actual val io: CoroutineDispatcher = Dispatchers.Default
}
