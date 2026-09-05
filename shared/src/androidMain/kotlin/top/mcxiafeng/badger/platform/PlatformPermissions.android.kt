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
}
