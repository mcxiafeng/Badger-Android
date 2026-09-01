package top.mcxiafeng.badger

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ui.navigation.AppNavigator
import top.mcxiafeng.badger.ui.navigation.Route

/** Consumes cold/hot deep-link events and turns them into navigation intents. */
@Composable
fun AppDeepLinkEffect(
    navigator: AppNavigator,
    appViewModel: AppViewModel,
) {
    val context = LocalContext.current

    suspend fun resolve(serverId: String) {
        val contactId = withContext(Dispatchers.IO) {
            appViewModel.findContactIdByServerId(serverId)
        }
        if (contactId != null) {
            navigator.navigate(Route.ContactDetail(contactId))
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "未找到该联系人", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(context, navigator, appViewModel) {
        val activity = context as? MainActivity ?: return@LaunchedEffect
        activity.consumeDeepLink()?.let { serverId ->
            resolve(serverId)
        }
    }

    LaunchedEffect(context, navigator, appViewModel) {
        val activity = context as? MainActivity ?: return@LaunchedEffect
        activity.deepLinkEvents.collect { serverId ->
            resolve(serverId)
        }
    }
}