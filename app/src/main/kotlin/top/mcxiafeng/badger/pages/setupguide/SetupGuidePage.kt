package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.setOnboardingCompleted
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SetupGuideRoute(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 5 }
    val setupGuideViewModel: SetupGuideViewModel = hiltViewModel()
    val isSyncing by setupGuideViewModel.isSyncing.collectAsState()

    SetupGuideScreen(
        pagerState = pagerState,
        scope = scope,
        isPlatformsSyncLocked = isSyncing,
        onComplete = {
            setOnboardingCompleted(context)
            setSetupGuideCompleted(context)
            Log.d(TAG, "Setup guide completed")
            onComplete()
        }
    )
}

@Composable
internal fun SetupGuideScreen(
    pagerState: PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    isPlatformsSyncLocked: Boolean,
    onComplete: () -> Unit
) {
    Scaffold { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        StepProgressIndicator(
            currentStep = pagerState.currentPage,
            totalSteps = 5,
            modifier = Modifier
                .padding(top = 24.dp, bottom = 8.dp)
                .align(Alignment.CenterHorizontally)
        )

        // [修复防御]: 平台信息同步期间禁用 HorizontalPager 滑动，
        // 防止用户用滑动手势绕过"下一步"按钮进入 SetupStepProfile。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = !(pagerState.currentPage == 1 && isPlatformsSyncLocked)
        ) { page ->
            when (page) {
                0 -> SetupStepWelcome(
                    onNext = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
                1 -> SetupStepPlatforms(
                    onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onSkip = { scope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> SetupStepProfile(
                    onBack = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(3) } },
                    onSkip = { scope.launch { pagerState.animateScrollToPage(3) } },
                    pageTrigger = pagerState.currentPage
                )
                3 -> SetupStepNavBarEffect(
                    onBack = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(4) } },
                    onSkip = { scope.launch { pagerState.animateScrollToPage(4) } }
                )
                4 -> SetupStepFinish(
                    onBack = { scope.launch { pagerState.animateScrollToPage(3) } },
                    onComplete = onComplete
                )
            }
        }
    }
    }
}

// --- Step 0: 欢迎 ---

@Composable
internal fun SetupStepWelcome(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.QrCodeScanner,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "欢迎来到 Badger",
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "帮你创建个人社交名片\n通过二维码或 NFC 快速分享联系方式",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            lineHeight = 1.5.em
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text(text = "开始")
        }
    }
}

// --- Step 6: 欢迎完成 ---

@Composable
internal fun SetupStepFinish(
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.QrCodeScanner,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "设置完成！",
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "你已准备好开始使用 Badger\n之后可以在设置页随时修改这些配置",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            lineHeight = 1.5.em
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Filled.QrCodeScanner, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = "扫描二维码", style = MiuixTheme.textStyles.subtitle)
                    Text(text = "扫一扫对方的二维码，自动添加联系人", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Filled.Nfc, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = "NFC 碰一碰分享", style = MiuixTheme.textStyles.subtitle)
                    Text(text = "写入 NFC 标签，手机一碰即可交换信息", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SetupStepNavButtons(
            onBack = onBack,
            onNext = onComplete,
            nextText = "开始使用"
        )
    }
}