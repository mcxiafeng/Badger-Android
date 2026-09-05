package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.StateFlow

/** NFC 写卡结果（原 NfcHelper.WriteResult 语义：success + 用户可读 message）。 */
data class NfcWriteResult(val success: Boolean, val message: String)

/**
 * [KMP K11] NFC 写卡边界（expect）：把 HTTPS URI record 写入空白 NFC 标签。
 *
 * - Android actual：ReaderMode 检测标签 + Ndef/NdefFormatable 写入（原
 *   `pages/social/NfcHelper.kt` 逻辑不动迁入 shared androidMain）。写入成功后
 *   **不立即** disable ReaderMode（持标签排他权防系统"选择操作"弹窗），仅在
 *   [stopWriting] 时延迟 3s 禁用——该契约原样保留。
 * - iOS actual：CoreNFC `NFCNDEFWriterSession` 骨架（系统弹原生写卡面板，交互形态
 *   与 Android 不同属预期）；实接 + 真机验证登记 K16/K17。
 *
 * Activity 依赖：Android 侧经 `NfcActivityHost`（androidMain）由 UI 层显式挂载，
 * 本接口签名保持平台无关；数据格式约束不变——必须写 HTTPS URI（iOS 后台 NFC 不识别
 * 自定义 scheme，该约束同样适用于鸿蒙/iOS 侧写入的数据格式）。
 */
expect class NfcWriter {
    /** 是否处于待写入状态（startWriting 成功后到 stopWriting 前）。 */
    val isWriting: Boolean

    /** 最近一次写入结果；startWriting 时重置为 null。 */
    val writeResult: StateFlow<NfcWriteResult?>

    /** 设备是否具备 NFC 硬件。 */
    fun isSupported(): Boolean

    /** 打开系统 NFC 设置页；返回是否成功拉起。 */
    fun openNfcSettings(): Boolean

    /** 开始写入：进入待写状态并在检测到标签时写入 [uri]（HTTPS）。 */
    fun startWriting(uri: String)

    /** 停止写入并清除状态（Android 延迟 3s disable ReaderMode）。 */
    fun stopWriting()
}
