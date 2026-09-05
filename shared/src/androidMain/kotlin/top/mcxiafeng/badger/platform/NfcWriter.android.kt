package top.mcxiafeng.badger.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import top.mcxiafeng.badger.shared.db.SpikeContextHolder

private const val TAG = "NfcHelper"

/** NFC 写入防抖间隔（毫秒），避免短时间内对同一标签重复写入 */
private const val WRITE_DEBOUNCE_MS = 3000L
/** 停止写入后延迟禁用 ReaderMode 的时间（毫秒），防止系统弹出标签选择对话框 */
private const val READER_MODE_DISABLE_DELAY_MS = 3000L

/**
 * [KMP K11] Android UI 层挂载当前 Activity 的宿主注册表。
 *
 * NfcWriter 的公共签名平台无关（无 Activity 参数）；ReaderMode enable/disable
 * 需要 Activity，由「我的名片」页在写入对话框生命周期内显式 attach/detach。
 * WeakReference 保活策略在 actual 内部维持。
 */
object NfcActivityHost {
    @Volatile
    internal var activity: Activity? = null

    fun attach(activity: Activity?) {
        this.activity = activity
    }

    fun detach() {
        this.activity = null
    }
}

/**
 * [KMP K11] NFC 写卡 Android actual：ReaderMode + Ndef/NdefFormatable。
 *
 * 原 `pages/social/NfcHelper.kt` 逻辑不动迁入（object → Koin 单例 actual class）。
 * Application context 生命周期等同于应用进程，不会造成 Activity 泄漏。
 */
@SuppressLint("StaticFieldLeak")
actual class NfcWriter {

    // --- NFC 写入状态 ---

    private var _pendingUri: String? = null
    actual val isWriting: Boolean get() = _pendingUri != null

    private val _writeResult = MutableStateFlow<NfcWriteResult?>(null)
    actual val writeResult: StateFlow<NfcWriteResult?> = _writeResult.asStateFlow()

    // 防抖：避免短时间内对同一标签重复写入
    private var lastWriteTime = 0L

    // 当前 Activity 引用（WeakReference 避免泄漏），用于延迟禁用 ReaderMode
    private var _currentActivityRef: WeakReference<Activity>? = null

    // 延迟禁用 ReaderMode 的 Handler，防止写入成功后立即 disable 导致系统弹出"选择操作"
    private val handler = Handler(Looper.getMainLooper())
    private var disableRunnable: Runnable? = null

    private fun activityOrNull(): Activity? = NfcActivityHost.activity ?: _currentActivityRef?.get()
    private fun contextOrNull(): Context? = NfcActivityHost.activity ?: SpikeContextHolder.appContext

    // --- NFC 硬件检测 ---

    actual fun isSupported(): Boolean {
        val context = contextOrNull() ?: return false
        val manager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
        return manager?.defaultAdapter != null
    }

    actual fun openNfcSettings(): Boolean {
        val context = contextOrNull() ?: return false
        return try {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开 NFC 系统设置失败", e)
            false
        }
    }

    // --- 写入控制 ---

    /**
     * ReaderMode 回调：检测到标签时直接写入
     */
    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        Log.d(TAG, "ReaderMode 检测到标签: techList=${tag.techList.toList()}")
        val uri = _pendingUri
        if (uri == null) {
            Log.w(TAG, "ReaderMode: 无待写入 URI，忽略")
            return@ReaderCallback
        }

        // 防抖：3秒内不重复写入
        val now = System.currentTimeMillis()
        if (now - lastWriteTime < WRITE_DEBOUNCE_MS) {
            Log.d(TAG, "写入防抖，忽略 (间隔 ${now - lastWriteTime}ms)")
            return@ReaderCallback
        }
        lastWriteTime = now

        try {
            val success = writeUriToTag(tag, uri)
            _writeResult.value = NfcWriteResult(
                success = success,
                message = if (success) "NFC 标签写入成功" else "写入失败，标签可能不支持或已损坏"
            )
            Log.d(TAG, "ReaderMode: 写入结果=$success")

            // 写入成功后不立即禁用 ReaderMode！
            // ReaderMode 活跃时拥有标签排他权，系统不会触发标签分发（"选择操作"弹窗）。
            // 只有在用户主动关闭对话框时才 disableReaderMode，此时标签已远离手机。
        } catch (e: Exception) {
            Log.e(TAG, "写入 NFC 标签失败", e)
            _writeResult.value = NfcWriteResult(false, "写入失败：${e.localizedMessage}")
        }
    }

    actual fun startWriting(uri: String) {
        val activity = NfcActivityHost.activity ?: run {
            Log.w(TAG, "startWriting: 宿主 Activity 未挂载，忽略")
            return
        }
        // 取消之前可能存在的延迟禁用
        disableRunnable?.let { handler.removeCallbacks(it) }
        disableRunnable = null

        _pendingUri = uri
        _writeResult.value = null
        _currentActivityRef = WeakReference(activity)
        // enableReaderMode 失败时（NFC 未开启/不支持）清 pending 并通知 UI
        val enabled = enableReaderMode(activity)
        if (!enabled) {
            _pendingUri = null
            _writeResult.value = NfcWriteResult(false, "NFC 未开启，请在系统设置中开启 NFC")
            Log.w(TAG, "startWriting: NFC 不可用，已清除写入状态")
            return
        }
        Log.d(TAG, "NFC 写入模式已启动，目标 URI: $uri")
    }

    actual fun stopWriting() {
        val activity = activityOrNull() ?: run {
            // 无 Activity 可用（页面已销毁）：仍清除状态，ReaderMode 由 unbind 生命周期兜底
            _pendingUri = null
            _writeResult.value = null
            Log.w(TAG, "stopWriting: 宿主 Activity 不可用，仅清除写入状态")
            return
        }
        // 立即清除写入状态，防止 readerCallback 再写入
        _pendingUri = null
        _writeResult.value = null

        // 延迟 3 秒 disableReaderMode，防止系统弹出"选择操作"对话框
        disableRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            try {
                val adapter = NfcAdapter.getDefaultAdapter(activity)
                if (adapter != null) {
                    adapter.disableReaderMode(activity)
                    Log.d(TAG, "延迟禁用 ReaderMode 完成")
                }
            } catch (e: Exception) {
                Log.e(TAG, "延迟禁用 ReaderMode 失败", e)
            }
            _currentActivityRef = null
        }
        disableRunnable = runnable
        handler.postDelayed(runnable, READER_MODE_DISABLE_DELAY_MS)
        Log.d(TAG, "NFC 写入状态已清除，ReaderMode 将在 3 秒后禁用")
    }

    // --- ReaderMode 调度 ---

    /** 启用 ReaderMode，返回是否成功。 */
    private fun enableReaderMode(activity: Activity): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: run {
            Log.w(TAG, "设备不支持 NFC")
            return false
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "NFC 未开启")
            return false
        }

        try {
            // FLAG_READER_NFC_A | FLAG_READER_NFC_B | FLAG_READER_NFC_F | FLAG_READER_NFC_V
            // 覆盖所有常见 NFC 标签类型
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            adapter.enableReaderMode(activity, readerCallback, flags, null)
            Log.d(TAG, "NFC ReaderMode 已启用")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "启用 NFC ReaderMode 失败", e)
            return false
        }
    }

    // --- 底层写入 ---

    private fun writeUriToTag(tag: Tag, uri: String): Boolean {
        val ndefRecord = NdefRecord.createUri(uri)
        val ndefMessage = NdefMessage(ndefRecord)
        val bytes = ndefMessage.toByteArray()

        // 优先尝试已格式化的 Ndef 标签
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            return try {
                if (!ndef.isWritable) {
                    Log.w(TAG, "Ndef 标签不可写")
                    false
                } else if (ndef.maxSize < bytes.size) {
                    Log.w(TAG, "Ndef 标签容量不足: max=${ndef.maxSize}, need=$bytes.size")
                    false
                } else {
                    ndef.writeNdefMessage(ndefMessage)
                    Log.d(TAG, "Ndef 标签写入成功")
                    true
                }
            } finally {
                try { ndef.close() } catch (e: Exception) { Log.e(TAG, "close NDEF tag failed", e) }
            }
        }

        // 尝试格式化空白标签
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            formatable.connect()
            return try {
                formatable.format(ndefMessage)
                Log.d(TAG, "NdefFormatable 标签格式化并写入成功")
                true
            } finally {
                try { formatable.close() } catch (e: Exception) { Log.e(TAG, "close NdefFormatable tag failed", e) }
            }
        }

        Log.w(TAG, "标签不支持 Ndef 或 NdefFormatable: ${tag.techList.toList()}")
        return false
    }
}
