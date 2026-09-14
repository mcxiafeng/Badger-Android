package top.mcxiafeng.badger.pages.setupguide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.prefs.setOnboardingCompleted
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.ScanLine
import com.composables.icons.lucide.SlidersHorizontal
import top.mcxiafeng.badger.utils.BadgerLog

private const val TOTAL_STEPS = 6

@Composable
fun SetupGuideRoute(onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { TOTAL_STEPS }
    val setupGuideViewModel: SetupGuideViewModel = koinViewModel()
    val pageValidity by setupGuideViewModel.pageValidity.collectAsState()

    SetupGuideScreen(
        pagerState = pagerState,
        scope = scope,
        currentPageValid = pageValidity[pagerState.currentPage] == true,
        onComplete = {
            setOnboardingCompleted()
            setSetupGuideCompleted()
            BadgerLog.d(TAG, "Setup guide completed")
            onComplete()
        },
    )
}

@Composable
internal fun SetupGuideScreen(
    pagerState: PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    currentPageValid: Boolean,
    onComplete: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            StepProgressIndicator(
                currentStep = pagerState.currentPage,
                totalSteps = TOTAL_STEPS,
                modifier = Modifier
                    .padding(top = BadgerSpacing.xl, bottom = BadgerSpacing.sm)
                    .align(Alignment.CenterHorizontally),
            )

            
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = currentPageValid,
            ) { page ->
                when (page) {
                    0 -> SetupStepServerUrl(
                        onNext = { scope.launch { pagerState.animateScrollToPage(1) } },
                    )
                    1 -> SetupStepAccount(
                        onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                        onNext = { scope.launch { pagerState.animateScrollToPage(2) } },
                    )
                    2 -> SetupStepProfile(
                        onBack = { scope.launch { pagerState.animateScrollToPage(1) } },
                        onNext = { scope.launch { pagerState.animateScrollToPage(3) } },
                        pageTrigger = pagerState.currentPage,
                    )
                    3 -> SetupStepPlatforms(
                        onBack = { scope.launch { pagerState.animateScrollToPage(2) } },
                        onNext = { scope.launch { pagerState.animateScrollToPage(4) } },
                    )
                    4 -> SetupStepNavBarEffect(
                        onBack = { scope.launch { pagerState.animateScrollToPage(3) } },
                        onNext = { scope.launch { pagerState.animateScrollToPage(5) } },
                    )
                    5 -> SetupStepFinish(
                        onBack = { scope.launch { pagerState.animateScrollToPage(4) } },
                        onComplete = onComplete,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SetupStepFinish(
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    SetupStepScaffold(
        onBack = onBack,
        onNext = onComplete,
        nextEnabled = true,
        nextText = "开始使用",
        backText = "上一步",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BadgerSpacing.xxl, vertical = BadgerSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepHeader(
                title = "设置完成",
                subtitle = "你已准备好开始使用 Badger\n之后可以在设置页随时修改这些配置",
                icon = Lucide.CircleCheck,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            SummaryCard(
                title = "分享你的名片",
                body = "通过二维码或 NFC 让对方扫码/碰一碰添加你",
                icon = Lucide.ScanLine,
            )
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            SummaryCard(
                title = "随时调整",
                body = "服务器、账号、个人资料、外观风格都能在「设置」中修改",
                icon = Lucide.SlidersHorizontal,
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    body: String,
    icon: ImageVector,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(BadgerSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.size(BadgerSpacing.md))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                Text(
                    text = body,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
