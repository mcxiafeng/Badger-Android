package top.mcxiafeng.badger.ui.components

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 骨架占位组件：数据到达前的呼吸块，形状由调用方 modifier 决定。
 *
 * 规范（docs/ui-spec.md 第五节）：加载态优先骨架屏，转圈留给无法预知布局的场景。
 * 动画值仅在绘制期读取（graphicsLayer），组合期不触发重组。
 */

/** 呼吸周期：缓慢往返，数据到达后骨架整体被替换，无需退出动画 */
private const val SKELETON_BREATH_DURATION_MS = 800

/** 呼吸透明度区间：下限保持可辨认，上限为全色 */
private const val SKELETON_MIN_BREATH_ALPHA = 0.45f
private const val SKELETON_MAX_BREATH_ALPHA = 1f

/** 默认骨架块色：主题前景的轻覆盖，浅深色主题下均与页面底色可区分 */
private const val SKELETON_BLOCK_ALPHA = 0.12f

private val SkeletonAvatarSize = 40.dp
private val SkeletonTitleWidth = 96.dp
private val SkeletonTitleHeight = 16.dp
private val SkeletonSummaryHeight = 12.dp

/** 默认骨架行数：约一屏可见量，数据到达前不出现明显空白 */
private const val DEFAULT_SKELETON_ROW_COUNT = 5

/**
 * 骨架显示延迟：本地 Room 查询（<100ms）与搜索防抖（~300ms）都应在窗口内完成，
 * 否则骨架只亮一瞬就被内容替换形成闪屏；超过该时长的慢加载（网络类）才亮骨架。
 */
const val SKELETON_SHOW_DELAY_MS = 400L

/**
 * 骨架可见性：挂载 [SKELETON_SHOW_DELAY_MS] 后才显示。
 * 数据在窗口内到达时骨架从不出现（空白直接切内容），只有慢加载才亮骨架。
 */
@Composable
fun rememberSkeletonDelayVisible(): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SKELETON_SHOW_DELAY_MS)
        visible = true
        Log.d("SkeletonBoxTester", "骨架延迟窗口结束，开始显示骨架")
    }
    return visible
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, color: Color? = null) {
    val blockColor = color ?: MiuixTheme.colorScheme.onSurface.copy(alpha = SKELETON_BLOCK_ALPHA)
    val transition = rememberInfiniteTransition(label = "SkeletonBox")
    val breathAlpha by transition.animateFloat(
        initialValue = SKELETON_MIN_BREATH_ALPHA,
        targetValue = SKELETON_MAX_BREATH_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(SKELETON_BREATH_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonBreathAlpha"
    )
    Box(modifier.graphicsLayer { alpha = breathAlpha }.background(blockColor))
}

/**
 * 通用两行列表骨架行（头像 + 标题 + 摘要），匹配 BasicComponent 式列表行的最终布局。
 *
 * @param avatar 是否渲染行首头像圆
 * @param summary 是否渲染第二行摘要线
 */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier, avatar: Boolean = true, summary: Boolean = true) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = BadgerSpacing.lg, vertical = BadgerSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatar) {
            SkeletonBox(Modifier.size(SkeletonAvatarSize).clip(CircleShape))
            Spacer(Modifier.width(BadgerSpacing.md))
        }
        Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.sm)) {
            SkeletonBox(
                Modifier
                    .width(SkeletonTitleWidth)
                    .height(SkeletonTitleHeight)
                    .clip(RoundedCornerShape(BadgerRadius.chip))
            )
            if (summary) {
                SkeletonBox(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(SkeletonSummaryHeight)
                        .clip(RoundedCornerShape(BadgerRadius.chip))
                )
            }
        }
    }
}

/** 整片列表骨架：rowCount 个 [SkeletonListRow]，用于首帧数据未到达时占住列表高度 */
@Composable
fun SkeletonListPlaceholder(
    modifier: Modifier = Modifier,
    rowCount: Int = DEFAULT_SKELETON_ROW_COUNT,
    avatar: Boolean = true,
    summary: Boolean = true
) {
    if (!rememberSkeletonDelayVisible()) return
    Column(modifier.fillMaxWidth()) {
        repeat(rowCount) { SkeletonListRow(avatar = avatar, summary = summary) }
    }
}
