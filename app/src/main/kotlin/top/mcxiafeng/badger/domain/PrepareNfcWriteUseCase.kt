package top.mcxiafeng.badger.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.pages.setupguide.isDeveloperMode
import javax.inject.Inject

/**
 * 准备 NFC 写入 UseCase
 *
 * 检查配置，决定使用短链接还是长链接，准备写入 URL。
 */
class PrepareNfcWriteUseCase @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) {
    private val TAG = "PrepareNfcWriteUseCase"

    /**
     * 准备 NFC 写入的 URL
     *
     * @return 准备好的 URL，null 表示错误（错误信息通过 onError 回调）
     */
    suspend operator fun invoke(
        context: Context,
        targetUrl: String,
        onError: (String) -> Unit
    ): String? {
        val devMode = isDeveloperMode(context)
        val savedUrl = ShortLinkService.getShortUrl(context)

        if (savedUrl == null && devMode) {
            onError("请先在设置中选择一个短链接")
            return null
        }

        return if (savedUrl != null) {
            // 有短链接，更新目标地址后使用短链接
            val updateResult = ShortLinkService.updateLinkDestination(context, targetUrl)
            updateResult.onFailure {
                Log.w(TAG, "更新短链接目标地址失败，仍使用已有链接写入", it)
            }
            updateResult.getOrDefault(savedUrl)
        } else {
            // 非开发者模式且未配置短链接，直接使用长链接
            Log.d(TAG, "非开发者模式，使用长链接写入 NFC: $targetUrl")
            targetUrl
        }
    }
}
