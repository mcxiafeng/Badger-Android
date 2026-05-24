package top.mcxiafeng.badger.pages.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.rememberContactRepository
import top.mcxiafeng.badger.network.ShortIoDomain
import top.mcxiafeng.badger.network.ShortIoLink
import top.mcxiafeng.badger.network.ShortLinkService
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

private const val TAG = "ShortLinkSettings"

@Composable
internal fun ShortLinkSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var apiKey by remember { mutableStateOf(ShortLinkService.getApiKey(context)) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var domain by remember { mutableStateOf(ShortLinkService.getDomain(context)) }
    var selectedLinkId by remember { mutableStateOf(ShortLinkService.getLinkId(context)) }
    var shortUrl by remember { mutableStateOf(ShortLinkService.getShortUrl(context)) }
    var domains by remember { mutableStateOf<List<ShortIoDomain>>(emptyList()) }
    var domainsLoading by remember { mutableStateOf(false) }
    var domainError by remember { mutableStateOf<String?>(null) }
    var links by remember { mutableStateOf<List<ShortIoLink>>(emptyList()) }
    var linksLoading by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var customEnabled by remember { mutableStateOf(ShortLinkService.isCustomEnabled(context)) }
    var apiUrl by remember { mutableStateOf(ShortLinkService.getApiUrl(context)) }
    var updatePath by remember { mutableStateOf(ShortLinkService.getUpdatePath(context)) }
    var apiMethod by remember { mutableStateOf(ShortLinkService.getApiMethod(context)) }
    var authHeader by remember { mutableStateOf(ShortLinkService.getAuthHeader(context)) }
    var authPrefix by remember { mutableStateOf(ShortLinkService.getAuthPrefix(context)) }
    var updateBody by remember { mutableStateOf(ShortLinkService.getUpdateBody(context)) }

    // 页面显示时从 SharedPreferences 刷新，避免云同步恢复后显示过时数据
    LaunchedEffect(Unit) {
        apiKey = ShortLinkService.getApiKey(context)
        domain = ShortLinkService.getDomain(context)
        selectedLinkId = ShortLinkService.getLinkId(context)
        shortUrl = ShortLinkService.getShortUrl(context)
        customEnabled = ShortLinkService.isCustomEnabled(context)
        apiUrl = ShortLinkService.getApiUrl(context)
        updatePath = ShortLinkService.getUpdatePath(context)
        apiMethod = ShortLinkService.getApiMethod(context)
        authHeader = ShortLinkService.getAuthHeader(context)
        authPrefix = ShortLinkService.getAuthPrefix(context)
        updateBody = ShortLinkService.getUpdateBody(context)
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            domainsLoading = true
            domainError = null
            val result = ShortLinkService.fetchDomains(context)
            result.onSuccess { domains = it }.onFailure { domainError = it.message }
            domainsLoading = false
        } else {
            domains = emptyList()
        }
    }

    suspend fun loadLinks() {
        val domainId = ShortLinkService.getDomainId(context)
        if (domainId > 0) {
            linksLoading = true
            linkError = null
            val result = ShortLinkService.fetchLinks(context, domainId)
            result.onSuccess { links = it }.onFailure { linkError = it.message }
            linksLoading = false
        }
    }

    LaunchedEffect(domain) {
        if (domain.isNotBlank() && ShortLinkService.getDomainId(context) > 0) loadLinks()
        else links = emptyList()
    }

    Scaffold(
        topBar = { TopAppBar(title = "短链接", scrollBehavior = topAppBarScrollBehavior, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item(key = "shortlink_help") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Text(
                        text = "短链接可将你的名片变成一个短网址，对方碰 NFC 标签后可打开该网址查看你的信息。",
                        fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, lineHeight = 18.sp
                    )
                }
            }
            item(key = "shortlink_settings") {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "API Key", fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        TextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; ShortLinkService.saveApiKey(context, it) },
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
                        onClick = { if (domains.isNotEmpty()) showDomainDialog = true }
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
                        onClick = { if (domain.isNotBlank()) showLinkDialog = true }
                    )
                    if (selectedLinkId.isNotBlank()) {
                        var currentLinkDetails by remember { mutableStateOf<ShortIoLink?>(null) }
                        var detailsLoading by remember { mutableStateOf(false) }
                        var detailsError by remember { mutableStateOf<String?>(null) }
                        var defaultPlatform by remember { mutableStateOf<String?>(null) }
                        val settingsRepository = rememberContactRepository()
                        LaunchedEffect(Unit) {
                            settingsRepository.getUserProfile().collect { profile ->
                                defaultPlatform = profile?.defaultPlatform
                            }
                        }
                        LaunchedEffect(selectedLinkId, apiKey, domain) {
                            if (ShortLinkService.isConfigured(context)) {
                                detailsLoading = true
                                detailsError = null
                                val result = ShortLinkService.fetchLinkDetails(context)
                                detailsLoading = false
                                result.onSuccess { currentLinkDetails = it }.onFailure { detailsError = it.message }
                            }
                        }
                        ArrowPreference(
                            title = "当前指向",
                            summary = when {
                                detailsLoading -> "更新中..."
                                detailsError != null -> "获取失败: $detailsError"
                                currentLinkDetails != null && currentLinkDetails!!.originalURL.isNotBlank() -> {
                                    "${defaultPlatform ?: ""} ${currentLinkDetails!!.originalURL}".trim()
                                }
                                else -> "未设置目标地址"
                            }
                        )
                    }
                }
            }

            // 高级设置
            item(key = "title_advanced") {
                SmallTitle(text = "高级设置", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
            }
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
                            Text(text = "API 地址", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                            TextField(value = apiUrl, onValueChange = { apiUrl = it; saveAdvanced(context, customEnabled, it, updatePath, apiMethod, authHeader, authPrefix, updateBody) }, label = "https://api.example.com", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "更新端点", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                            TextField(value = updatePath, onValueChange = { updatePath = it; saveAdvanced(context, customEnabled, apiUrl, it, apiMethod, authHeader, authPrefix, updateBody) }, label = "/links/{linkId}", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "HTTP 方法", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                            TextField(value = apiMethod, onValueChange = { apiMethod = it; saveAdvanced(context, customEnabled, apiUrl, updatePath, it, authHeader, authPrefix, updateBody) }, label = "POST", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "认证头名称", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                            TextField(value = authHeader, onValueChange = { authHeader = it; saveAdvanced(context, customEnabled, apiUrl, updatePath, apiMethod, it, authPrefix, updateBody) }, label = "Authorization", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "认证前缀", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                            TextField(value = authPrefix, onValueChange = { authPrefix = it; saveAdvanced(context, customEnabled, apiUrl, updatePath, apiMethod, authHeader, it, updateBody) }, label = "Bearer ", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "更新请求体", fontSize = 15.sp); Spacer(Modifier.height(4.dp))
                            TextField(value = updateBody, onValueChange = { updateBody = it; saveAdvanced(context, customEnabled, apiUrl, updatePath, apiMethod, authHeader, authPrefix, it) }, label = """{"originalURL":"{url}"}""", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (customEnabled) {
                item(key = "advanced_help") {
                    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                        Text(text = "占位符说明\n{url} → 目标链接\n{linkId} → 链接 ID", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, lineHeight = 18.sp)
                    }
                }
            }
        }
    }

    // 域名选择弹窗
    if (showDomainDialog) {
        val dialogVisible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { dialogVisible.value = true }
        DialogLayout(visible = dialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(360.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("选择域名", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        when {
                            domainsLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(36.dp)) }
                            domainError != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = domainError ?: "未知错误", fontSize = 13.sp, color = Color.Red, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(text = "重试", onClick = { scope.launch { domainsLoading = true; domainError = null; ShortLinkService.fetchDomains(context).onSuccess { domains = it }.onFailure { domainError = it.message }; domainsLoading = false } }, colors = ButtonDefaults.textButtonColorsPrimary())
                                }
                            }
                            domains.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("没有可用域名\n请在 short.io 后台添加", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center) }
                            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                                items(domains) { d ->
                                    val isSelected = d.hostname == domain
                                    Box(modifier = Modifier.fillMaxWidth().clickable { ShortLinkService.saveDomainSelection(context, d); domain = d.hostname; selectedLinkId = ""; shortUrl = null; showDomainDialog = false }.background(if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent, miuixShape(8.dp)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                                        Text(text = d.hostname, fontSize = 14.sp, color = if (isSelected) MiuixTheme.colorScheme.primary else Color.Unspecified, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(text = "关闭", onClick = { showDomainDialog = false }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    // 链接选择弹窗
    if (showLinkDialog) {
        val dialogVisible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { dialogVisible.value = true }
        DialogLayout(visible = dialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(420.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("选择短链接", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        when {
                            linksLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(36.dp)) }
                            linkError != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = linkError ?: "未知错误", fontSize = 13.sp, color = Color.Red, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(text = "重试", onClick = { scope.launch { loadLinks() } }, colors = ButtonDefaults.textButtonColorsPrimary())
                                }
                            }
                            links.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("该域名下还没有短链接", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(text = "创建一个", onClick = { showLinkDialog = false; showCreateDialog = true }, colors = ButtonDefaults.textButtonColorsPrimary())
                                }
                            }
                            else -> LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(links) { link ->
                                    val isSelected = link.idString == selectedLinkId
                                    Column(modifier = Modifier.fillMaxWidth().clickable { ShortLinkService.saveLinkSelection(context, link); selectedLinkId = link.idString; shortUrl = link.shortURL.ifBlank { "https://$domain/${link.path}" }; showLinkDialog = false }.background(if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f), miuixShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Text(text = link.shortURL.ifBlank { "$domain/${link.path}" }, fontSize = 14.sp, color = if (isSelected) MiuixTheme.colorScheme.primary else Color.Unspecified, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (link.originalURL.isNotBlank()) { Text(text = link.originalURL, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(text = "关闭", onClick = { showLinkDialog = false }, modifier = Modifier.fillMaxWidth())
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
        val dialogVisible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { dialogVisible.value = true }
        DialogLayout(visible = dialogVisible, enableWindowDim = true, renderInRootScaffold = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), insideMargin = PaddingValues(24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("创建短链接", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text("目标链接（对方碰 NFC 后打开的地址）", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(8.dp))
                        TextField(value = createUrl, onValueChange = { createUrl = it; createError = null }, label = "https://example.com", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                        if (createError != null) { Spacer(Modifier.height(4.dp)); Text(text = createError!!, fontSize = 12.sp, color = Color.Red) }
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(text = "取消", onClick = { showCreateDialog = false }, modifier = Modifier.weight(1f))
                            TextButton(text = if (creating) "创建中..." else "创建", onClick = {
                                if (createUrl.isBlank()) { createError = "请输入目标链接"; return@TextButton }
                                creating = true; createError = null
                                scope.launch {
                                    val result = ShortLinkService.createShortIoLink(context, createUrl)
                                    creating = false
                                    result.onSuccess { link -> ShortLinkService.saveLinkSelection(context, link); selectedLinkId = link.idString; shortUrl = link.shortURL.ifBlank { "https://$domain/${link.path}" }; links = listOf(link); showCreateDialog = false }
                                        .onFailure { createError = it.message ?: "创建失败" }
                                }
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), enabled = !creating)
                        }
                    }
                }
            }
        }
    }
}
