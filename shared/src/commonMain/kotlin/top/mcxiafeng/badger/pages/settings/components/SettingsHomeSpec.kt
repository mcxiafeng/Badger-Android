package top.mcxiafeng.badger.pages.settings.components

import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.ui.navigation.SettingsPage.About
import top.mcxiafeng.badger.ui.navigation.SettingsPage.Dashboard
import top.mcxiafeng.badger.ui.navigation.SettingsPage.OperationHistory
import top.mcxiafeng.badger.ui.navigation.SettingsPage.SyncStatus
import top.mcxiafeng.badger.ui.navigation.SettingsPage.TagManager
import top.mcxiafeng.badger.ui.navigation.SettingsPage.UiSettings
import top.mcxiafeng.badger.ui.navigation.SettingsPage.UserSettings

internal data class SettingsHomeGroup(
    val title: String,
    val pages: List<SettingsPage>,
)

internal val settingsHomeGroups: List<SettingsHomeGroup> = listOf(
    SettingsHomeGroup("数据与同步", listOf(Dashboard, SyncStatus, OperationHistory)),
    SettingsHomeGroup("内容与链接", listOf(TagManager)),
    SettingsHomeGroup("偏好", listOf(UserSettings, UiSettings)),
    SettingsHomeGroup("关于", listOf(About)),
)

internal val SettingsPage.homeSummary: String
    get() = when (this) {
        Dashboard -> "联系人 / 标签 / 名片夹统计"
        SyncStatus -> "同步健康与未同步项"
        OperationHistory -> "查看历史操作记录"
        TagManager -> "管理全局标签库 / 色点显示"
        UserSettings -> "云端偏好（语言 / 主题 / 通知邮件 / 短链）"
        UiSettings -> "悬浮导航栏 / 模糊 / 液态玻璃"
        About -> "版本 / 开源许可 / 联系我们"
        
        else -> ""
    }
