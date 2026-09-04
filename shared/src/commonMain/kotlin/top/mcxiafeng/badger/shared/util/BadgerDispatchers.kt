package top.mcxiafeng.badger.shared.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * [KMP K08-B] 平台 IO 调度器（common 抽象）。
 *
 * common 代码不可访问 `Dispatchers.IO`（kotlinx-coroutines 的 IO 是 JVM 专属扩展）。
 * Android actual = Dispatchers.IO（行为零变化）；iOS actual = Dispatchers.Default
 * （KMP 惯例：kotlinx-coroutines 未为 native 提供 IO 池，Default 的线程数 ≈ CPU 核数，
 * DB/网络挂起式调用在 Default 下语义等价）。
 */
expect object BadgerDispatchers {
    val io: CoroutineDispatcher
}
