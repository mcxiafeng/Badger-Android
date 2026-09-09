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
        val activity = ActivityHost.activity
        if (activity == null) {
            BadgerLog.w(TAG, "requestCamera: ActivityHost 未挂载，无法发起权限请求")
            return false
        }
        return suspendCancellableCoroutine { cont ->
            val launcher = activity.activityResultRegistry.register(
                "badger_camera_permission",
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                BadgerLog.d(TAG, "requestCamera result: granted=$granted")
                if (cont.isActive) cont.resume(granted)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(Manifest.permission.CAMERA)
        }
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
        val activity = ActivityHost.activity
        if (activity == null) {
            BadgerLog.w(TAG, "requestLocation: ActivityHost 未挂载，无法发起权限请求")
            return false
        }
        return suspendCancellableCoroutine { cont ->
            val launcher = activity.activityResultRegistry.register(
                "badger_location_permission",
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                BadgerLog.d(TAG, "requestLocation result: granted=$granted")
                if (cont.isActive) cont.resume(granted)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            // FINE 覆盖 COARSE 的使用面；系统弹窗会级联申请粗定位
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
