package top.mcxiafeng.badger.page.person.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.repository.PlatformRepository
import top.mcxiafeng.badger.data.repository.ProfileRepository
import top.mcxiafeng.badger.data.user.entity.Contact
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.network.core.ProfileApi
import top.mcxiafeng.badger.page.social.dialogs.QrCodeCard
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

private const val TAG = "ShareDialogTester"

@Composable
fun ShareDialog(
    person: Person?,
    profile: Profile? = null,
    show: Boolean = false,
    onDismissRequest: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope = rememberCoroutineScope(),
) {
    var selectedPlatformIndex by remember { mutableIntStateOf(0) }
    var fetchedProfile by remember(person?.profileId, profile?.uuid) { mutableStateOf<Profile?>(null) }

    val platformRepository = remember { PlatformRepository() }
    val profileRepository = remember { ProfileRepository() }

    val platformsList by remember { platformRepository.observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val profileUuid = profile?.uuid ?: person?.profileId

    LaunchedEffect(profileUuid, show) {
        if (!show || profileUuid == null) return@LaunchedEffect

        BadgerLog.d(TAG, "Fetching profile directly from network by uuid: $profileUuid")
        val netProfile = runCatching {
            (ProfileApi.getProfile(profileUuid) as? HttpResult.Success)
                ?.let { Json.parseToJsonElement(it.body).jsonObject["data"]?.jsonObject }
                ?.let { Json.decodeFromJsonElement<Profile>(it) }
        }.getOrNull()
        BadgerLog.d(TAG, "Fetching profile directly runCatching: $netProfile")
        if (netProfile != null) {
            fetchedProfile = netProfile
            runCatching { profileRepository.upsert(netProfile) }
            BadgerLog.d(TAG, "Profile fetched directly from network successfully: $profileUuid")
        } else {
            BadgerLog.d(TAG, "Network profile fetch failed/null, fallback to local DB for profile: $profileUuid")
            val dbProfile = runCatching { profileRepository.get(profileUuid) }.getOrNull()
            if (dbProfile != null) {
                fetchedProfile = dbProfile
                BadgerLog.d(TAG, "Profile loaded from local DB: $profileUuid")
            } else if (profile != null) {
                fetchedProfile = profile
                BadgerLog.d(TAG, "Fallback to passed-in profile: $profileUuid")
            }
        }
    }

    val activeProfile = fetchedProfile ?: profile
    val actualPlatforms: List<Contact>? = activeProfile?.contact

    // 当切换 Profile 或平台列表变化时，防止索引越界
    LaunchedEffect(activeProfile?.uuid, actualPlatforms) {
        if (actualPlatforms.isNullOrEmpty() || selectedPlatformIndex >= actualPlatforms.size) {
            selectedPlatformIndex = 0
        }
    }

    val currentPlatform = actualPlatforms?.getOrNull(selectedPlatformIndex)
    val currentPlatformValue = currentPlatform?.sourceUrl

    val displayName = person?.name?.ifBlank { null } ?: "未命名联系人"
    val effectiveAvatarPath = person?.avatarURL

    val targetPlatform = platformsList.firstOrNull { it.name == currentPlatform?.platformName }
    val platformName = targetPlatform?.displayName ?: currentPlatform?.platformName

    val dropdownItems = remember(actualPlatforms, platformsList) {
        actualPlatforms?.map { contact ->
            val platformInfo = platformsList.firstOrNull { it.name == contact.platformName }
            platformInfo?.displayName ?: contact.platformName
        } ?: emptyList()
    }

    QrCodeCard(
        userName = displayName,
        platformName = platformName,
        platformValue = currentPlatformValue,
        avatarPath = effectiveAvatarPath,
        externalShowDialog = show,
        onDialogDismiss = onDismissRequest
    ) {
        if (dropdownItems.isNotEmpty()) {
            WindowDropdownPreference(
                title = "选择平台",
                items = dropdownItems,
                selectedIndex = selectedPlatformIndex,
                onSelectedIndexChange = { selectedPlatformIndex = it }
            )
        }
    }
}
