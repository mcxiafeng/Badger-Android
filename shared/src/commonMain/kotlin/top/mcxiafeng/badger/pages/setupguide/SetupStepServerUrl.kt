package top.mcxiafeng.badger.pages.setupguide

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
import org.koin.compose.viewmodel.koinViewModel
import top.mcxiafeng.badger.data.prefs.DEFAULT_SERVER_URL
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.X
import top.mcxiafeng.badger.utils.BadgerLog

private const val SERVER_TAG = "SetupStepServerUrl"
private const val PAGE_INDEX = 0

@Composable
internal fun SetupStepServerUrl(
    onNext: () -> Unit,
    viewModel: SetupGuideViewModel = koinViewModel(),
) {
    val initialUrl by viewModel.currentServerUrl.collectAsState()
    val testState by viewModel.testState.collectAsState()

    
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
    
    
    
    val canAdvance = testSuccess && syntaxValid

    
    LaunchedEffect(urlInput.text) {
        if (testState !is SetupGuideViewModel.TestState.Idle) {
            viewModel.resetTestState()
        }
    }

    
    LaunchedEffect(canAdvance) {
        viewModel.setPageValid(PAGE_INDEX, canAdvance)
    }

    
    SetupStepScaffold(
        onBack = null,
        
        onNext = {
            when (val s = testState) {
                is SetupGuideViewModel.TestState.Success -> {
                    val err = validateServerUrl(urlInput.text)
                    if (err != null) {
                        BadgerLog.w(SERVER_TAG, "next blocked: $err")
                        return@SetupStepScaffold
                    }
                    val cleaned = cleanServerUrl(urlInput.text)
                    viewModel.updateServerUrl(cleaned, DEFAULT_SERVER_URL)
                    viewModel.setPageValid(PAGE_INDEX, true)
                    BadgerLog.d(SERVER_TAG, "next → apply serverUrl=$cleaned")
                    onNext()
                }
                else -> {
                    
                    if (s !is SetupGuideViewModel.TestState.Testing && syntaxValid) {
                        viewModel.testServerConnection(cleanServerUrl(urlInput.text))
                    }
                }
            }
        },
        nextEnabled = when (testState) {
            is SetupGuideViewModel.TestState.Testing -> false
            
            
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
                icon = Lucide.Cloud,
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
                        
                        
                        
                        trailingIcon = {
                            if (urlInput.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    BadgerLog.d(SERVER_TAG, "clear URL input (was len=${urlInput.text.length})")
                                    urlInput = TextFieldValue(
                                        text = "",
                                        selection = TextRange(0),
                                    )
                                }) {
                                    Icon(
                                        imageVector = Lucide.X,
                                        contentDescription = "清空",
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    
                    val syntaxErr = validateServerUrl(urlInput.text)
                    if (syntaxErr != null) {
                        Spacer(modifier = Modifier.height(BadgerSpacing.sm))
                        Text(
                            text = syntaxErr,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }

                    
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
                imageVector = Lucide.Cloud,
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
                imageVector = Lucide.Cloud,
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

internal fun cleanServerUrl(input: String): String {
    val trimmed = input.trim()
    val schemeEnd = trimmed.indexOf("://")
    val searchStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
    val firstSlash = trimmed.indexOf('/', startIndex = searchStart)
    return if (firstSlash > 0) trimmed.substring(0, firstSlash).trimEnd('/')
    else trimmed.trimEnd('/')
}
