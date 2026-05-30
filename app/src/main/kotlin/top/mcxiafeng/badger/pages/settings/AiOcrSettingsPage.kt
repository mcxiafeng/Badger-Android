package top.mcxiafeng.badger.pages.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ocr.AiOcrConfig
import top.mcxiafeng.badger.ocr.AiOcrService
import top.mcxiafeng.badger.ocr.ModelInfo
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.pages.setupguide.AI_PRESETS
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "AiOcrSettings"

@Composable
internal fun AiOcrSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var aiApiEndpoint by rememberSaveable { mutableStateOf(AiOcrConfig.getApiBaseUrl(context)) }
    var aiApiPath by rememberSaveable { mutableStateOf(AiOcrConfig.getApiPath(context)) }
    var aiApiKey by rememberSaveable { mutableStateOf(AiOcrConfig.getApiKey(context)) }
    var aiApiKeyVisible by remember { mutableStateOf(false) }
    var aiModel by rememberSaveable { mutableStateOf(AiOcrConfig.getModel(context)) }
    var aiSupportsVision by remember { mutableStateOf(AiOcrConfig.supportsVision(context)) }
    var aiAutoFallback by remember { mutableStateOf(AiOcrConfig.isAutoFallback(context)) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showProviderPopup by remember { mutableStateOf(false) }
    var showVisionHint by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 页面显示时从 SharedPreferences 刷新，避免云同步恢复后显示过时数据
    LaunchedEffect(Unit) {
        aiApiEndpoint = AiOcrConfig.getApiBaseUrl(context)
        aiApiPath = AiOcrConfig.getApiPath(context)
        aiApiKey = AiOcrConfig.getApiKey(context)
        aiModel = AiOcrConfig.getModel(context)
        aiSupportsVision = AiOcrConfig.supportsVision(context)
        aiAutoFallback = AiOcrConfig.isAutoFallback(context)
    }

    val customPresetIndex = AI_PRESETS.indexOfLast { it.name == "自定义" }
    var selectedPresetIndex by remember {
        mutableStateOf(
            AI_PRESETS.indexOfFirst { it.endpoint.isNotBlank() && it.endpoint == aiApiEndpoint }
                .takeIf { it >= 0 } ?: customPresetIndex
        )
    }
    val isCustomPreset = selectedPresetIndex == customPresetIndex

    BackHandler(enabled = showProviderPopup) { showProviderPopup = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "AI 配置",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + floatingBarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 基本配置（一个 Card）
            item(key = "main_card") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Column {
                        // 供应商
                        Box {
                            ArrowPreference(
                                title = "供应商",
                                summary = AI_PRESETS[selectedPresetIndex].name,
                                onClick = {
                                    showProviderPopup = true
                                    Log.d(TAG, "Provider popup opened")
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
                                                    aiSupportsVision = preset.supportsVision
                                                    AiOcrConfig.setSupportsVision(context, preset.supportsVision)
                                                } else {
                                                    aiApiEndpoint = ""
                                                    AiOcrConfig.setApiEndpoint(context, "")
                                                }
                                                Log.d(TAG, "Provider selected: ${preset.name}")
                                                showProviderPopup = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // API 地址（自定义时显示）
                        if (isCustomPreset) {
                            Spacer(Modifier.height(12.dp))
                            Text(text = "API 地址", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(Modifier.height(4.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                TextField(
                                    value = aiApiEndpoint,
                                    onValueChange = {
                                        aiApiEndpoint = it
                                        AiOcrConfig.setApiEndpoint(context, it)
                                        Log.d(TAG, "Endpoint updated: $it")
                                    },
                                    label = "https://api.openai.com/v1",
                                    useLabelAsPlaceholder = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                TextField(
                                    value = aiApiPath,
                                    onValueChange = {
                                        aiApiPath = it
                                        AiOcrConfig.setApiPath(context, it)
                                        Log.d(TAG, "API path updated: $it")
                                    },
                                    label = "/chat/completions",
                                    useLabelAsPlaceholder = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "完整地址：${aiApiEndpoint.trimEnd('/')}${if (aiApiPath.startsWith("/")) aiApiPath else "/$aiApiPath"}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // API Key
                        Spacer(Modifier.height(12.dp))
                        Text(text = "API Key", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(4.dp))
                        TextField(
                            value = aiApiKey,
                            onValueChange = {
                                aiApiKey = it
                                AiOcrConfig.setApiKey(context, it)
                                Log.d(TAG, "API key updated")
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

                        // 模型
                        Spacer(Modifier.height(12.dp))
                        Text(text = "模型", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(4.dp))
                        TextField(
                            value = aiModel,
                            onValueChange = {
                                aiModel = it
                                AiOcrConfig.setModel(context, it)
                                Log.d(TAG, "Model updated: $it")
                            },
                            label = "如 deepseek-chat",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = "从 API 获取模型列表",
                            onClick = {
                                showModelDialog = true
                                Log.d(TAG, "Model picker opened")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        Spacer(Modifier.height(8.dp))
                        ArrowPreference(
                            title = "测试 API 连接",
                            summary = if (isTesting) "连接中..." else testResult ?: "验证 API 配置是否正确",
                            onClick = {
                                scope.launch {
                                    isTesting = true
                                    testResult = null
                                    Log.d(TAG, "Test API clicked")
                                    val result = AiOcrService.testApi(context)
                                    isTesting = false
                                    testResult = result.getOrNull() ?: "连接失败: ${result.exceptionOrNull()?.message}"
                                    Log.d(TAG, "Test API result: $testResult")
                                }
                            }
                        )
                    }
                }
            }

            // Vision 提示
            if (showVisionHint) {
                item(key = "vision_hint") {
                    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                        Column {
                            Text(
                                text = "该模型支持图片输入，建议开启以获得更准确的识别效果。",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    text = "暂不",
                                    onClick = { showVisionHint = false }
                                )
                                Spacer(Modifier.size(8.dp))
                                TextButton(
                                    text = "开启",
                                    onClick = {
                                        aiSupportsVision = true
                                        AiOcrConfig.setSupportsVision(context, true)
                                        showVisionHint = false
                                        Log.d(TAG, "Vision enabled via hint")
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                            }
                        }
                    }
                }
            }

            // 高级选项
            item(key = "advanced_toggle") {
                SmallTitle(text = "高级选项", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
            }
            item(key = "advanced_card") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    SwitchPreference(
                        title = "显示高级选项",
                        summary = "视觉识别、自动降级等设置",
                        checked = showAdvanced,
                        onCheckedChange = { showAdvanced = it }
                    )
                }
            }

            if (showAdvanced) {
                item(key = "advanced_switches") {
                    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        Column {
                            SwitchPreference(
                                title = "支持图片输入",
                                summary = "该模型支持直接上传图片识别",
                                checked = aiSupportsVision,
                                onCheckedChange = {
                                    aiSupportsVision = it
                                    AiOcrConfig.setSupportsVision(context, it)
                                    Log.d(TAG, "Supports vision changed: $it")
                                }
                            )
                            SwitchPreference(
                                title = "识别失败自动换下一个",
                                summary = "自动尝试下一个可用模型",
                                checked = aiAutoFallback,
                                onCheckedChange = {
                                    aiAutoFallback = it
                                    AiOcrConfig.setAutoFallback(context, it)
                                    Log.d(TAG, "Auto fallback changed: $it")
                                }
                            )
                        }
                    }
                }
            }

            // 帮助说明
            item(key = "ai_ocr_help") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Text(
                        text = if (aiSupportsVision) "当前模式：图片识别。拍照后将图片直接发送给 AI 识别，更准确。" else "当前模式：文字识别。拍照后先用 OCR 提取文字，再发送给 AI 解析。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 1.5.em
                    )
                }
            }
        }
    }

    if (showModelDialog) {
        ModelPickerDialog(
            context = context,
            currentModel = aiModel,
            currentVisionEnabled = aiSupportsVision,
            onSelect = { model, supportsVision ->
                aiModel = model
                AiOcrConfig.setModel(context, model)
                Log.d(TAG, "Model selected: $model, supportsVision: $supportsVision")
                if (supportsVision && !aiSupportsVision) {
                    showVisionHint = true
                }
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false }
        )
    }
}

@Composable
private fun ModelPickerDialog(
    context: android.content.Context,
    currentModel: String,
    currentVisionEnabled: Boolean,
    onSelect: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        loadError = null
        Log.d(TAG, "Auto-fetching model list...")
        val (fetchedModels, error) = AiOcrService.fetchModels(context)
        models = fetchedModels
        isLoading = false
        if (models.isEmpty()) {
            loadError = error ?: "未获取到模型"
            Toast.makeText(context, error ?: "未获取到模型", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Fetched 0 models, error=$error")
        } else {
            Toast.makeText(context, "获取到 ${models.size} 个模型", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Fetched ${models.size} models")
        }
    }

    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "选择模型"
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isLoading) {
                Text("获取中...", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }

            loadError?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.error)
            }

            if (models.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(models, key = { it.id }) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(miuixShape(8.dp))
                                .clickable { onSelect(model.id, model.supportsVision) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.id,
                                    style = MiuixTheme.textStyles.body2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (model.supportsVision) {
                                    Text(
                                        text = "支持图片输入",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (model.id == currentModel) {
                                Text("当前", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (!isLoading && models.isEmpty() && loadError == null) {
                Text("暂无模型", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
