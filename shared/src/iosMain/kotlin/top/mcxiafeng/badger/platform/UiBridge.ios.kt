package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSLog

/**
 * [KMP K13c] iOS actual 骨架：toast → NSLog（K16 以原生 overlay/SwiftUI 形态接线）。
 */
actual fun showToast(message: String) {
    NSLog("BadgerToast: %@", message)
}

/**
 * [KMP K13c] iOS actual 骨架：返回拦截 no-op。
 * iOS 侧返回语义（边缘滑动/交互式返回手势）在 K16 统一接线——Compose 内部的
 * 自定义返回拦截（多选模式/对话框）届时映射为状态回退而非系统返回事件。
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // K16: 接 org.jetbrains.compose.ui.backhandler 或自研手势层。当前 no-op 保持编译面。
}
