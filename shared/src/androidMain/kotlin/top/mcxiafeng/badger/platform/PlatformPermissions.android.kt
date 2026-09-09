package top.mcxiafeng.badger.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import top.mcxiafeng.badger.shared.db.SpikeContextHolder
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.coroutines.resume

private const val TAG = "PlatformPermissions"

/** Activity 宿主注册表（app 侧 MainActivity 在 onCreate/onDestroy 挂钩）。 */
object ActivityHost {
    @Volatile
    var activity: androidx.activity.ComponentActivity? = null
}

/** [KMP K13c] Android actual：ContextCompat 检查 + ActivityResultRegistry 注册式请求。 */
actual object PlatformPermissions {

    actual fun isCameraGranted(): Boolean {
        val context = SpikeContextHolder.appContext ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun requestCamera(): Boolean {
        if (isCameraGranted()) return true
        return requestRuntimePermission("requestCamera", "badger_camera_permission", Manifest.permission.CAMERA)
    }

    actual fun isLocationGranted(): Boolean {
        val context = SpikeContextHolder.appContext ?: return false
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    actual suspend fun requestLocation(): Boolean {
        if (isLocationGranted()) return true
        // FINE 覆盖 COARSE 的使用面；系统弹窗会级联申请粗定位
        return requestRuntimePermission("requestLocation", "badger_location_permission", Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** camera/location 共用的注册式权限请求（ActivityResultRegistry 模板）。 */
    private suspend fun requestRuntimePermission(what: String, registryKey: String, permission: String): Boolean {
        val activity = ActivityHost.activity
        if (activity == null) {
            BadgerLog.w(TAG, "$what: ActivityHost 未挂载，无法发起权限请求")
            return false
        }
        return suspendCancellableCoroutine { cont ->
            val launcher = activity.activityResultRegistry.register(
                registryKey,
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                BadgerLog.d(TAG, "$what result: granted=$granted")
                if (cont.isActive) cont.resume(granted)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(permission)
        }
    }

    actual fun openAppSettings() {
        val activity = ActivityHost.activity ?: run {
            BadgerLog.w(TAG, "openAppSettings: ActivityHost 未挂载")
            return
        }
        try {
            activity.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", activity.packageName, null),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            BadgerLog.w(TAG, "openAppSettings: 打开应用设置失败", e)
        }
    }
}
