package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.network.ShortLinkService

internal fun saveAdvanced(
    ctx: android.content.Context, enabled: Boolean, apiUrl: String,
    updatePath: String, method: String, authHeader: String, authPrefix: String,
    updateBody: String
) {
    ShortLinkService.saveAdvancedSettings(ctx, enabled, apiUrl, updatePath, method, authHeader, authPrefix, updateBody)
}
