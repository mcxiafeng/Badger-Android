package top.mcxiafeng.badger.pages.settings

import androidx.compose.runtime.Composable
import org.koin.core.context.GlobalContext

/**
 * 临时兼容入口：NfcSettingsPage 当前直接获取 ServerApi。
 * 后续将随 Settings UI DI 收口改为显式构造注入。
 */
@Composable
internal inline fun <reified T : Any> koinInject(): T =
    GlobalContext.get().get()
