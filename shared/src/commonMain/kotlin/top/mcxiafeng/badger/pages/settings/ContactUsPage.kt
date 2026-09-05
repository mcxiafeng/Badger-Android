package top.mcxiafeng.badger.pages.settings

import top.mcxiafeng.badger.platform.UrlOpener
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ContactUsPage"

private const val QQ_GROUP_URL = "https://qm.qq.com/q/Rl7VFgrtOE"
private const val TELEGRAM_GROUP_URL = "https://t.me/+TCvPsqPXQltjOWM1"
private const val MATRIX_ROOM_URL = "https://matrix.to/#/#Open-Badger-APP:matrix.org"

/**
 * 开发者联系方式页：QQ 群 / Telegram / Matrix 三个外部社群入口。
 *
 * 语义上和"联系平台"（UserProfile.platforms）是完全不同的事：
 *   - 这里是开发者留给用户的反馈 / 群组渠道
 *   - 联系平台是用户自己名片里已经填的社交平台
 */
@Composable
internal fun ContactUsPage(onBack: () -> Unit) {
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    LaunchedEffect(Unit) {
        BadgerLog.d(TAG, "ContactUsPage loaded")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "联系我们",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Lucide.ArrowLeft, contentDescription = "返回")
                    }
                }
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + floatingBarBottomPadding),
        ) {
            item(key = "contact_groups") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "QQ 群",
                        summary = "点击加入 QQ 群",
                        onClick = {
                            BadgerLog.d(TAG, "open QQ group: $QQ_GROUP_URL")
                            if (!UrlOpener.openUrl(QQ_GROUP_URL)) BadgerLog.w(TAG, "open QQ group failed")
                        }
                    )
                    ArrowPreference(
                        title = "Telegram 群",
                        summary = "点击加入 Telegram 群",
                        onClick = {
                            BadgerLog.d(TAG, "open Telegram group: $TELEGRAM_GROUP_URL")
                            if (!UrlOpener.openUrl(TELEGRAM_GROUP_URL)) BadgerLog.w(TAG, "open Telegram group failed")
                        }
                    )
                    ArrowPreference(
                        title = "Matrix.org（备用）",
                        summary = "点击加入 Matrix 房间",
                        onClick = {
                            BadgerLog.d(TAG, "open Matrix room: $MATRIX_ROOM_URL")
                            if (!UrlOpener.openUrl(MATRIX_ROOM_URL)) BadgerLog.w(TAG, "open Matrix room failed")
                        }
                    )
                }
            }
        }
    }
}
