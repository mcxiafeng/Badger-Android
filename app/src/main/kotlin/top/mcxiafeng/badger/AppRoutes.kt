package top.mcxiafeng.badger

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.ocr.FIELD_DEF_MAP
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.pages.auth.LoginScreen
import top.mcxiafeng.badger.pages.auth.RegisterScreen
import top.mcxiafeng.badger.pages.card.CollectionDetailPage
import top.mcxiafeng.badger.pages.person.contact.detail.ContactDetailPage
import top.mcxiafeng.badger.pages.person.contact.CreateContactPage
import top.mcxiafeng.badger.pages.scanner.ScannerPage
import top.mcxiafeng.badger.pages.settings.SettingsSubPage
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.ui.navigation.SettingsPage

@RequiresApi(Build.VERSION_CODES.R)
@Composable
internal fun AppSubRouteContent(
    currentRoute: Route,
    navigator: AppNavigator,
    onNavigateBack: () -> Unit,
    scope: CoroutineScope,
    appContext: Context,
    userProfileRepository: UserProfileRepository,
    pagerState: PagerState,
    onRefreshUserProfile: () -> Unit,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit,
) {
    BackHandler(onBack = { onNavigateBack() })
    when (currentRoute) {
        is Route.Login -> {
            LoginScreen(
                onAuthed = {
                    navigator.resetToMain()
                },
                onNavigateToRegister = { navigator.navigate(Route.Register) },
                onBack = { onNavigateBack() },
            )
        }
        is Route.Register -> {
            RegisterScreen(
                onAuthed = {
                    navigator.resetToMain()
                },
                onNavigateToLogin = { navigator.navigate(Route.Login) },
                onBack = { onNavigateBack() },
            )
        }
        is Route.Scanner -> {
            ScannerPage(
                onBack = { onNavigateBack() },
                targetCollectionId = if (currentRoute.mode == "collection") currentRoute.targetCollectionId else null,
                onNavigateToAiSettings = { navigator.navigate(Route.SettingsSubPage(SettingsPage.NfcSettings)) },
                onNavigateToCreateContact = {
                    navigator.navigate(Route.CreateContact(targetCollectionId = currentRoute.targetCollectionId))
                },
                onImportToProfile = if (currentRoute.mode == "importProfile") { { items ->
                    scope.launch(Dispatchers.IO) {
                        var importedCount = 0
                        for ((rawContent, info) in items) {
                            info.toFieldValues().forEach { (key, value) ->
                                if (value.isNotBlank() && key != "phone" && key != "email") {
                                    val displayName = FIELD_DEF_MAP[key]?.displayName ?: key
                                    val jumpLink = buildPlatformLink(key, value)
                                    val adapterResult = try {
                                        ContactNetworkResolver.getResultInfo(jumpLink, mutableMapOf())
                                    } catch (e: Exception) {
                                        Log.w("App", "导入时平台信息解析失败", e)
                                        null
                                    }
                                    val platformName = adapterResult?.nickname?.takeIf { it.isNotBlank() && it != "未知" }
                                    val platformAvatar = adapterResult?.avatarUrl?.takeIf { it.isNotBlank() }
                                    userProfileRepository.updatePlatformField(displayName, jumpLink, value, platformName, platformAvatar)
                                    importedCount++
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            onNavigateBack()
                            if (importedCount > 0) {
                                Toast.makeText(appContext, "已导入 $importedCount 个平台", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(appContext, "未识别到可导入的平台", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } } else null
            )
        }

        is Route.ContactDetail -> {
            ContactDetailPage(
                contactId = currentRoute.contactId,
                onBack = { onNavigateBack() },
                onRefreshData = {
                    // [修复防御]: 详情页发生数据变更（同步信息/编辑头像/编辑联系人等），
                    // 切到 PersonRoute 那一页（PagerState 仍在 composition 中），
                    // 触发 PersonViewModel.refreshUserProfile() 拉一次最新 UserProfile。
                    scope.launch {
                        pagerState.animateScrollToPage(1)
                        onRefreshUserProfile()
                    }
                },
                onOpenScannerForImport = if (currentRoute.contactId == -1L) {{
                    navigator.navigate(Route.Scanner(mode = "importProfile"))
                }} else null
            )
        }

        is Route.SettingsSubPage -> {
            SettingsSubPage(
                page = currentRoute.page,
                onBack = { onNavigateBack() },
                onNavigateToSubPage = { subPage -> navigator.navigate(Route.SettingsSubPage(subPage)) },
                onNavigateToLogin = { navigator.navigate(Route.Login) },
                onNavigateToMyProfile = { navigator.navigate(Route.ContactDetail(-1L)) },
                onNavigateToContact = { contactId -> navigator.navigate(Route.ContactDetail(contactId)) },
                devMode = devMode,
                onDevModeChange = { onDevModeChange(it) },
            )
        }

        is Route.CollectionDetail -> {
            CollectionDetailPage(
                collectionId = currentRoute.collectionId,
                onBack = { onNavigateBack() },
                onNavigateToScanner = { cid ->
                    navigator.navigate(Route.Scanner(mode = "collection", targetCollectionId = cid))
                },
                onNavigateToContactDetail = { cid ->
                    navigator.navigate(Route.ContactDetail(cid))
                },
                onNavigateToCreateContact = { cid ->
                    navigator.navigate(Route.CreateContact(targetCollectionId = cid))
                }
            )
        }

        is Route.CreateContact -> {
            CreateContactPage(
                targetCollectionId = currentRoute.targetCollectionId,
                onBack = { onNavigateBack() },
                onNavigateToContactDetail = { contactId ->
                    navigator.navigate(Route.ContactDetail(contactId))
                }
            )
        }

        is Route.MainTabs -> {}
    }
}
