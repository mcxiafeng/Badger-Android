package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SetupStepAiKey(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var aiApiEndpoint by rememberSaveable { mutableStateOf(AiOcrConfig.getApiBaseUrl(context)) }
    var aiApiPath by rememberSaveable { mutableStateOf(AiOcrConfig.getApiPath(context)) }
    var aiApiKey by rememberSaveable { mutableStateOf(AiOcrConfig.getApiKey(context)) }
    var aiApiKeyVisible by remember { mutableStateOf(false) }
    var aiModel by remember { mutableStateOf(AiOcrConfig.getModel(context)) }
    var showProviderPopup by remember { mutableStateOf(false) }

    var selectedPresetIndex by remember {
        mutableStateOf(
            AI_PRESETS.indexOfFirst { it.endpoint.isNotBlank() && it.endpoint == aiApiEndpoint }
                .takeIf { it >= 0 } ?: AI_PRESETS.indexOfLast { it.name == "自定义" }
        )
    }

    BackHandler(enabled = showProviderPopup) { showProviderPopup = false }

    SetupStepScaffold(
        onBack = onBack,
        onSkip = {
            Log.d(TAG, "AI key step skipped")
            onSkip()
        },
        onNext = {
            Log.d(TAG, "AI key step completed")
            onNext()
        },
        nextEnabled = if (selectedPresetIndex == AI_PRESETS.indexOfLast { it.name == "自定义" }) {
            aiApiKey.isNotBlank() && aiApiEndpoint.isNotBlank()
        } else {
            aiApiKey.isNotBlank()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "设置 AI 识别",
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "拍照识别名片信息需要 AI 服务",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            Box {
                ArrowPreference(
                    title = "AI 供应商",
                    summary = AI_PRESETS[selectedPresetIndex].name,
                    onClick = {
                        showProviderPopup = true
                        Log.d(TAG, "AI provider popup opened")
                    }
                )
                OverlayListPopup(
                    show = showProviderPopup,
                    alignment = PopupPositionProvider.Align.End,
                    onDismissRequest = { showProviderPopup = false },
                    onDismissFinished = { showProviderPopup = false }
                ) {
                    ListPopupColumn {
                        AI_PRESETS.forEachIndexed { index, preset ->
                            DropdownImpl(
                                text = preset.name,
                                optionSize = AI_PRESETS.size,
                                isSelected = index == selectedPresetIndex,
                                index = index,
                                onSelectedIndexChange = {
                                    selectedPresetIndex = index
                                    if (preset.endpoint.isNotBlank()) {
                                        aiApiEndpoint = preset.endpoint
                                        AiOcrConfig.setApiEndpoint(context, preset.endpoint)
                                        aiModel = preset.defaultModel
                                        AiOcrConfig.setModel(context, preset.defaultModel)
                                        AiOcrConfig.setSupportsVision(context, preset.supportsVision)
                                    } else {
                                        aiApiEndpoint = ""
                                        AiOcrConfig.setApiEndpoint(context, "")
                                    }
                                    Log.d(TAG, "AI preset selected: ${preset.name}")
                                    showProviderPopup = false
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (selectedPresetIndex == AI_PRESETS.indexOfLast { it.name == "自定义" }) {
                    Text(text = "API 地址", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = aiApiEndpoint,
                            onValueChange = {
                                aiApiEndpoint = it
                                AiOcrConfig.setApiEndpoint(context, it)
                                Log.d(TAG, "Custom API endpoint updated: $it")
                            },
                            label = "https://api.openai.com/v1",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = aiApiPath,
                            onValueChange = {
                                aiApiPath = it
                                AiOcrConfig.setApiPath(context, it)
                                Log.d(TAG, "Custom API path updated: $it")
                            },
                            label = "/chat/completions",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(text = "API Key", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = aiApiKey,
                    onValueChange = {
                        aiApiKey = it
                        AiOcrConfig.setApiKey(context, it)
                        Log.d(TAG, "AI API key updated")
                    },
                    label = "粘贴你的 API Key",
                    useLabelAsPlaceholder = true,
                    visualTransformation = if (aiApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { aiApiKeyVisible = !aiApiKeyVisible }) {
                            Icon(
                                imageVector = if (aiApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (aiApiKeyVisible) "隐藏" else "显示",
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "模型名称", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = aiModel,
                    onValueChange = {
                        aiModel = it
                        AiOcrConfig.setModel(context, it)
                        Log.d(TAG, "AI model updated: $it")
                    },
                    label = "如 deepseek-chat",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    }
}