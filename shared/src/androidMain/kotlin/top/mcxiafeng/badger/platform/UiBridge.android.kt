package top.mcxiafeng.badger.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler as ActivityBackHandler
import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.shared.db.SpikeContextHolder

private const val TAG_TOAST = "PlatformToast"

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/** [KMP K13c] Android actual：主线程 Toast（LENGTH_SHORT 与原页面代码一致）。 */
actual fun showToast(message: String) {
    val context: Context = SpikeContextHolder.appContext ?: run {
        android.util.Log.w(TAG_TOAST, "showToast: appContext 未初始化，丢弃 toast: $message")
        return
    }
    mainHandler.post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/** [KMP K13c] Android actual：委托 androidx.activity.compose.BackHandler。 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    ActivityBackHandler(enabled = enabled, onBack = onBack)
}
