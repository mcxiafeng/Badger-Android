package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import top.mcxiafeng.badger.pages.settings.account.DEFAULT_SERVER_URL
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SERVER_TAG = "SetupStepServerUrl"
private const val PAGE_INDEX = 0

/**
 * 引导 Step 0 — 服务器地址。
 *
 * 流程：
 *   1. 输入 URL → 实时校验语法
 *   2. 点「测试连接」→ HEAD 请求验证可达性（5s 超时）
 *   3. 测试通过 → 热更 ServerApi + 启用「继续」
 *   4. 「继续」才推进到下一步
 *
 * 不可跳过（[nextEnabled] 仅在 [SetupGuideViewModel.TestState.Success] 时为 true）。
 */
@Composable
internal fun SetupStepServerUrl(
    onNext: () -> Unit,
    viewModel: SetupGuideViewModel = koinViewModel(),
) {
    val initialUrl by viewModel.currentServerUrl.collectAsState()
    val testState by viewModel.testState.collectAsState()

    // [修复防御]: 一次计算有效 URL,后续比较/初始化都用它 —— 避免 `.ifBlank{DEFAULT}.trim().trimEnd('/')` 散落各处。
    val effectiveUrl = initialUrl.ifBlank { DEFAULT_SERVER_URL }
    var urlInput by remember(effectiveUrl) {
        mutableStateOf(
            TextFieldValue(
                text = effectiveUrl,
                selection = TextRange(0, effectiveUrl.length),
            )
        )
    }

    val syntaxValid = remember(urlInput.text) { validateServerUrl(urlInput.text) == null }
    val testSuccess = testState is SetupGuideViewModel.TestState.Success
    // [修复防御]: 不要把 isDirty 加进 canAdvance —— LaunchedEffect(urlInput.text) 已经保证
    // 任何 URL 改动都会把 testState 重置为 Idle,所以 testState=Success 必然是针对当前 urlInput 的
    // 新鲜测试结果。加上 !isDirty 会把"测试通过的新 URL"卡在 disabled "URL 已修改"。
    val canAdvance = testSuccess && syntaxValid

    // [修复防御]: URL 改了 → 重置测试状态。用户改完必须重测,不能拿旧测试结果蒙混过关。
    LaunchedEffect(urlInput.text) {
        if (testState !is SetupGuideViewModel.TestState.Idle) {
            viewModel.resetTestState()
        }
    }

    // [修复防御]: 上报当前页可推进性 → SetupGuideScreen 决定 Pager 是否锁定。
    LaunchedEffect(canAdvance) {
        viewModel.setPageValid(PAGE_INDEX, canAdvance)
    }

    // [修复防御]: 进入下一步前必须再次校验（键盘 enter 等绕过 UI 控件的事件）。
    SetupStepScaffold(
        onBack = null,
        // 底部 CTA 是这个 step 唯一的主按钮 —— 状态机驱动 text / enabled / onNext。
        onNext = {
            when (val s = testState) {
                is SetupGuideViewModel.TestState.Success -> {
                    val err = validateServerUrl(urlInput.text)
                    if (err != null) {
                        Log.w(SERVER_TAG, "next blocked: $err")
                        return@SetupStepScaffold
                    }
                    val cleaned = cleanServerUrl(urlInput.text)
                    viewModel.updateServerUrl(cleaned, DEFAULT_SERVER_URL)
                    viewModel.setPageValid(PAGE_INDEX, true)
                    Log.d(SERVER_TAG, "next → apply serverUrl=$cleaned")
                    onNext()
                }
                else -> {
                    // Idle / Failed / Testing → 触发测试（Testing 时 runSync 会拦截）
                    if (s !is SetupGuideViewModel.TestState.Testing && syntaxValid) {
                        viewModel.testServerConnection(cleanServerUrl(urlInput.text))
                    }
                }
            }
        },
        nextEnabled = when (testState) {
            is SetupGuideViewModel.TestState.Testing -> false
            // [修复防御]: 测试已通过即可推进 —— isDirty 由 LaunchedEffect 守卫,
            // 这里再卡 isDirty 会把"测试通过的新 URL"卡死。
            is SetupGuideViewModel.TestState.Success -> syntaxValid
            else -> syntaxValid
        },
        nextText = when (testState) {
            is SetupGuideViewModel.TestState.Testing -> "测试中…"
            is SetupGuideViewModel.TestState.Success -> "继续"
            is SetupGuideViewModel.TestState.Failed -> "重新测试"
            SetupGuideViewModel.TestState.Idle -> "测试连接"
        },
        backText = "上一步",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BadgerSpacing.xxl, vertical = BadgerSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepHeader(
                title = "连接到服务器",
                subtitle = "设置你的 Badger Server 地址",
                icon = Icons.Outlined.Cloud,
            )

            Spacer(modifier = Modifier.height(BadgerSpacing.xl))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(BadgerSpacing.lg)) {
                    Text(
                        text = "服务器地址",
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = "https://badger.example.com",
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        // 「清空全部」：仅在有内容时显示 —— 输入为空时按钮噪音对用户无意义。
                        // [修复防御]: 仅修改 UI 本地 state,不调 resetServerUrlToDefault/dirty 任何副作用;
                        // 用户主动清空后由「测试连接」按钮兜底,未通过测试不会持久化到 holder/factory。
                        trailingIcon = {
                            if (urlInput.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    Log.d(SERVER_TAG, "clear URL input (was len=${urlInput.text.length})")
                                    urlInput = TextFieldValue(
                                        text = "",
                                        selection = TextRange(0),
                                    )
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "清空",
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 错误信息：仅在 URL 语法不合法时展示。
                    val syntaxErr = validateServerUrl(urlInput.text)
                    if (syntaxErr != null) {
                        Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                        Text(
                            text = syntaxErr,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }

                    // 测试结果展示 —— 仅在有结果（success / failed）时显示，不抢 CTA 焦点。
                    val success = testState as? SetupGuideViewModel.TestState.Success
                    val failed = testState as? SetupGuideViewModel.TestState.Failed
                    if (success != null || failed != null) {
                        Spacer(modifier = Modifier.height(BadgerSpacing.md))
                        TestResultLine(testState = testState)
                    }
                }
            }
        }
    }
}

/**
 * 测试结果展示行 — 仅展示当前测试结果,不持有按钮,避免与底部 CTA 抢焦点。
 */
@Composable
private fun TestResultLine(testState: SetupGuideViewModel.TestState) {
    val success = testState as? SetupGuideViewModel.TestState.Success
    val failed = testState as? SetupGuideViewModel.TestState.Failed
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
    ) {
        if (success != null) {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "HTTP ${success.httpCode} · 连接成功",
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.primary,
            )
        } else if (failed != null) {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = failed.message,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 引导各 Step 共用的视觉头部：大图标 + title + subtitle。统一放在卡片之外作为视觉锚点。
 */
@Composable
internal fun StepHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.lg))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(BadgerSpacing.sm))
        Text(
            text = subtitle,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 校验 Server URL：返回 `null` 表示通过；否则返回错误文案。
 *
 * 与 [top.mcxiafeng.badger.pages.settings.EditServerUrlDialog] 同构：
 * - 拒绝看起来像凭证的输入（@、token=、Bearer）
 * - 必须以 http:// 或 https:// 开头
 * - host 非空
 */
internal fun validateServerUrl(input: String): String? {
    val raw = input.trim()
    if (raw.isBlank()) return "请填写服务器地址"
    val looksLikeCredential =
        raw.contains("://") && (raw.contains("@") || raw.contains("token=") || raw.contains("Bearer "))
    if (looksLikeCredential) return "URL 中不能包含账号或 token"
    val schemeOk = raw.startsWith("http://") || raw.startsWith("https://")
    if (!schemeOk) return "需以 http:// 或 https:// 开头"
    val cleaned = cleanServerUrl(raw)
    val hostPart = cleaned.substringAfter("://", missingDelimiterValue = "")
    if (hostPart.isBlank()) return "主机名不能为空"
    if (!hostPart.contains('.')) return "请填写有效的域名或 IP"
    return null
}

/** 清洗 URL：trim、剥尾部斜杠、剥路径后缀。 */
internal fun cleanServerUrl(input: String): String {
    val trimmed = input.trim()
    val schemeEnd = trimmed.indexOf("://")
    val searchStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
    val firstSlash = trimmed.indexOf('/', startIndex = searchStart)
    return if (firstSlash > 0) trimmed.substring(0, firstSlash).trimEnd('/')
    else trimmed.trimEnd('/')
}
