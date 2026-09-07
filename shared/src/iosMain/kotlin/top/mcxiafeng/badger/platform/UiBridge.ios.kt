package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler as ComposeBackHandler
import platform.Foundation.NSLog

/**
 * [KMP K13c→K16] iOS actual：toast → NSLog（无原生 overlay，compose 层需自研 toast 容器）。
 * 当前为日志降级——iOS 无全局 Toast 等价物，需 SwiftUI overlay 或 compose 状态层实现。
 * 调用方应在 iOS 侧以 inline 反馈（Snackbar/对话框）替代 Toast。
 */
actual fun showToast(message: String) {
    NSLog("BadgerToast: %@", message)
}

/**
 * [KMP K13c→K16] iOS actual：委托 CMP 多平台 BackHandler。
 *
 * CMP 1.11 的 `androidx.compose.ui.backhandler.BackHandler` 在 iOS 上接交互式返回手势
 * （边缘滑动 pop gesture），与 Android 系统返回键语义对齐——多选模式退出、对话框拦截、
 * 搜索折叠等场景均可正常工作。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    ComposeBackHandler(enabled = enabled, onBack = onBack)
}
