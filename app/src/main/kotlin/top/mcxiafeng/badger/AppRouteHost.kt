package top.mcxiafeng.badger

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.pages.auth.LoginScreen
import top.mcxiafeng.badger.pages.auth.RegisterScreen
import top.mcxiafeng.badger.pages.card.CollectionDetailPage
import top.mcxiafeng.badger.pages.person.contact.ContactDetailPage
import top.mcxiafeng.badger.pages.person.contact.CreateContactPage
import top.mcxiafeng.badger.pages.scanner.ScannerPage
import top.mcxiafeng.badger.pages.settings.SettingsSubPage
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.Route
import top.mcxiafeng.badger.ui.navigation.SettingsPage

/** Dispatches secondary routes while keeping business work in [AppViewModel]. */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun AppRouteHost(
    route: Route,
    navigator: AppNavigator,
    appViewModel: AppViewModel,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun navigateBack() {
        if (!navigator.navigateBack()) navigator.resetToMain()
    }

    BackHandler(onBack = ::navigateBack)

    when (route) {
        Route.Login -> LoginScreen(
            onAuthed = navigator::resetToMain,
            onNavigateToRegister = { navigator.navigate(Route.Register) },
            onBack = ::navigateBack,
        )

        Route.Register -> RegisterScreen(
            onAuthed = navigator::resetToMain,
            onNavigateToLogin = { navigator.navigate(Route.Login) },
            onBack = ::navigateBack,
        )

        is Route.Scanner -> ScannerPage(
            onBack = ::navigateBack,
            targetCollectionId = route.targetCollectionId.takeIf { route.mode == "collection" },
            onNavigateToAiSettings = {
                navigator.navigate(Route.SettingsSubPage(SettingsPage.NfcSettings))
            },
            onNavigateToCreateContact = {
                navigator.navigate(Route.CreateContact(route.targetCollectionId))
            },
            onImportToProfile = if (route.mode == "importProfile") {
                { items ->
                    scope.launch {
                        val importedCount = withContext(Dispatchers.IO) {
                            appViewModel.importProfileFields(items.map { it.second })
                        }
                        navigateBack()
                        val message = if (importedCount > 0) {
                            "已导入 $importedCount 个平台"
                        } else {
                            "未识别到可导入的平台"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                null
            },
        )

        is Route.ContactDetail -> ContactDetailPage(
            contactId = route.contactId,
            onBack = ::navigateBack,
            onRefreshData = appViewModel::refreshUserProfile,
            onOpenScannerForImport = if (route.contactId == -1L) {
                { navigator.navigate(Route.Scanner(mode = "importProfile")) }
            } else {
                null
            },
        )

        is Route.SettingsSubPage -> SettingsSubPage(
            page = route.page,
            onBack = ::navigateBack,
            onNavigateToSubPage = { navigator.navigate(Route.SettingsSubPage(it)) },
            onNavigateToLogin = { navigator.navigate(Route.Login) },
            onNavigateToMyProfile = { navigator.navigate(Route.ContactDetail(-1L)) },
            onNavigateToContact = { navigator.navigate(Route.ContactDetail(it)) },
            devMode = devMode,
            onDevModeChange = onDevModeChange,
        )

        is Route.CollectionDetail -> CollectionDetailPage(
            collectionId = route.collectionId,
            onBack = ::navigateBack,
            onNavigateToScanner = { collectionId ->
                navigator.navigate(Route.Scanner(mode = "collection", targetCollectionId = collectionId))
            },
            onNavigateToContactDetail = { contactId ->
                navigator.navigate(Route.ContactDetail(contactId))
            },
            onNavigateToCreateContact = { collectionId ->
                navigator.navigate(Route.CreateContact(collectionId))
            },
        )

        is Route.CreateContact -> CreateContactPage(
            targetCollectionId = route.targetCollectionId,
            onBack = ::navigateBack,
            onNavigateToContactDetail = { contactId ->
                navigator.navigate(Route.ContactDetail(contactId))
            },
        )

        Route.MainTabs -> Unit
    }
}
