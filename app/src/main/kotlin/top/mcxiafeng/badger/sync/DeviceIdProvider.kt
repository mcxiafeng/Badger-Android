package top.mcxiafeng.badger.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import java.util.UUID

/**
 * [V2-P3] 设备 ID 提供器。
 *
 * 单设备稳定 UUID,用于:
 * - `operation_history` 多设备冲突排查
 * - 服务端 S13 "anti-revoke" 校验(若采用)
 *
 * 与 [top.mcxiafeng.badger.data.AuthPrefs] 的区别:
 * - AuthPrefs 保存 token / 服务器 URL,**敏感**,登出/换账号时清空。
 * - 本类保存匿名 device UUID,**非敏感**,即使登出也不应清空(否则多设备冲突排查断链)。
 *
 * **不要**用 `android.provider.Settings.Secure.ANDROID_ID`:
 * - 厂商定制(小米/华为)会改 ANDROID_ID,跨设备用户看到同一个 ID。
 * - 重置后用户期待"像新设备一样",应保持能自增。
 *
 * **不要**用纯随机 UUID:卸载重装后丢失,导致服务端看到同一用户出现"新设备",
 * 与 anti-revoke 机制冲突。
 *
 * 设计要点(对应 `docs/BADGER_V2_CLIENT_PLAN.md` §4 + §5.5.1):
 * 1. 一次性生成,落 SharedPreferences(`badger_device`),后续全部读缓存。
 * 2. Koin 单例,Application 启动期由 `startKoin{}` 内注册,无需在 BadgerApplication.onCreate
 *    显式调 init。
 * 3. 提供 [resetForTesting],仅单元测试或开发者设置页"重置设备"按钮调用。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor(@ApplicationContext ...)` → Koin
 * `singleOf(::DeviceIdProvider)`。
 */
class DeviceIdProvider(
    private val context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 取当前设备 ID。首次调用时若 SharedPreferences 没有,生成新的 UUID 并落盘。
     *
     * 内存缓存避免每次写 history 都做 prefs.getString。
     */
    fun deviceId(): String {
        cachedId?.let { return it }
        val stored = prefs.getString(KEY_DEVICE_ID, null)
        val resolved = if (stored.isNullOrBlank()) {
            val fresh = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_DEVICE_ID, fresh) }
            Log.i(TAG, "DeviceIdProvider: generated new deviceId=${takePrefix(fresh)}")
            fresh
        } else stored
        cachedId = resolved
        return resolved
    }

    /**
     * 重置设备 ID。仅供:
     * - 单元测试(多设备模拟);
     * - 开发者设置页"重置设备"调试入口。
     *
     * [修复防御]:普通用户**不应**触发,会丢失"撤回来自哪个设备"的可追溯性。
     */
    fun resetForTesting() {
        prefs.edit { remove(KEY_DEVICE_ID) }
        cachedId = null
        Log.w(TAG, "DeviceIdProvider: resetForTesting — 后续 deviceId() 将生成新 UUID")
    }

    private fun takePrefix(uuid: String): String =
        if (uuid.length >= 8) uuid.substring(0, 8) else uuid

    private var cachedId: String? = null

    companion object {
        private const val TAG = "DeviceIdProvider"
        private const val PREFS_NAME = "badger_device"
        private const val KEY_DEVICE_ID = "device_id"
    }
}