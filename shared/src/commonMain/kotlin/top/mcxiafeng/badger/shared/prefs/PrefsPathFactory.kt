package top.mcxiafeng.badger.shared.prefs

/**
 * [KMP K05] DataStore 文件路径工厂（expect）。
 *
 * 各端 actual：
 * - Android：BadgerApplication 启动时 [storeAppDir] 注入 `context.filesDir` 绝对路径
 *   （expect 单例无法在无 Activity/Context 的 common 签名里拿 Context，采用启动期注入）。
 * - iOS：NSDocumentDirectory。
 * - JVM/桌面（仅测试）：java.io.tmpdir 兜底。
 *
 * 若未注入且运行在 Android，路径退化为相对目录 `datastore/`（App 自身 filesDir 下
 * 相对 CWD 也通常可写，但正常启动路径一定先注入）。
 */
expect object PrefsPathFactory {
    /** Android 启动期由 Application 注入的目录（iOS/JVM 忽略）。 */
    fun storeAppDir(dir: String)

    /** 返回 DataStore 根目录（不带尾分隔符），调用方自行拼接文件名。 */
    fun prefsDir(): String
}
