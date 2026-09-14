package top.mcxiafeng.badger.domain

import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.data.prefs.isDeveloperMode
import top.mcxiafeng.badger.network.ShortLinkService

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
            
            
            BadgerLog.w(TAG, "更新短链接目标地址失败，中止 NFC 写入", it)
            onError("短链更新失败，请检查网络后重试")
            return null
        }
        return savedUrl
    }
}
