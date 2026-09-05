package top.mcxiafeng.badger.di

import org.koin.core.Koin
import org.koin.core.error.KoinApplicationAlreadyStartedException
import org.koin.mp.KoinPlatformTools

/**
 * [§14.2] Koin 静态工具助手,VM 不再继承 [org.koin.core.component.KoinComponent]。
 *
 * 背景:Koin 4.0 同时在 [org.koin.core.component.KoinComponent] 与
 * [android.content.ComponentCallbacks] 上声明 `get<T>()` extension。`ViewModel`
 * 同时继承两者时,编译器在两套同签名 extension 间无法推断 ⇒ `Cannot infer type for type parameter 'T'`。
 *
 * 修法(方案 C):
 * 1. **VM 彻底不再实现 `KoinComponent`,只接 `android.content.ComponentCallbacks`**
 *    (消除双接收器歧义)
 * 2. **VM 字段直接通过 `org.koin.android.ext.android.get<T>()` 拿依赖** ——
 *    该工具方法本身**无 receiver**,通过 `GlobalContext` 静态取单例,所以
 *    在字段初始化器里写 `= org.koin.android.ext.android.get<T>()` 即可。
 *
 * 本文件保留:
 * - `KoinComponentBy.get<T>()` —— 静态方法,无 receiver,内部转发 `GlobalContext.get().get<T>()`。
 *   VM 字段处写 `= KoinComponentBy.get<T>()` 调用无歧义路径(等价于
 *   `org.koin.android.ext.android.get<T>()`)。
 * - `KoinComponentBy.koin` —— 静态 [Koin] 句柄,供 [ContactNetworkResolver] /
 *   [HttpUtil] 等静态 compat 层使用,等价于原 Hilt `EntryPointAccessors.fromApplication`。
 *
 * 测试兼容:Robolectric 跨测试共享 Koin 上下文;每个测试 setUp 已 stop+start Koin 一次,
 * 单元测试运行期 GlobalContext 必然是已 startKoin 状态,直接 `get()` 命中即可。
 * 未启动场景下 fallback [KoinPlatformTools] defaultContext(例如 release 包没装 Koin)。
 */
object KoinComponentBy {

    /** 静态 `get<T>()` —— 无 receiver,内部 [GlobalContext] 取 [Koin] 解析 `<T>`。 */
    inline fun <reified T : Any> get(): T =
        currentKoin().get<T>()

    /** 静态 [Koin] 句柄 — `GlobalContext.get()` 单例返回值,可在 `object` 内 `KoinComponentBy.koin.get<T>()`。 */
    val koin: Koin
        get() = currentKoin()

    @PublishedApi
    internal fun currentKoin(): Koin {
        return runCatching { KoinPlatformTools.defaultContext().get() }.getOrElse { err ->
            // [修复防御]: 已经 startKoin 但 register 抛错时,直接重抛 KoinApplicationAlreadyStartedException;
            // 未启动场景下 fallback 到 defaultContext(单测里几乎不会命中,但保留兜底)。
            if (err is KoinApplicationAlreadyStartedException || err is IllegalStateException) {
                KoinPlatformTools.defaultContext().get()
            } else {
                throw err
            }
        }
    }
}