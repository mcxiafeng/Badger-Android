package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "NfcWriter.ios"

/**
 * [KMP K11/K16] NFC 写卡 iOS actual 骨架。
 *
 * **限制**：Windows 开发机的 K/N 平台 klib 缺少 `NFCNDEFTag` / polling option 常量，
 * 完整 CoreNFC 实接需在 macOS + Xcode SDK 环境下进行（登记 K17 真机阶段）。
 *
 * K16（iosApp 工程，需 CoreNFC entitlement）实接时按以下语义实现：
 * - [startWriting]：构建 `NFCNDEFReaderSession(pollingOption, delegate, queue)` 并 `beginSession()`，
 *   系统弹原生写卡面板（交互形态与 Android ReaderMode 不同属预期）；
 *   delegate `readerSessionDidDetectTags` 中 `connectToTag` → `queryNDEFStatus` →
 *   可写则 `NFCNDEFPayload.wellKnownTypeURIPayloadWithString(uri)` 构造 **HTTPS URI record**
 *   （与 Android 写入的数据格式一致——iOS 后台 NFC 不识别自定义 scheme）→ `writeNDEF`；
 *   `readerSessionDidInvalidateWithError` 回填 [writeResult]（成功文案与 Android 对齐）。
 * - [stopWriting]：`invalidate()`；iOS 无 ReaderMode 排他权问题，无需延迟禁用。
 * - [isSupported]：`NFCNDEFReaderSession.readingAvailable()`。
 * - Info.plist 需声明 `NFCReaderUsageDescription` + `com.apple.developer.nfc.readersession.formats`
 *   entitlement（NFC 写入标签格式 TAG）。
 *
 * 真机验证（写卡成功率 / iPhone 7+ A12 写 NDEF 限制 / 面板时序）登记 K17。
 */
actual class NfcWriter {

    private var _pendingUri: String? = null
    actual val isWriting: Boolean get() = _pendingUri != null

    private val _writeResult = MutableStateFlow<NfcWriteResult?>(null)
    actual val writeResult: StateFlow<NfcWriteResult?> = _writeResult.asStateFlow()

    actual fun isSupported(): Boolean {
        // 骨架：K17 真机实接后改为 NFCNDEFReaderSession.readingAvailable()
        return false
    }

    actual fun openNfcSettings(): Boolean {
        // iOS 无公开 NFC 设置页 deep link（只能引导用户去 设置 > 通用 > NFC，iOS 14+ 存在）
        BadgerLog.w(TAG, "iOS 骨架：无 NFC 设置页跳转，UI 层应提示手动开启")
        return false
    }

    actual fun startWriting(uri: String) {
        // 骨架：K17 真机实接后构建 NFCNDEFReaderSession 并 beginSession()
        BadgerLog.w(TAG, "iOS 骨架：CoreNFC NFCNDEFReaderSession 实接登记 K17，目标 URI=$uri")
        _pendingUri = uri
        _writeResult.value = NfcWriteResult(false, "iOS 端 NFC 写入尚未接入")
    }

    actual fun stopWriting() {
        // 骨架：K17 真机实接后调用 session.invalidate()
        BadgerLog.d(TAG, "iOS 骨架：stopWriting 清除写入状态")
        _pendingUri = null
        _writeResult.value = null
    }
}
