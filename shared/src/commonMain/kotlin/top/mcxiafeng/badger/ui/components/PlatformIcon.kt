package top.mcxiafeng.badger.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * [KMP K13c] 平台图标组件：1:1 圆角方块底色 + 白色矢量图标。
 *
 * expect/actual（原 app 实现依赖 app 模块 R.drawable；drawable 资源随迁 shared androidMain，
 * iOS actual 骨架 K16 换 bundle 图片）。包名保持 `ui.components`——几十处调用点 import 零改动。
 *
 * @param fieldKey 字段 key（用于匹配图标资源）
 * @param color 平台主题色
 * @param sizeDp 图标尺寸（默认 28dp）
 */
@Composable
expect fun PlatformIcon(fieldKey: String, color: Color, sizeDp: Float = 28f)
