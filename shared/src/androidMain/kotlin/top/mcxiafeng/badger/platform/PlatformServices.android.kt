package top.mcxiafeng.badger.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import top.mcxiafeng.badger.shared.db.SpikeContextHolder
import java.io.File

private const val TAG = "PlatformServices"

/** 取宿主 context（Activity 优先，兜底 Application context）。 */
private fun hostContext(): Context? =
    SpikeContextHolder.appContext

/** 拉起 activity 需要的 NEW_TASK 标记（从非 Activity context 启动必需，Activity context 下无害）。 */
private const val NEW_TASK = Intent.FLAG_ACTIVITY_NEW_TASK

actual object PlatformClipboard {
    actual fun copy(text: String): Boolean {
        val context = hostContext() ?: run {
            Log.e(TAG, "copy: appContext 未初始化")
            return false
        }
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("badger", text))
            true
        } catch (e: Exception) {
            Log.e(TAG, "copy: 写入剪贴板失败", e)
            false
        }
    }
}

actual object SystemShare {
    actual fun shareText(title: String, text: String): Boolean {
        val context = hostContext() ?: run {
            Log.e(TAG, "shareText: appContext 未初始化")
            return false
        }
        return try {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(intent, title).addFlags(NEW_TASK))
            true
        } catch (e: Exception) {
            Log.e(TAG, "shareText: 分享失败", e)
            false
        }
    }

    actual fun shareFile(filePath: String, mimeType: String, title: String): Boolean {
        val context = hostContext() ?: run {
            Log.e(TAG, "shareFile: appContext 未初始化")
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(filePath)
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title).addFlags(NEW_TASK))
            true
        } catch (e: Exception) {
            Log.e(TAG, "shareFile: 分享失败 path=$filePath", e)
            false
        }
    }
}

actual object UrlOpener {
    actual fun openUrl(url: String): Boolean {
        val context = hostContext() ?: run {
            Log.e(TAG, "openUrl: appContext 未初始化")
            return false
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(NEW_TASK)
            )
            true
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "openUrl: 无可处理该链接的应用 url=$url")
            false
        } catch (e: Exception) {
            Log.e(TAG, "openUrl: 打开失败 url=$url", e)
            false
        }
    }
}
