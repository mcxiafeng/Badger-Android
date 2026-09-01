package top.mcxiafeng.badger.di
import top.mcxiafeng.badger.LegacyTagFixup
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.DeviceRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.WorldRegionRepository
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.AvatarStorage
import top.mcxiafeng.badger.data.queue.PendingPersonUpdateStore
import top.mcxiafeng.badger.domain.DuplicateDetectionUseCase
import top.mcxiafeng.badger.domain.ImportProfileFieldsUseCase
import top.mcxiafeng.badger.domain.MergeContactUseCase
import top.mcxiafeng.badger.domain.ParseQrCodeUseCase
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.SaveScannedContactUseCase
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingPersonUpdateScheduler
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
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactFieldValueCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.PersonProfileCacheDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.data.queue.OperationHistoryDao
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
    // [Phase 3 Task #17] 已退役: contactFieldDao / customFieldDao / contactFieldValueDao
    // [Phase 4 Task #19] 已退役: contactPlatformDao
    // [Phase 4 Task #20] 已退役: scanResultDao

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
    single { get<AppDatabase>().personProfileCacheDao() }
    // [Phase 3 Task #30] custom_fields V2 cache DAO
    single { get<AppDatabase>().customFieldCacheDao() }
    // [Phase 4 Task #20] 名片夹成员关联 V2 cache DAO
    single { get<AppDatabase>().collectionMemberCacheDao() }

    // ============ [V2-P2] queue DAO（Phase 4 后仅剩 operation_history 只读日志） ============
    single { get<AppDatabase>().operationHistoryDao() }
    // [迁移] 失败 Person PUT 的持久化 outbox（data/queue/PendingPersonUpdateStore）
    single { PendingPersonUpdateStore(get()) }
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
    // [迁移] 头像文件 IO 边界（UI 层不再直接写盘）
    singleOf(::AvatarStorage)
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
    single { top.mcxiafeng.badger.NetworkModule.provideTokenHolder() }
    // [迁移] OkHttpClient 注入 token 拦截器由 NetworkModule 统一构造（2 参签名）
    single {
        top.mcxiafeng.badger.NetworkModule.provideOkHttpClient(
            context = androidContext(),
            tokenHolder = get(),
        )
    }
    // [迁移] ServerApi 构造成功后才 install 进 factory；同时持有 outbox store/scheduler
    single<ServerApi> {
        top.mcxiafeng.badger.NetworkModule.provideServerApi(
            context = androidContext(),
            http = get(),
            tokenHolder = get(),
            pendingPersonUpdateStore = get(),
            pendingPersonUpdateScheduler = get(),
            factory = get(),
        )
    }
    // [迁移] resolver / 短链服务改为 Koin 单例（构造器注入 ServerApi）
    singleOf(::ContactNetworkResolver)
    singleOf(::ShortLinkService)
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
 *
 * [重构] 不再混入 Repository / StateHolder / Fixup 等单例 ——
 * 那些放到 [appStateModule],与 UseCase 区分开。
 */
val useCaseModule = module {
    factoryOf(::DuplicateDetectionUseCase)
    // [迁移] 扫码导入档案字段的编排用例（AppViewModel 消费）
    factoryOf(::ImportProfileFieldsUseCase)
    factoryOf(::MergeContactUseCase)
    factoryOf(::ParseQrCodeUseCase)
    factoryOf(::PrepareNfcWriteUseCase)
    factoryOf(::SaveScannedContactUseCase)
    // [迁移] singleOf：debounce/短链更新状态必须跨调用方共享（5829aa7）
    singleOf(::SelectPlatformUseCase)
}

/**
 * Application 单例 — Repository / StateHolder / 引导期 Fixup / 后台轮询器等。
 *
 * [重构] 这些与 UseCase 不同的关注点(状态托管、生命周期长)之前被混入 [useCaseModule],
 * 拆出后单一职责 + 装载顺序清晰(必须先于 viewModelModule,因为 VM 字段注入要拉这些)。
 */
val appStateModule = module {
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
    // [迁移] 失败 Person PUT 的 WorkManager 重放调度器
    singleOf(::PendingPersonUpdateScheduler)
    // [B1] 站内通知：未读 60s 轮询。显式 get() 避免 Koin 去解析默认的 Dispatcher/Scope 参数。
    // createdAtStart：B1 无 UI 时也要在 SignedIn 后开始轮询（B2 badge 才能立刻有数）。
    single(createdAtStart = true) { NotificationRepository(serverApi = get(), userAuthRepository = get()) }
    // [B3] 设备管理：无需轮询，UI 主动 refresh。
    single { DeviceRepository(serverApi = get(), userAuthRepository = get()) }
}

/** ViewModel registrations consumed by Compose `koinViewModel()`. */
val viewModelModule = module {
    viewModel {
        // [迁移] AppViewModel 改为构造器注入（App.kt 的 koinViewModel() 调用面不变）
        top.mcxiafeng.badger.AppViewModel(
            userProfileRepository = get(),
            userProfileTicker = get(),
            userAuthRepository = get(),
            notificationRepository = get(),
            contactRepository = get(),
            importProfileFieldsUseCase = get(),
        )
    }
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
    viewModel { top.mcxiafeng.badger.pages.settings.NotificationViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.DeviceViewModel() }
    viewModel { top.mcxiafeng.badger.pages.dashboard.DashboardViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.NfcSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.OperationHistoryViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.SettingsHomeViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.SyncStatusViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.TagManagerSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.PlatformListViewModel() }
    viewModel { top.mcxiafeng.badger.pages.social.SocialViewModel() }
    viewModel { top.mcxiafeng.badger.pages.setupguide.SetupGuideViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.ChangePasswordViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.ServerShortLinkViewModel() }
}
