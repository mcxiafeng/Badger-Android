package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "NfcWriter"

/**
 * [KMP K11] NFC 写卡 iOS actual：CoreNFC `NFCNDEFWriterSession` 骨架（编译占位）。
 *
 * K16（iosApp 工程，需 CoreNFC entitlement）实接时按以下语义实现：
 * - [startWriting]：构建 `NFCNDEFWriterSession(pollingOption, delegate, queue)` 并 `begin()`，
 *   系统弹原生写卡面板（交互形态与 Android ReaderMode 不同属预期）；
 *   delegate `writerSession(_:didDetectTags:)` 中 `queryNDEFStatus` → 可写则
 *   `NFCNDEFPayload.wellKnownTypeURIPayload(string:)` 构造 **HTTPS URI record**
 *   （与 Android 写入的数据格式一致——iOS 后台 NFC 不识别自定义 scheme）→ `writeNDEF`；
 *   `writerSession(_:didInvalidateWithError:)` 回填 [writeResult]（成功文案与 Android 对齐）。
 * - [stopWriting]：`invalidate()`；iOS 无 ReaderMode 排他权问题，无需延迟禁用。
 * - [isSupported]：`NFCNDEFReaderSession.readingAvailable`。
 * - Info.plist 需声明 `NFCReaderUsageDescription` + `com.apple.developer.nfc.readersession.formats`
 *   entitlement（NFC 写入标签格式 TAG）。
 *
 * 真机验证（写卡成功率 / iPhone 7+ A12 写 NDEF 限制 / 面板时序）登记 K17。
 */
actual class NfcWriter {

    private val _writeResult = MutableStateFlow<NfcWriteResult?>(null)
    actual val writeResult: StateFlow<NfcWriteResult?> = _writeResult.asStateFlow()

    actual val isWriting: Boolean get() = false

    actual fun isSupported(): Boolean {
        // 骨架：实接后改为 NFCNDEFReaderSession.readingAvailable
        return false
    }

    actual fun openNfcSettings(): Boolean {
        // iOS 无公开 NFC 设置页 deep link（只能引导用户去 设置 > 通用 > NFC，iOS 14+ 存在）
        BadgerLog.w(TAG, "iOS 骨架：无 NFC 设置页跳转，UI 层应提示手动开启", null)
        return false
    }

    actual fun startWriting(uri: String) {
        BadgerLog.w(TAG, "iOS 骨架：CoreNFC NFCNDEFWriterSession 实接登记 K16，目标 URI=$uri", null)
        _writeResult.value = NfcWriteResult(false, "iOS 端 NFC 写入尚未接入")
    }

    actual fun stopWriting() {
        // 骨架：无会话可终止
    }
}
