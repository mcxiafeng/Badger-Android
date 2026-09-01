package top.mcxiafeng.badger.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import top.mcxiafeng.badger.LegacyTagFixup
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.queue.PendingPersonUpdateStore
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.CollectionRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.ContactRepositoryImpl
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.FieldRepositoryImpl
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository
import top.mcxiafeng.badger.data.repository.OperationHistoryRepositoryImpl
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.SyncStatusRepositoryImpl
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.data.repository.WorldRegionRepository
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.DeviceRepository
import top.mcxiafeng.badger.domain.DuplicateDetectionUseCase
import top.mcxiafeng.badger.domain.MergeContactUseCase
import top.mcxiafeng.badger.domain.ParseQrCodeUseCase
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.SaveScannedContactUseCase
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.pages.setupguide.SetupGuideViewModel
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingPersonUpdateScheduler
import top.mcxiafeng.badger.sync.SyncRepository

val databaseModule = module {
    single { AppDatabase.build(get()) }
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
    single { PendingPersonUpdateStore(get()) }
}

val repositoryModule = module {
    singleOf(::ContactRepositoryImpl) { bind<ContactRepository>() }
    singleOf(::FieldRepositoryImpl) { bind<FieldRepository>() }
    singleOf(::CollectionRepositoryImpl) { bind<CollectionRepository>() }
    singleOf(::UserProfileRepositoryImpl) { bind<UserProfileRepository>() }
    singleOf(::TagRepositoryImpl) { bind<TagRepository>() }
    singleOf(::OperationHistoryRepositoryImpl) { bind<OperationHistoryRepository>() }
    singleOf(::SyncStatusRepositoryImpl) { bind<SyncStatusRepository>() }
    single { UserProfileTicker() }
}

val networkModule = module {
    single { ServerApiFactory() }
    single<ServerApi> { get<ServerApiFactory>().get() }
    single { top.mcxiafeng.badger.NetworkModule.provideTokenHolder() }
    single {
        top.mcxiafeng.badger.NetworkModule.provideOkHttpClient(
            androidContext(), get(), get(), get<PendingPersonUpdateScheduler>(), get<PendingPersonUpdateStore>()
        )
    }
    singleOf(::ContactNetworkResolver)
    singleOf(::ShortLinkService)
}

val imageModule = module {
    single<ImageLoader> {
        val context = androidContext()
        val okHttpClient: okhttp3.OkHttpClient = get()
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("image_cache")).maxSizePercent(0.02).build() }
            .components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
            .crossfade(true)
            .build()
    }
}

val useCaseModule = module {
    factoryOf(::DuplicateDetectionUseCase)
    factoryOf(::MergeContactUseCase)
    factoryOf(::ParseQrCodeUseCase)
    factoryOf(::PrepareNfcWriteUseCase)
    factoryOf(::SaveScannedContactUseCase)
    singleOf(::SelectPlatformUseCase)
}

val appStateModule = module {
    singleOf(::ServerUrlHolder)
    singleOf(::WorldRegionRepository)
    singleOf(::UserAuthRepository)
    singleOf(::AiTagGenerator)
    singleOf(::SyncRepository)
    singleOf(::DeviceIdProvider)
    singleOf(::LegacyTagFixup)
    singleOf(::PlatformManifestRepository)
    singleOf(::PendingPersonUpdateScheduler)
    single(createdAtStart = true) { NotificationRepository(serverApi = get(), userAuthRepository = get()) }
    single { DeviceRepository(serverApi = get(), userAuthRepository = get()) }
}

val viewModelModule = module {
    viewModel { top.mcxiafeng.badger.AppViewModel(get(), get(), get(), get(), get()) }
    viewModel { top.mcxiafeng.badger.pages.auth.AuthViewModel() }
    viewModel {
        top.mcxiafeng.badger.pages.card.CardViewModel(
            repository = get(),
            contactRepository = get(),
            fieldRepository = get(),
            tagRepository = get(),
        )
    }
    viewModel { top.mcxiafeng.badger.pages.person.PersonViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.ContactDetailViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.CreateContactViewModel(get(), get()) }
    viewModel { top.mcxiafeng.badger.pages.person.contact.UserProfileDetailViewModel(get()) }
    viewModel { top.mcxiafeng.badger.pages.person.contact.CountryPickerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.RegionPickerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.scanner.ScannerViewModel() }
    viewModel {
        top.mcxiafeng.badger.pages.settings.AccountSettingsViewModel(
            context = androidContext(),
            userAuthRepository = get(),
            serverApiFactory = get(),
            serverUrlHolder = get(),
        )
    }
    viewModel { top.mcxiafeng.badger.pages.settings.NotificationViewModel(get(), get(), get()) }
    viewModel { top.mcxiafeng.badger.pages.settings.DeviceViewModel(get(), get(), get()) }
    viewModel { top.mcxiafeng.badger.pages.dashboard.DashboardViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.OperationHistoryViewModel(get()) }
    viewModel {
        top.mcxiafeng.badger.pages.settings.SettingsHomeViewModel(
            context = androidContext(),
            userAuthRepository = get(),
            serverUrlHolder = get(),
            syncStatusRepository = get(),
            notificationRepository = get(),
        )
    }
    viewModel {
        top.mcxiafeng.badger.pages.settings.SyncStatusViewModel(
            context = androidContext(),
            repository = get(),
        )
    }
    viewModel { top.mcxiafeng.badger.pages.settings.TagManagerSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.PlatformListViewModel() }
    viewModel {
        top.mcxiafeng.badger.pages.social.SocialViewModel(
            repository = get(),
            applicationContext = androidContext(),
            selectPlatformUseCase = get(),
            prepareNfcWriteUseCase = get(),
        )
    }
    viewModel { SetupGuideViewModel(get(), get(), androidContext(), get(), get(), get(), get()) }
    viewModel { top.mcxiafeng.badger.pages.settings.ChangePasswordViewModel(get()) }
    viewModel { top.mcxiafeng.badger.pages.settings.ServerShortLinkViewModel(get(), get()) }
}
