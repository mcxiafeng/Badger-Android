package top.mcxiafeng.badger.pages.social

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

/**
 * NFC 标签写入工具类
 *
 * 使用 ReaderMode（而非 ForegroundDispatch）检测 NFC 标签，
 * 避免 Android 12+ 的 BAL (Background Activity Launch) 限制导致 Tag 数据丢失。
 *
 * 使用方式：
 * 1. 调用 [startWriting] 启动写入模式（自动启用 ReaderMode）
 * 2. 当设备检测到 NFC 标签时，通过 ReaderMode 回调直接获取 Tag，[writeResult] 会更新
 * 3. 调用 [stopWriting] 结束写入模式
 */
// Application context 生命周期等同于应用进程，不会造成 Activity 泄漏
@SuppressLint("StaticFieldLeak")
object NfcHelper {

    private const val TAG = "NfcHelper"

    data class WriteResult(val success: Boolean, val message: String)

    // --- NFC 写入状态 ---

    private var _pendingUri: String? = null
    val isWriting: Boolean get() = _pendingUri != null

    private val _writeResult = MutableStateFlow<WriteResult?>(null)
    val writeResult: StateFlow<WriteResult?> = _writeResult.asStateFlow()

    // 防抖：避免短时间内对同一标签重复写入
    private var lastWriteTime = 0L
    private const val WRITE_DEBOUNCE_MS = 3000L
    /** 停止写入后延迟禁用 ReaderMode 的时间（毫秒），防止系统弹出标签选择对话框 */
    private const val READER_MODE_DISABLE_DELAY_MS = 3000L

    // --- NFC 硬件检测 ---

    fun isNfcSupported(context: Context): Boolean {
        val manager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
        return manager?.defaultAdapter != null
    }

    fun openNfcSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // --- 写入控制 ---

    // 当前 Activity 引用（WeakReference 避免泄漏），用于延迟禁用 ReaderMode
    private var _currentActivityRef: WeakReference<Activity>? = null

    // 延迟禁用 ReaderMode 的 Handler，防止写入成功后立即 disable 导致系统弹出"选择操作"
    private val handler = Handler(Looper.getMainLooper())
    private var disableRunnable: Runnable? = null

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
            _writeResult.value = WriteResult(
                success = success,
                message = if (success) "NFC 标签写入成功" else "写入失败，标签可能不支持或已损坏"
            )
            Log.d(TAG, "ReaderMode: 写入结果=$success")

            // 写入成功后不立即禁用 ReaderMode！
            // ReaderMode 活跃时拥有标签排他权，系统不会触发标签分发（"选择操作"弹窗）。
            // 只有在用户主动关闭对话框时才 disableReaderMode，此时标签已远离手机。
        } catch (e: Exception) {
            Log.e(TAG, "写入 NFC 标签失败", e)
            _writeResult.value = WriteResult(false, "写入失败：${e.localizedMessage}")
        }
    }

    fun startWriting(activity: Activity, uri: String) {
        // 取消之前可能存在的延迟禁用
        disableRunnable?.let { handler.removeCallbacks(it) }
        disableRunnable = null

        _pendingUri = uri
        _writeResult.value = null
        _currentActivityRef = WeakReference(activity)
        enableReaderMode(activity)
        Log.d(TAG, "NFC 写入模式已启动，目标 URI: $uri")
    }

    fun stopWriting(activity: Activity) {
        // 立即清除写入状态，防止 readerCallback 再写入
        _pendingUri = null
        _writeResult.value = null

        // 延迟 3 秒后才真正 disableReaderMode
        // 原因：写入成功后如果标签仍在附近，立即 disableReaderMode 会让系统获得标签分发权，
        // 弹出"选择操作"对话框（Tags、钱包等）。保持 ReaderMode 活跃可以阻止系统分发，
        // 延迟 3 秒给用户足够时间将标签拿开。
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

    /**
     * 启用 ReaderMode：直接通过回调接收 Tag，不走 PendingIntent，不受 BAL 限制。
     */
    fun enableReaderMode(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: run {
            Log.w(TAG, "设备不支持 NFC")
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "NFC 未开启")
            return
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
        } catch (e: Exception) {
            Log.e(TAG, "启用 NFC ReaderMode 失败", e)
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
