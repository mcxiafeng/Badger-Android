package top.mcxiafeng.badger.pages.settings

import android.util.Log
import android.widget.Toast
import top.mcxiafeng.badger.pages.settings.saveAdvanced
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.BasicComponent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.network.ShortIoDomain
import top.mcxiafeng.badger.network.ShortIoLink
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.utils.miuixShape
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "NfcSettings"

@Composable
internal fun NfcSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var apiKey by rememberSaveable { mutableStateOf(ShortLinkService.getApiKey()) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var shortLinkEnabled by remember { mutableStateOf(ShortLinkService.isEnabled()) }
    var domain by remember { mutableStateOf(ShortLinkService.getDomain()) }
    var selectedLinkId by remember { mutableStateOf(ShortLinkService.getLinkId()) }
    var shortUrl by remember { mutableStateOf(ShortLinkService.getShortUrl()) }
    var domains by remember { mutableStateOf<List<ShortIoDomain>>(emptyList()) }
    var domainsLoading by remember { mutableStateOf(false) }
    var domainError by remember { mutableStateOf<String?>(null) }
    var links by remember { mutableStateOf<List<ShortIoLink>>(emptyList()) }
    var linksLoading by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var customEnabled by remember { mutableStateOf(ShortLinkService.isCustomEnabled()) }
    var apiUrl by remember { mutableStateOf(ShortLinkService.getApiUrl()) }
    var updatePath by remember { mutableStateOf(ShortLinkService.getUpdatePath()) }
    var apiMethod by remember { mutableStateOf(ShortLinkService.getApiMethod()) }
    var authHeader by remember { mutableStateOf(ShortLinkService.getAuthHeader()) }
    var authPrefix by remember { mutableStateOf(ShortLinkService.getAuthPrefix()) }
    var updateBody by remember { mutableStateOf(ShortLinkService.getUpdateBody()) }

    // 页面显示时从 SharedPreferences 刷新，避免云同步恢复后显示过时数据
    LaunchedEffect(Unit) {
        shortLinkEnabled = ShortLinkService.isEnabled()
        apiKey = ShortLinkService.getApiKey()
        domain = ShortLinkService.getDomain()
        selectedLinkId = ShortLinkService.getLinkId()
        shortUrl = ShortLinkService.getShortUrl()
        customEnabled = ShortLinkService.isCustomEnabled()
        apiUrl = ShortLinkService.getApiUrl()
        updatePath = ShortLinkService.getUpdatePath()
        apiMethod = ShortLinkService.getApiMethod()
        authHeader = ShortLinkService.getAuthHeader()
        authPrefix = ShortLinkService.getAuthPrefix()
        updateBody = ShortLinkService.getUpdateBody()
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            domainsLoading = true
            domainError = null
            val result = ShortLinkService.fetchDomains()
            result.onSuccess { domains = it }.onFailure { domainError = it.message }
            domainsLoading = false
        } else {
            domains = emptyList()
        }
    }

    suspend fun loadLinks() {
        val domainId = ShortLinkService.getDomainId()
        if (domainId > 0) {
            linksLoading = true
            linkError = null
            val result = ShortLinkService.fetchLinks(domainId)
            result.onSuccess { links = it }.onFailure { linkError = it.message }
            linksLoading = false
        }
    }

    LaunchedEffect(domain) {
        if (domain.isNotBlank() && ShortLinkService.getDomainId() > 0) loadLinks()
        else links = emptyList()
    }

    BackHandler(enabled = showDomainDialog || showLinkDialog || showCreateDialog) {
        showDomainDialog = false
        showLinkDialog = false
        showCreateDialog = false
    }

    Scaffold(
        topBar = { TopAppBar(title = "NFC设置", scrollBehavior = topAppBarScrollBehavior, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = BadgerSpacing.md, end = BadgerSpacing.md, top = BadgerSpacing.sm, bottom = BadgerSpacing.sm + floatingBarBottomPadding),
            verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md),
        ) {
            item(key = "nfc_help") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Text(
                        text = "NFC 碰一碰即可将你的名片分享给对方。开启短链接后，NFC 标签上只存储短网址，可随时更新指向的目标。",
                        style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, lineHeight = 1.5.em
                    )
                }
            }
            item(key = "shortlink_toggle") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    SwitchPreference(
                        title = "开启短链接",
                        summary = "启用后 NFC 写入将使用短链接，否则使用原始长链接",
                        checked = shortLinkEnabled,
                        onCheckedChange = {
                            shortLinkEnabled = it
                            ShortLinkService.setEnabled(it)
                            Log.d(TAG, "短链接功能切换: $it")
                        }
                    )
                }
            }
            if (shortLinkEnabled) {
            item(key = "shortlink_settings") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "API Key", style = MiuixTheme.textStyles.body1)
                        Spacer(Modifier.height(4.dp))
                        // 失焦时写盘，不再每个字符都 saveApiKey
                        TextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = "输入 short.io API Key",
                            useLabelAsPlaceholder = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (apiKeyVisible) "隐藏" else "显示"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        ShortLinkService.saveApiKey(apiKey)
                                    }
                                }
                        )
                    }
                    ArrowPreference(
                        title = "域名",
                        summary = when {
                            domain.isNotBlank() -> domain
                            domainsLoading -> "加载中..."
                            domainError != null -> "获取失败"
                            apiKey.isBlank() -> "请先填写 API Key"
                            else -> "点击选择"
                        },
                        onClick = { if (domains.isNotEmpty()) { Log.d(TAG, "Domain dialog opened"); showDomainDialog = true } }
                    )
                    ArrowPreference(
                        title = "短链接",
                        summary = when {
                            shortUrl != null -> shortUrl!!
                            linksLoading -> "加载中..."
                            linkError != null -> "获取失败"
                            domain.isBlank() -> "请先选择域名"
                            else -> "点击选择"
                        },
                        onClick = { if (domain.isNotBlank()) { Log.d(TAG, "Link dialog opened"); showLinkDialog = true } }
                    )
                    if (selectedLinkId.isNotBlank()) {
                        var currentLinkDetails by remember { mutableStateOf<ShortIoLink?>(null) }
                        var detailsLoading by remember { mutableStateOf(false) }
                        var detailsError by remember { mutableStateOf<String?>(null) }
                        var defaultPlatform by remember { mutableStateOf<String?>(null) }
                        val userProfileViewModel: NfcSettingsViewModel = koinViewModel()
                        LaunchedEffect(Unit) {
                            userProfileViewModel.getUserProfile().collect { profile ->
                                defaultPlatform = profile?.defaultPlatform
                            }
                        }
                        LaunchedEffect(selectedLinkId, apiKey, domain) {
                            if (ShortLinkService.isConfigured()) {
                                detailsLoading = true
                                detailsError = null
                                val result = ShortLinkService.fetchLinkDetails()
                                detailsLoading = false
                                result.onSuccess { currentLinkDetails = it }.onFailure { detailsError = it.message }
                            }
                        }
                        // 只读状态行用 BasicComponent（ArrowPreference 箭头暗示可点击）
                        BasicComponent(
                            title = "当前指向",
                            summary = when {
                                detailsLoading -> "更新中..."
                                detailsError != null -> "获取失败: $detailsError"
                                currentLinkDetails != null && currentLinkDetails!!.originalURL.isNotBlank() -> {
                                    "${defaultPlatform ?: ""} ${currentLinkDetails!!.originalURL}".trim()
                                }
                                else -> "未设置目标地址"
                            },
                        )
                    }
                }
            }
            } // shortLinkEnabled

            // 高级设置
            item(key = "advanced_settings") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    SwitchPreference(
                        title = "使用其他短链接服务（高级）",
                        summary = "使用自定义短链接平台替代 short.io",
                        checked = customEnabled,
                        onCheckedChange = {
                            customEnabled = it
                            saveAdvanced(context, customEnabled, apiUrl, updatePath, apiMethod, authHeader, authPrefix, updateBody)
                        }
                    )
                    if (customEnabled) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "API 地址", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                            TextField(value = apiUrl, onValueChange = { apiUrl = it; saveAdvanced(context, customEnabled, it, updatePath, apiMethod, authHeader, authPrefix, updateBody) }, label = "https://api.example.com", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "更新端点", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                            TextField(value = updatePath, onValueChange = { updatePath = it; saveAdvanced(context, customEnabled, apiUrl, it, apiMethod, authHeader, authPrefix, updateBody) }, label = "/links/{linkId}", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "HTTP 方法", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                            TextField(value = apiMethod, onValueChange = { apiMethod = it; saveAdvanced(context, customEnabled, apiUrl, updatePath, it, authHeader, authPrefix, updateBody) }, label = "POST", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "认证头名称", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                            TextField(value = authHeader, onValueChange = { authHeader = it; saveAdvanced(context, customEnabled, apiUrl, updatePath, apiMethod, it, authPrefix, updateBody) }, label = "Authorization", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "认证前缀", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                            TextField(value = authPrefix, onValueChange = { authPrefix = it; saveAdvanced(context, customEnabled, apiUrl, updatePath, apiMethod, authHeader, it, updateBody) }, label = "Bearer ", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "更新请求体", style = MiuixTheme.textStyles.body1); Spacer(Modifier.height(4.dp))
                            TextField(value = updateBody, onValueChange = { updateBody = it; saveAdvanced(context, customEnabled, apiUrl, updatePath, apiMethod, authHeader, authPrefix, it) }, label = """{"originalURL":"{url}"}""", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (customEnabled) {
                item(key = "advanced_help") {
                    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                        Text(text = "占位符说明\n{url} → 目标链接\n{linkId} → 链接 ID", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, lineHeight = 1.5.em)
                    }
                }
            }
        }
    }

    // 域名选择弹窗
    if (showDomainDialog) {
        WindowDialog(
            show = true,
            title = "选择域名",
            onDismissRequest = { showDomainDialog = false }
        ) {
            when {
                domainsLoading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(36.dp)) }
                domainError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = domainError ?: "未知错误", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(text = "重试", onClick = { scope.launch { domainsLoading = true; domainError = null; ShortLinkService.fetchDomains().onSuccess { domains = it }.onFailure { domainError = it.message }; domainsLoading = false } }, colors = ButtonDefaults.textButtonColorsPrimary())
                }
                domains.isEmpty() -> Text("没有可用域名\n请在 short.io 后台添加", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
                else -> LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(domains, key = { it.hostname }) { d ->
                        val isSelected = d.hostname == domain
                        Box(modifier = Modifier.fillMaxWidth().clickable { Log.d(TAG, "Domain selected: ${d.hostname}"); ShortLinkService.saveDomainSelection(d); domain = d.hostname; selectedLinkId = ""; shortUrl = null; showDomainDialog = false }.background(if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent, miuixShape(8.dp)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Text(text = d.hostname, style = if (isSelected) MiuixTheme.textStyles.subtitle else MiuixTheme.textStyles.body2, color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    // 链接选择弹窗
    if (showLinkDialog) {
        WindowDialog(
            show = true,
            title = "选择短链接",
            onDismissRequest = { showLinkDialog = false }
        ) {
            when {
                linksLoading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(36.dp)) }
                linkError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = linkError ?: "未知错误", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(text = "重试", onClick = { scope.launch { loadLinks() } }, colors = ButtonDefaults.textButtonColorsPrimary())
                }
                links.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("该域名下还没有短链接", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(text = "创建一个", onClick = { Log.d(TAG, "Create link dialog opened from empty list"); showLinkDialog = false; showCreateDialog = true }, colors = ButtonDefaults.textButtonColorsPrimary())
                }
                else -> LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(links, key = { it.idString }) { link ->
                        val isSelected = link.idString == selectedLinkId
                        Column(modifier = Modifier.fillMaxWidth().clickable { Log.d(TAG, "Link selected: ${link.shortURL}"); ShortLinkService.saveLinkSelection(link); selectedLinkId = link.idString; shortUrl = link.shortURL.ifBlank { "https://$domain/${link.path}" }; showLinkDialog = false }.background(if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f), miuixShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(text = link.shortURL.ifBlank { "$domain/${link.path}" }, style = if (isSelected) MiuixTheme.textStyles.subtitle else MiuixTheme.textStyles.body2, color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (link.originalURL.isNotBlank()) { Text(text = link.originalURL, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }

    // 创建短链接弹窗
    if (showCreateDialog) {
        var createUrl by remember { mutableStateOf("") }
        var creating by remember { mutableStateOf(false) }
        var createError by remember { mutableStateOf<String?>(null) }
        WindowDialog(
            show = true,
            title = "创建短链接",
            onDismissRequest = { showCreateDialog = false }
        ) {
            Text("目标链接（对方碰 NFC 后打开的地址）", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(8.dp))
            TextField(value = createUrl, onValueChange = { createUrl = it; createError = null }, label = "https://example.com", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            if (createError != null) { Spacer(Modifier.height(4.dp)); Text(text = createError!!, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(text = "取消", onClick = { Log.d(TAG, "Create link dialog cancelled"); showCreateDialog = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(text = if (creating) "创建中..." else "创建", onClick = {
                    if (createUrl.isBlank()) { createError = "请输入目标链接"; return@TextButton }
                    Log.d(TAG, "Create short link: $createUrl")
                    creating = true; createError = null
                    scope.launch {
                        val result = ShortLinkService.createShortIoLink(createUrl)
                        creating = false
                        result.onSuccess { link -> ShortLinkService.saveLinkSelection(link); selectedLinkId = link.idString; shortUrl = link.shortURL.ifBlank { "https://$domain/${link.path}" }; links = listOf(link); showCreateDialog = false }
                            .onFailure { createError = it.message ?: "创建失败" }
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), enabled = !creating)
            }
        }
    }
}
