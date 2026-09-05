package top.mcxiafeng.badger.platform

import androidx.compose.runtime.Composable

/**
 * [KMP K13c] Toast 平台边界。
 *
 * Android actual = 主线程 Handler + Toast.makeText（LENGTH_SHORT 语义与原页面代码一致）；
 * iOS actual = NSLog 占位（原生 toast/overlay 形态 K16 接线）。
 */
expect fun showToast(message: String)

/**
 * [KMP K13c] 返回键拦截平台边界。
 *
 * Android actual = androidx.activity.compose.BackHandler（系统返回/手势返回）；
 * iOS actual = no-op 骨架（iOS 侧边缘滑动返回/navigationBar 返回接线 K16）。
 *
 * 17 个调用方从 androidx.activity.compose.BackHandler import 平移到此处，签名一致。
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
