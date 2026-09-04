package top.mcxiafeng.badger.domain

import android.content.Context
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
        context: Context,
        targetUrl: String,
        onError: (String) -> Unit,
    ): String? {
        val devMode = isDeveloperMode(context)
        val savedUrl = shortLinkService.getShortUrl(context)

        if (savedUrl == null && devMode) {
            onError("请先在设置中选择一个短链接")
            return null
        }

        if (savedUrl == null) {
            BadgerLog.d(TAG, "未配置短链接，使用长链接写入 NFC: $targetUrl")
            return targetUrl
        }

        val updateResult = shortLinkService.updateLinkDestination(context, targetUrl)
        updateResult.onFailure {
            BadgerLog.w(TAG, "更新短链接目标地址失败，仍使用已有链接写入", it)
        }
        return updateResult.getOrDefault(savedUrl)
    }
}
