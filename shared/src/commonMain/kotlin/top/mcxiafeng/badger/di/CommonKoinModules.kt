package top.mcxiafeng.badger.di

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.LegacyTagFixup
import top.mcxiafeng.badger.data.SessionDataCleaner
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.CollectionRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.ContactRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactWriter
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.FieldRepositoryImpl
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository
import top.mcxiafeng.badger.data.repository.OperationHistoryRepositoryImpl
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.SyncStatusRepositoryImpl
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.data.repository.DeviceRepository
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.WorldRegionRepository
import top.mcxiafeng.badger.domain.DuplicateDetectionUseCase
import top.mcxiafeng.badger.domain.ImportProfileFieldsUseCase
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.RefreshFromServerUseCase
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.OutboxQueue
import top.mcxiafeng.badger.sync.OutboxStore
import top.mcxiafeng.badger.sync.SyncEngine

val useCaseModule = module {
    factoryOf(::DuplicateDetectionUseCase)
    
    factoryOf(::ImportProfileFieldsUseCase)
    factoryOf(::PrepareNfcWriteUseCase)
    
    singleOf(::SelectPlatformUseCase)
    
    factoryOf(::RefreshFromServerUseCase)
}

val viewModelModule = module {
    viewModel {
        
        top.mcxiafeng.badger.AppViewModel(
            userProfileRepository = get(),
            userProfileTicker = get(),
            userAuthRepository = get(),
            notificationRepository = get(),
            contactRepository = get(),
            importProfileFieldsUseCase = get(),
            syncEngine = get(),
        )
    }
    viewModel { top.mcxiafeng.badger.pages.auth.AuthViewModel() }
    viewModel { top.mcxiafeng.badger.pages.card.CardViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.PersonViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.detail.ContactDetailViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.CreateContactViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.UserProfileDetailViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.dialogs.CountryPickerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.dialogs.RegionPickerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.scanner.ScannerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.account.AccountSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.notification.NotificationViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.devices.DeviceViewModel() }
    viewModel { top.mcxiafeng.badger.pages.dashboard.DashboardViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.history.OperationHistoryViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.SettingsHomeViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.UserSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.sync.SyncStatusViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.tags.TagManagerSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.social.SocialViewModel() }
    viewModel { top.mcxiafeng.badger.pages.setupguide.SetupGuideViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.account.ChangePasswordViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.sync.ServerShortLinkViewModel() }
}

val daoModule = module {
    single { get<AppDatabase>().contactCacheDao() }
    single { get<AppDatabase>().contactFieldCacheDao() }
    single { get<AppDatabase>().contactFieldValueCacheDao() }
    single { get<AppDatabase>().contactPlatformCacheDao() }
    single { get<AppDatabase>().tagCacheDao() }
    single { get<AppDatabase>().cardCollectionCacheDao() }
    single { get<AppDatabase>().userProfileCacheDao() }
    single { get<AppDatabase>().contactTagCacheDao() }
    single { get<AppDatabase>().syncCursorDao() }
    single { get<AppDatabase>().personProfileCacheDao() }
    single { get<AppDatabase>().customFieldCacheDao() }
    single { get<AppDatabase>().collectionMemberCacheDao() }
    single { get<AppDatabase>().operationHistoryDao() }
    single { get<AppDatabase>().outboxDao() }
    
    
    singleOf(::OutboxStore) { bind<OutboxQueue>() }
}

fun commonRepositoryModule(
    avatarFetcher: suspend (String, Long) -> String?,
) = module {
    
    single<suspend (String, Long) -> String?> { avatarFetcher }
    singleOf(::ContactRepositoryImpl) { bind<ContactRepository>() }
    singleOf(::FieldRepositoryImpl) { bind<FieldRepository>() }
    singleOf(::CollectionRepositoryImpl) { bind<CollectionRepository>() }
    singleOf(::UserProfileRepositoryImpl) { bind<UserProfileRepository>() }
    singleOf(::TagRepositoryImpl) { bind<TagRepository>() }
    singleOf(::OperationHistoryRepositoryImpl) { bind<OperationHistoryRepository>() }
    singleOf(::SyncStatusRepositoryImpl) { bind<SyncStatusRepository>() }
    singleOf(::ContactWriter)

    single { UserProfileTicker() }
}

val commonAppStateModule = module {
    singleOf(::ServerUrlHolder)
    singleOf(::WorldRegionRepository)
    
    singleOf(::SessionDataCleaner)
    singleOf(::UserAuthRepository)
    singleOf(::AiTagGenerator)
    
    singleOf(::SyncEngine)
    singleOf(::DeviceIdProvider)
    singleOf(::LegacyTagFixup)
    
    singleOf(::PlatformManifestRepository)
    
    single(createdAtStart = true) { NotificationRepository(serverApi = get(), userAuthRepository = get()) }
    
    single { DeviceRepository(serverApi = get(), userAuthRepository = get()) }
    
}
