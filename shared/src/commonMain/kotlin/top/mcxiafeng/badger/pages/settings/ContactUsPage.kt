package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.platform.UrlOpener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import top.mcxiafeng.badger.pages.settings.components.SettingsGroupCard
import top.mcxiafeng.badger.pages.settings.components.SettingsListScaffold
import top.mcxiafeng.badger.ui.navigation.SettingsPage
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.preference.ArrowPreference

private const val TAG = "ContactUsPage"

private const val QQ_GROUP_URL = "https://qm.qq.com/q/Rl7VFgrtOE"
private const val TELEGRAM_GROUP_URL = "https://t.me/+TCvPsqPXQltjOWM1"
private const val MATRIX_ROOM_URL = "https://matrix.to/#/#Open-Badger-APP:matrix.org"

/**
 * 联系我们页（重写：共享脚手架 + SettingsGroupCard）。
 *
 * 三个外部社群入口（QQ 群 / Telegram / Matrix），点击调 [UrlOpener] 打开。
 * 与「联系平台」（UserProfile.platforms）语义不同：这里是开发者留给用户的反馈渠道。
 */
@Composable
internal fun ContactUsPage(onBack: () -> Unit) {
    LaunchedEffect(Unit) { BadgerLog.d(TAG, "ContactUsPage loaded") }

    SettingsListScaffold(title = SettingsPage.ContactUs.title, onBack = onBack) {
        item(key = "contact_groups") {
            SettingsGroupCard(
                rows = listOf(
                    {
                        ArrowPreference(
                            title = "QQ 群",
                            summary = "点击加入 QQ 群",
                            onClick = {
                                BadgerLog.d(TAG, "open QQ group: $QQ_GROUP_URL")
                                if (!UrlOpener.openUrl(QQ_GROUP_URL)) BadgerLog.w(TAG, "open QQ group failed")
                            },
                        )
                    },
                    {
                        ArrowPreference(
                            title = "Telegram 群",
                            summary = "点击加入 Telegram 群",
                            onClick = {
                                BadgerLog.d(TAG, "open Telegram group: $TELEGRAM_GROUP_URL")
                                if (!UrlOpener.openUrl(TELEGRAM_GROUP_URL)) BadgerLog.w(TAG, "open Telegram group failed")
                            },
                        )
                    },
                    {
                        ArrowPreference(
                            title = "Matrix.org（备用）",
                            summary = "点击加入 Matrix 房间",
                            onClick = {
                                BadgerLog.d(TAG, "open Matrix room: $MATRIX_ROOM_URL")
                                if (!UrlOpener.openUrl(MATRIX_ROOM_URL)) BadgerLog.w(TAG, "open Matrix room failed")
                            },
                        )
                    },
                ),
            )
        }
    }
}
