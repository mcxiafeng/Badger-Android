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

private const val WRITE_DEBOUNCE_MS = 3000L

private const val READER_MODE_DISABLE_DELAY_MS = 3000L

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

@SuppressLint("StaticFieldLeak")
actual class NfcWriter {

    

    private var _pendingUri: String? = null
    actual val isWriting: Boolean get() = _pendingUri != null

    private val _writeResult = MutableStateFlow<NfcWriteResult?>(null)
    actual val writeResult: StateFlow<NfcWriteResult?> = _writeResult.asStateFlow()

    
    private var lastWriteTime = 0L

    
    private var _currentActivityRef: WeakReference<Activity>? = null

    
    private val handler = Handler(Looper.getMainLooper())
    private var disableRunnable: Runnable? = null

    private fun activityOrNull(): Activity? = NfcActivityHost.activity ?: _currentActivityRef?.get()
    private fun contextOrNull(): Context? = NfcActivityHost.activity ?: SpikeContextHolder.appContext

    

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

    

    

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        Log.d(TAG, "ReaderMode 检测到标签: techList=${tag.techList.toList()}")
        val uri = _pendingUri
        if (uri == null) {
            Log.w(TAG, "ReaderMode: 无待写入 URI，忽略")
            return@ReaderCallback
        }

        
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
        
        disableRunnable?.let { handler.removeCallbacks(it) }
        disableRunnable = null

        _pendingUri = uri
        _writeResult.value = null
        _currentActivityRef = WeakReference(activity)
        
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
            
            _pendingUri = null
            _writeResult.value = null
            Log.w(TAG, "stopWriting: 宿主 Activity 不可用，仅清除写入状态")
            return
        }
        
        _pendingUri = null
        _writeResult.value = null

        
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

    

    private fun writeUriToTag(tag: Tag, uri: String): Boolean {
        val ndefRecord = NdefRecord.createUri(uri)
        val ndefMessage = NdefMessage(ndefRecord)
        val bytes = ndefMessage.toByteArray()

        
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
