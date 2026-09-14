package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "NfcWriter.ios"

actual class NfcWriter {

    private var _pendingUri: String? = null
    actual val isWriting: Boolean get() = _pendingUri != null

    private val _writeResult = MutableStateFlow<NfcWriteResult?>(null)
    actual val writeResult: StateFlow<NfcWriteResult?> = _writeResult.asStateFlow()

    actual fun isSupported(): Boolean {
        
        return false
    }

    actual fun openNfcSettings(): Boolean {
        
        BadgerLog.w(TAG, "iOS 骨架：无 NFC 设置页跳转，UI 层应提示手动开启")
        return false
    }

    actual fun startWriting(uri: String) {
        
        BadgerLog.w(TAG, "iOS 骨架：CoreNFC NFCNDEFReaderSession 实接登记 K17，目标 URI=$uri")
        _pendingUri = uri
        _writeResult.value = NfcWriteResult(false, "iOS 端 NFC 写入尚未接入")
    }

    actual fun stopWriting() {
        
        BadgerLog.d(TAG, "iOS 骨架：stopWriting 清除写入状态")
        _pendingUri = null
        _writeResult.value = null
    }
}
