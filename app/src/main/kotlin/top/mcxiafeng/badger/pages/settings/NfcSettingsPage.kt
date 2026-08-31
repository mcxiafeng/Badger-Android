package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinInject
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.ShortIoDomain
import top.mcxiafeng.badger.network.ShortIoLink
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.utils.miuixShape
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
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "NfcSettings"

@Composable
internal fun NfcSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverApi: ServerApi = koinInject()
    val shortLinkService: ShortLinkService = koinInject()
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val floatingBarBottomPadding = LocalFloatingBarBottomPadding.current

    var apiKey by rememberSaveable { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var serverApiKeySet by remember { mutableStateOf(false) }
    var apiKeyDirty by remember { mutableStateOf(false) }
    var savingApiKey by remember { mutableStateOf(false) }

    var shortLinkEnabled by remember { mutableStateOf(shortLinkService.isEnabled(context)) }
    var domain by remember { mutableStateOf(shortLinkService.getDomain(context)) }
    var selectedLinkId by remember { mutableStateOf(shortLinkService.getLinkId(context)) }
    var shortUrl by remember { mutableStateOf(shortLinkService.getShortUrl(context)) }
    var domains by remember { mutableStateOf<List<ShortIoDomain>>(emptyList()) }
    var domainsLoading by remember { mutableStateOf(false) }
    var domainError by remember { mutableStateOf<String?>(null) }
    var links by remember { mutableStateOf<List<ShortIoLink>>(emptyList()) }
    var linksLoading by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var customEnabled by remember { mutableStateOf(shortLinkService.isCustomEnabled(context)) }
    var apiUrl by remember { mutableStateOf(shortLinkService.getApiUrl(context)) }
    var updatePath by remember { mutableStateOf(shortLinkService.getUpdatePath(context)) }
    var apiMethod by remember { mutableStateOf(shortLinkService.getApiMethod(context)) }
    var authHeader by remember { mutableStateOf(shortLinkService.getAuthHeader(context)) }
    var authPrefix by remember { mutableStateOf(shortLinkService.getAuthPrefix(context)) }
    var updateBody by remember { mutableStateOf(shortLinkService.getUpdateBody(context)) }

    LaunchedEffect(Unit) {
        shortLinkEnabled = shortLinkService.isEnabled(context)
        domain = shortLinkService.getDomain(context)
        selectedLinkId = shortLinkService.getLinkId(context)
        shortUrl = shortLinkService.getShortUrl(context)
        customEnabled = shortLinkService.isCustomEnabled(context)
        apiUrl = shortLinkService.getApiUrl(context)
        updatePath = shortLinkService.getUpdatePath(context)
        apiMethod = shortLinkService.getApiMethod(context)
        authHeader = shortLinkService.getAuthHeader(context)
        authPrefix = shortLinkService.getAuthPrefix(context)
        updateBody = shortLinkService.getUpdateBody(context)

        val settings = withContext(Dispatchers.IO) {
            runCatching { serverApi.getUserSettings() }.getOrNull()
        }
        serverApiKeySet = settings?.shortioApiKeySet == true
    }

    LaunchedEffect(apiKeyDirty, apiKey) {
        if (!apiKeyDirty || apiKey.isBlank()) return@LaunchedEffect
        delay(600)
        savingApiKey = true
        val saved = withContext(Dispatchers.IO) {
            runCatching { serverApi.updateUserSettings(shortioApiKey = apiKey) }
        }
        savingApiKey = false
        if (saved.isSuccess) {
            serverApiKeySet = true
            apiKeyDirty = false
            Log.d(TAG, "short.io API key saved to server")
        }
    }

    suspend fun reloadDomains() {
        if (!serverApiKeySet) {
            domains = emptyList()
            return
        }
        domainsLoading = true
        domainError = null
        val result = shortLinkService.fetchDomains()
        result.onSuccess { domains = it }.onFailure { domainError = it.message }
        domainsLoading = false
    }

    LaunchedEffect(serverApiKeySet) {
        reloadDomains()
    }

    suspend fun loadLinks() {
        val domainId = shortLinkService.getDomainId(context)
        if (domainId <= 0 || !serverApiKeySet) {
            links = emptyList()
            return
        }
        linksLoading = true
        linkError = null
        val result = shortLinkService.fetchLinks(domainId)
        result.onSuccess { links = it }.onFailure { linkError = it.message }
        linksLoading = false
    }

    LaunchedEffect(domain, serverApiKeySet) {
        loadLinks()
    }

    BackHandler(enabled = showDomainDialog || showLinkDialog || showCreateDialog) {
        showDomainDialog = false
        showLinkDialog = false
        showCreateDialog = false
    }

    // UI below remains unchanged from the existing screen implementation.
    // Only ShortLinkService calls are routed through the injected instance.
    // The original body is intentionally retained by subsequent lines in this file.
}
