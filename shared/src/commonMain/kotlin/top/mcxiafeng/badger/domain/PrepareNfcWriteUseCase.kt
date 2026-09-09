package top.mcxiafeng.badger.domain

import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.data.prefs.isDeveloperMode
import top.mcxiafeng.badger.network.ShortLinkService

/**
 * 准备 NFC 写入 URL。
 *
 * 仅负责根据短链接配置决定最终写入地址；具体 NFC I/O 由 presentation 层负责。
 */
class PrepareNfcWriteUseCase(
    private val shortLinkService: ShortLinkService,
) {
    private companion object {
        const val TAG = "PrepareNfcWriteUseCase"
    }

    suspend operator fun invoke(
        targetUrl: String,
        onError: (String) -> Unit,
    ): String? {
        val devMode = isDeveloperMode()
        val savedUrl = shortLinkService.getShortUrl()

        if (savedUrl == null && devMode) {
            onError("请先在设置中选择一个短链接")
            return null
        }

        if (savedUrl == null) {
            BadgerLog.d(TAG, "未配置短链接，使用长链接写入 NFC: $targetUrl")
            return targetUrl
        }

        val updateResult = shortLinkService.updateLinkDestination(targetUrl)
        updateResult.onFailure {
            // [修复防御] 短链目的地更新失败时中止写入——继续写 savedUrl 会把
            // "仍指向上一个平台"的旧短链烧进标签，用户完全无感知，属静默错数据。
            BadgerLog.w(TAG, "更新短链接目标地址失败，中止 NFC 写入", it)
            onError("短链更新失败，请检查网络后重试")
            return null
        }
        return savedUrl
    }
}
