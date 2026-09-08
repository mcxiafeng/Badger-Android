package top.mcxiafeng.badger.pages.settings.components

import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.ui.navigation.SettingsPage.About
import top.mcxiafeng.badger.ui.navigation.SettingsPage.Dashboard
import top.mcxiafeng.badger.ui.navigation.SettingsPage.NfcSettings
import top.mcxiafeng.badger.ui.navigation.SettingsPage.OperationHistory
import top.mcxiafeng.badger.ui.navigation.SettingsPage.ServerShortLinks
import top.mcxiafeng.badger.ui.navigation.SettingsPage.SyncStatus
import top.mcxiafeng.badger.ui.navigation.SettingsPage.TagManager
import top.mcxiafeng.badger.ui.navigation.SettingsPage.UiSettings
import top.mcxiafeng.badger.ui.navigation.SettingsPage.UserSettings

/**
 * 设置一级页分组声明（IA 单一来源）。
 *
 * L1 主页按此表逐组渲染：分组标题 + 该组各 [SettingsPage] 行（title/icon/summary/onClick）。
 * 新增 / 调整设置页入口只动此表与 [SettingsPage] 枚举，不散落改主页 Composable。
 *
 * 约定：
 * - 「账号 hero 卡」单独位于分组之上（不走此表）。
 * - 「消息中心」由 TopBar 铃铛直入（[SettingsPage.Notifications]），不在分组内。
 */
internal data class SettingsHomeGroup(
    val title: String,
    val pages: List<SettingsPage>,
)

internal val settingsHomeGroups: List<SettingsHomeGroup> = listOf(
    SettingsHomeGroup("数据与同步", listOf(Dashboard, SyncStatus, OperationHistory)),
    SettingsHomeGroup("内容与链接", listOf(TagManager, ServerShortLinks)),
    SettingsHomeGroup("偏好", listOf(UserSettings, UiSettings, NfcSettings)),
    SettingsHomeGroup("关于", listOf(About)),
)

/**
 * 主页行的副标题文案（单一来源）。
 *
 * 调用点禁止再硬编码行 summary；新增页在此补一行。
 */
internal val SettingsPage.homeSummary: String
    get() = when (this) {
        Dashboard -> "联系人 / 标签 / 名片夹统计"
        SyncStatus -> "同步健康与未同步项"
        OperationHistory -> "查看历史操作记录"
        TagManager -> "管理全局标签库 / 色点显示"
        ServerShortLinks -> "管理服务端自建短链"
        UserSettings -> "云端偏好（语言 / 主题 / 通知邮件）"
        UiSettings -> "悬浮导航栏 / 模糊 / 液态玻璃"
        NfcSettings -> "短链服务 / 自定义接口 / API Key"
        About -> "版本 / 开源许可 / 联系我们"
        // L3 与 TopBar 直入页在主页不展示行，无需 summary
        else -> ""
    }
