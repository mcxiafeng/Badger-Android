package top.mcxiafeng.badger.di
import top.mcxiafeng.badger.LegacyTagFixup
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.WorldRegionRepository
import top.mcxiafeng.badger.domain.DuplicateDetectionUseCase
import top.mcxiafeng.badger.domain.FilterContactsUseCase
import top.mcxiafeng.badger.domain.MergeContactUseCase
import top.mcxiafeng.badger.domain.ParseQrCodeUseCase
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.SaveScannedContactUseCase
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.SyncRepository

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.ContactFieldDao
import top.mcxiafeng.badger.data.ContactFieldValueDao
import top.mcxiafeng.badger.data.ContactPlatformDao
import top.mcxiafeng.badger.data.CustomFieldDao
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.CollectionRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.ContactRepositoryImpl
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.FieldRepositoryImpl
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository
import top.mcxiafeng.badger.data.repository.OperationHistoryRepositoryImpl
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.SyncStatusRepositoryImpl
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.network.ServerApi

/**
 * [§14.2] Koin 模块总览。
 *
 * 按职责拆分 5 个 module,在 [BadgerApplication.onCreate] 内通过 `startKoin{}` 一次性
 * 装载。每个 module 的依赖关系通过 `singleOf { Impl() }` + 接口绑定实现 —
 * 与原 Hilt `@Binds` 一一对应。
 *
 * **为何按职责拆分**:单文件 5 个 module 加载,调试时 grep 容易命中;改一处只动一个 module。
 * **为何不复刻原 DataModule 嵌套**:Koin 单 module 就够清晰,拆太细反而难追。
 *
 * 与原 Hilt 拓扑对齐:
 * | 旧 Hilt                            | 新 Koin               |
 * |------------------------------------|-----------------------|
 * | @HiltAndroidApp BadgerApplication | Application + startKoin{} |
 * | @HiltViewModel X*ViewModel         | module { viewModelOf(::X) } |
 * | @Module @Provides DatabaseModule   | databaseModule        |
 * | @Module @Provides NetworkModule    | networkModule         |
 * | @Module @Provides ImageModule      | imageModule           |
 * | @Module @Binds DataModule          | repositoryModule      |
 * | @Module @Provides AuthModule       | authModule            |
 * | @HiltWorker + @AssistedInject      | 删 HiltWorker 注解,worker 由 SyncWorkerFactory 手动构造 |
 */

val databaseModule = module {

    single {
        // [§14.2] 改用 androidContext() 而非 Hilt 的 @ApplicationContext。
        AppDatabase.build(get())
    }

    // ============ V1 DAOs(老 schema,仍在 V2 代码路径上做平台字段 / FTS 查询) ============
    single<ContactFieldDao> { get<AppDatabase>().contactFieldDao() }
    single<ContactFieldValueDao> { get<AppDatabase>().contactFieldValueDao() }
    single<CustomFieldDao> { get<AppDatabase>().customFieldDao() }
    single<ScanResultDao> { get<AppDatabase>().scanResultDao() }
    single<ContactPlatformDao> { get<AppDatabase>().contactPlatformDao() }

    // ============ V2 cache DAOs ============
    single { get<AppDatabase>().contactCacheDao() }
    single { get<AppDatabase>().contactFieldCacheDao() }
    single { get<AppDatabase>().contactFieldValueCacheDao() }
    single { get<AppDatabase>().contactPlatformCacheDao() }
    single { get<AppDatabase>().tagCacheDao() }
    single { get<AppDatabase>().cardCollectionCacheDao() }
    single { get<AppDatabase>().userProfileCacheDao() }
    single { get<AppDatabase>().contactTagCacheDao() }
    single { get<AppDatabase>().syncCursorDao() }

    // ============ [V2-P2] queue DAOs（Phase 3 后降级为本地只读日志，保留表结构） ============
    single { get<AppDatabase>().pendingUploadDao() }
    single { get<AppDatabase>().operationHistoryDao() }
}

/**
 * 仓库 / 单例绑定 — 对应原 Hilt `DataModule` 的 `@Binds`。
 *
 * 用 `singleOf(::Impl) { bind<Iface>() }` 显式声明"用 Impl 实现 Iface",保持原
 * `repository.contactRepository` 的注入面不变。
 *
 * 注意:[ServerApi] / [ServerApiFactory] 留在 [networkModule] 中,避免在 repository
 * module 阶段未装好网络层就提前解析。
 */
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

/**
 * 网络层 — 对应原 Hilt `NetworkModule` + `AuthModule`。
 *
 * 关键保留:[ServerApiFactory] 持有 Volatile ServerApi 引用,需在 NetworkModule.provideOkHttpClient
 * 阶段完成 `factory.install(api, initialUrl)`。Koin 模式下由 [top.mcxiafeng.badger.NetworkModule]
 * 自行 install,与 Koin 解耦;此 module 仅负责 `factory { ServerApiFactory() }` 与 `OkHttpClient`。
 *
 * ServerApi 实例本身**也**通过 factory 提供,因为 `factory.get()` 是延迟求值,koin 解析时
 * 如果 factory 还没装入,使用方可在第一次 get().error() 处被识别。
 */
val networkModule = module {
    single { ServerApiFactory() }
    // [§14.2 修复] ServerApi 的解析必须顺带触发 OkHttpClient 构造,
    // 否则 NetworkModule.provideOkHttpClient 内的 `factory.install(api, initialUrl)`
    // 永远不会被调用,后续 get<ServerApiFactory>().get() 抛
    // `ServerApi not yet installed`。
    // 显式 `get<OkHttpClient>()` 让 Koin 知道该 lambda 依赖 OkHttpClient → 装载时
    // 链式解析 → install() 落地。
    single<ServerApi> {
        val factory: ServerApiFactory = get()
        get<okhttp3.OkHttpClient>()
        factory.get()
    }
    single { top.mcxiafeng.badger.NetworkModule.provideTokenHolder() }
    single { top.mcxiafeng.badger.NetworkModule.provideOkHttpClient(androidContext(), get(), get()) }
}

/**
 * ImageLoader — 与原 Hilt `ImageModule.provideImageLoader` 行为一致。
 */
val imageModule = module {
    single<ImageLoader> {
        val context = androidContext()
        val okHttpClient: okhttp3.OkHttpClient = get()
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .crossfade(true)
            .build()
    }
}

/**
 * UseCase — 对应原 Hilt 自动 `@Inject constructor`(无显式 Provider)。
 *
 * 原代码里 8 个 UseCase 都是无依赖(`@Inject constructor()`);Koin 这里简化成
 * `factoryOf(::UseCase)`,按需创建。
 */
val useCaseModule = module {
    factoryOf(::DuplicateDetectionUseCase)
    factoryOf(::FilterContactsUseCase)
    factoryOf(::MergeContactUseCase)
    factoryOf(::ParseQrCodeUseCase)
    factoryOf(::PrepareNfcWriteUseCase)
    factoryOf(::SaveScannedContactUseCase)
    factoryOf(::SelectPlatformUseCase)
    singleOf(::ServerUrlHolder)
    singleOf(::WorldRegionRepository)
    singleOf(::UserAuthRepository)
    singleOf(::AiTagGenerator)
    // [Phase 3] 服务端权威同步引擎（退役 ContactSyncBootstrapper/PendingUploadExecutor/Scheduler/Snapshotter）
    singleOf(::SyncRepository)
    singleOf(::DeviceIdProvider)
    singleOf(::LegacyTagFixup)
    // [Phase 4 剩余] 服务端平台清单缓存（`/api/resolve/platforms` 接入 UI 的单一来源）。
    singleOf(::PlatformManifestRepository)
}

/** ViewModel registrations consumed by Compose `koinViewModel()`. */
val viewModelModule = module {
    viewModel { top.mcxiafeng.badger.AppViewModel() }
    viewModel { top.mcxiafeng.badger.pages.auth.AuthViewModel() }
    viewModel { top.mcxiafeng.badger.pages.card.CardViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.PersonViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.ContactDetailViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.CreateContactViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.UserProfileDetailViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.CountryPickerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.RegionPickerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.scanner.ScannerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.AccountSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.CloudSyncSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.CloudBackupViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.NfcSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.OperationHistoryViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.SettingsHomeViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.SyncStatusViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.TagManagerSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.PlatformListViewModel() }
    viewModel { top.mcxiafeng.badger.pages.social.SocialViewModel() }
    viewModel { top.mcxiafeng.badger.pages.setupguide.SetupGuideViewModel() }
}
