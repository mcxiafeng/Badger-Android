# Badger 业务迁移清单（Android ↔ Badger-Server）

> 扫描日期：2026-07-23
> 范围：`F:\Java\Android Project\Badger`（39,436 行 Kotlin / 184 文件） 与 `F:\GolandProjects\Badger-Server`（10,970 行 Go / 45 文件 + 2,735 行 SPA）
> 既有契约：`Badger-Server/API_SPEC.md`、`Badger-Server/AGENT.md`、Android 端 `.agent-memory/memory/project_api_spec_gaps.md`
> 用户既定范围：客户端**不接** `/api/admin/*` 与 `/api/setup/*`（服务端自治）；本文件不再讨论这两块。

---

## 0. 总览

| 维度 | Android | Badger-Server |
| --- | --- | --- |
| 业务核心（联系人 / 名片 / OCR / 备份 / 鉴权 / 解析器） | 仅做 UI + Room + 设备能力 + 兼容 shim | 几乎全部业务实现 |
| 上游密钥（AI / 高德 / short.io） | 仅缓存只读 prefs | 持有 + 代理转发 |
| 平台识别（GitHub/B站/QQ/...） | UI 标签 | 唯一权威 |
| NFC 写标签 / 相机扫码 | 必 Android | 无 |

---

## 1. 仅能在 Android 端做的业务（**禁止迁移**）

| 业务 | 关键文件 | 锁定原因 |
| --- | --- | --- |
| **NFC ReaderMode 写标签** | `pages/social/NfcHelper.kt`、`pages/social/NfcWriteDialog.kt`、`pages/social/SocialPage.kt`、`pages/social/NfcActivityHandler.kt` | 需要 `NfcAdapter.ReaderCallback` 物理硬件；Android 12+ BAL 限制必须用 ReaderMode（参见 `feedback_android_patterns.md`） |
| **CameraX 取流 + WeChatQRCodeDetector** | `pages/scanner/ScannerCamera.kt`、`pages/scanner/QrCodeUtils.kt`、`pages/scanner/QrCoordinateMapper.kt`、`pages/scanner/QrImagePreprocessor.kt`、`pages/scanner/BoundingBoxSmoother.kt` | CameraX + ML Kit + WeChatQRCodeDetector 是 Android 原生 SDK；预览/框选/拍照 UI 必须本地 |
| **Room 本地数据库（含 FTS4 / PagingSource / 拼音索引）** | `data/AppDatabase.kt`、`data/Daos.kt`、`data/Models.kt`、`data/CollectionExporter.kt`、`data/QAuxvFriendImporter.kt`、`LegacyTagFixup.kt` | 离线可用 + PagingSource invalidation + FTS4 migration 强绑定 Room（参见 `feedback_room_paging_invalidation.md`、`feedback_room_fts_migration.md`） |
| **ICU 拼音首字母** | `utils/PinyinUtils.kt`（被 12 处仓库/DAO 调用） | `android.icu.text.Transliterator` + `Normalizer.NFD` 只能在 Android；服务端纯文本不带 ICU 等价物 |
| **Palette 主题色提取** | `utils/ColorExtractor.kt`（依赖 `androidx.palette.graphics.Palette`） | 需 `Bitmap`，Android-only |
| **GPU 渲染 / LiquidGlass 模糊** | `ui/blur/*`（BlurHelper、CombinedBackdrop、InnerShadow、Lens、SphereSurface、Vibrancy、GpuCompat） | GPU shader + RenderEffect；无跨平台等价 |
| **Android 平台 UI 框架** | `ui/navigation/AppNavigator.kt`、`ui/navigation/Route.kt`、`ui/LiquidGlassNavBar.kt`、`ui/components/*` | Miuix Compose 控件 + Miuix 主题（参见 `feedback_miuix_rules.md`） |
| **系统设置跳转 / NFC 设置 / 权限申请** | `pages/settings/NfcSettingsPage.kt`、`pages/settings/NfcSettingsViewModel.kt`、`pages/social/SocialPage.kt` | `Settings.ACTION_NFC_SETTINGS` + `ActivityResultContracts.RequestPermission` |
| **本地图片选择 / 裁剪 / WEBP 压缩** | `ui/components/ImageCropDialog.kt`、`pages/social/SocialPage.kt`（拍照） | PhotoPicker / 图像解码在 Android |
| **本地通知 / Toast / BackHandler** | 散落各 `*Page.kt` | 必须 Android |

---

## 2. 已迁到 Go 的业务（**Android 仅保留兼容 shim**）

| 业务 | Go 侧入口 | Android 端 shim | 状态 |
| --- | --- | --- | --- |
| **用户注册 / 登录 / 刷新 / 登出 / me** | `/api/auth/*`（`httpapi/user_auth.go`、`httpapi/auth_shared.go`、`auth/jwt.go`、`auth/password.go`） | `network/ServerApi.kt` + `data/repository/UserAuthRepository.kt` + `pages/auth/AuthScreens.kt` + `pages/setupguide/SetupStepAccount.kt` | ✅ 已迁 |
| **JWT + 401 自动 refresh** | `auth/middleware.go` + `auth/jwt.go` | `di/NetworkModule.kt` 内 `tokenRefreshInterceptor`（拦截器规则见 `feedback_android_patterns.md`） | ✅ 已迁 |
| **AI 标签推荐** | `POST /v1/proxy/ai/tasks/tag_generate`（`httpapi/handler_ai_tasks.go` + `internal/prompts/prompts.go`） | `ai/AiTagGenerator.kt` → `ServerApi.tagGenerate` | ✅ 已迁 |
| **AI 名片 OCR** | `POST /v1/proxy/ai/tasks/contact_ocr`（vision + 文本双通道） | `ocr/AiOcrService.kt` → `ServerApi.contactOcr` | ✅ 已迁 |
| **平台识别 + 头像 + 签名抓取** | `POST /v1/resolver`（`internal/resolver/resolver.go`、`httpapi/resolver.go`） | `network/ContactNetworkResolver.kt` → `ServerApi.resolveIdentify` | ✅ 已迁 |
| **按平台细分解析**（`github/{login}`、`bili/{uid}`、`qq/{qq}`、`twitter/{handle}`、`telegram/{path}`） | 同 `/v1/resolver`（单端点覆盖全部，旧 GET 路由已删除，参见 `API_SPEC.md §13 迁移提示`） | `ServerApi.resolveGitHub/.../resolveTelegram` 在 `ServerApi.kt:336-353` —— **死代码**，旧端点已删除 | ⚠️ 死代码 |
| **`/v1/resolver/qq-avatar/{qq}` 与 `/v1/resolver/favicon`** | 已并入 `POST /v1/resolver` 的 `avatar_url` 字段 | `ServerApi.resolveQqAvatar` / `resolveFavicon`（`ServerApi.kt:355-373`） —— **死代码** | ⚠️ 死代码 |
| **QQ / short.io API key 管理** | `/v1/proxy/shortio/*`（`httpapi/proxy_shortio.go` + `internal/proxy/shortio/`），key 仅服务端持有 | `network/ShortLinkService.kt` → `ServerApi.shortioList/Update`；UI 还在 `pages/settings/NfcSettingsPage.kt` | ✅ key 已迁；UI 流程仍是 Android |
| **Amap（district/geo/regeo）API key** | `/v1/proxy/amap/*`（`httpapi/proxy_amap.go` + `internal/proxy/amap/amap.go`） | **未接** —— Android `WorldRegionRepository.kt` 仍直连 `raw.githubusercontent.com/dr5hn/countries-states-cities-database` | ⚠️ 密钥迁了、客户端没切（见 §5 P2-⑦） |
| **AI provider key 管理** | `ai_providers` 表 + `api_keys` 表（`db/migrations/0001_init.sql`）+ `internal/secretbox/`（AES-256-GCM） | 无客户端入口（`/api/admin/keys/*` 在 §5 占位 501） | ✅ 客户端不动 |
| **云备份（upload / list / download）** | `POST/GET /v1/backups`（`httpapi/backup.go` + `repo/backups.go` + `internal/backup/{schema,sanitize}.go`） | `pages/settings/CloudSyncSettingsViewModel.kt` → `ServerApi.uploadBackup/listBackups/downloadBackup` | ✅ 已迁 |
| **Setup Wizard（首次启动 site→admin→appearance→complete）** | `/api/setup/*`（`httpapi/setup.go` + `setup_guard.go`） | **客户端零接入**（用户已决定） | ✅ 服务端自治 |
| **Admin 控制台（用户/Key/Settings/Dashboard）** | `/api/admin/*`（`httpapi/admin_users.go` + `admin_auth.go` + `admin_settings.go` + `dashboard.go`） + SPA `web/src/pages/*` | **客户端零接入**（用户已决定） | ✅ 服务端自治 |

---

## 3. 迁了一半的业务（**混合 / 双侧重复**）

| 业务 | Android 端保留了什么 | Go 端做了什么的等价物 | 重复 / 矛盾点 |
| --- | --- | --- | --- |
| **联系人全量 CRUD + 多字段 + 标签 + 名片夹 + 重复检测 + 合并** | `data/repository/ContactRepositoryImpl.kt`、`data/repository/FieldRepositoryImpl.kt`、`data/repository/TagRepositoryImpl.kt`、`data/repository/CollectionRepositoryImpl.kt`、`domain/{DuplicateDetectionUseCase,MergeContactUseCase,FilterContactsUseCase,ParseQrCodeUseCase,SaveScannedContactUseCase,PrepareNfcWriteUseCase,SelectPlatformUseCase}.kt`、`pages/person/contact/*` | **无**（服务端没有联系人模型，`backups` 只存信封） | Android 完全自治；备份只负责快照往返，不做协作 / 多端同步 |
| **UserProfile（我的名片）** | `data/repository/UserProfileRepositoryImpl.kt`、`pages/person/contact/UserProfileDetailPage.kt`、`pages/social/SocialPage.kt`、`pages/social/QrCodeCard.kt` | **无** | 仅本地；服务端没有 `/v1/profile/*` |
| **Settings（界面/NFC/标签/服务器等）** | `pages/settings/*`（22 个文件）+ `data/OnboardingPrefs.kt` + `data/DeveloperModePref.kt` + `data/ShortLinkPrefs.kt` + `data/CloudSyncConfig.kt` + `data/AuthPrefs.kt` + `ocr/AiOcrConfig.kt` | `/api/admin/settings` 服务端有同名 key/value 存储（`repo/settings.go`、`httpapi/admin_settings.go`） | **同名字段双侧各持一份**：服务端 `settings` 表管全局；客户端 prefs 管单机。**双方不互通**——这是有意为之（用户隔离），但要小心别混淆 |
| **平台 Adapter 注册表（UI 配色 / kind 词表）** | `network/PlatformAdapterRegistry.kt`（14 个 `ContactType` + `kindToContactType` + `SYNCABLE_KINDS`） | `internal/resolver/resolver.go` 第 88-103 行（`PlatformGitHub` 等 13 个常量） | **双侧词表必须保持字面量一致** —— `resolver.go:43` 显式注释「This matches the Android client (PlatformAdapterRegistry.kt:71-75)」。**这是隐式契约**，任一侧改名都会失配 |
| **`PlatformAdapterRegistry` 的同步判定** | `PlatformAdapterRegistry.kindCanSync`（基于 `SYNCABLE_KINDS = {github,bilibili,qq,x,telegram}`） | 服务端 `/v1/resolver` 返回 `status` 字段（`StatusOK/Partial/Fallback/Error`） | UI 的「可重新抓数据」按钮依赖客户端的 `SYNCABLE_KINDS`；服务端已不区分平台，全在一个端点 |
| **`LinkResolver`（短链跳转 + 平台正则）** | `network/LinkResolver.kt` —— **空壳**，注释直接说「UI 应该走 `POST /v1/resolver/link`」但该端点**不存在** | 服务端 `/s/{slug}`（`httpapi/router.go:166`）—— **501 桩** | UI 早就不调 Android 的 `LinkResolver.resolve()`，返回原 URL 兜底；Go 端 `/s/{slug}` 待落地 |
| **`/v1/proxy/ai/legacy/*`（chat / models / router）** | **未接** | `httpapi/proxy_ai.go` + `handler_ai_tasks.go` 实现完整 | 旧 Android WebClient 时代的端点，客户端完全没碰；保留只为兼容性 |
| **ContactType ⇄ kind 的映射** | `PlatformAdapterRegistry.kindToContactType` —— 14 个分支 | 服务端只发 `kind` 字符串，不感知 UI 标签 | UI 标签色板是 Android 专属，但映射表改了会拖累 `kind` |

---

## 4. Android 与 Go 重复实现的业务（**两套都在跑**）

| 业务 | Android 实现 | Go 实现 | 后果 |
| --- | --- | --- | --- |
| **`/v1/resolver` 老 GET 端点（github/{login} 等 7 个）** | `ServerApi.resolveGitHub/.../resolveTelegram/resolveQqAvatar/resolveFavicon`（`ServerApi.kt:336-373`） | **已删除**，统一到 `POST /v1/resolver`（参见 `API_SPEC.md §13 与旧 API 的差异`） | **Android 死代码**，调过去必然 404 |
| **平台正则解析（detect() 函数）** | 已被 `ContactNetworkResolver.identify` 替代，只剩 shim | `internal/resolver/resolver.go::detect()` + 7 个 `extractXxx` | 旧 `adapter/*.kt` 已删（参见 `resolver.go:43` 注释），Android 端只剩 UI 标签映射 |
| **PlatformAdapterRegistry**（UI 配色 + 14 个 ContactType） | `network/PlatformAdapterRegistry.kt`（108 行） | `internal/resolver/resolver.go` 的 `PlatformXxx` 常量 | 同 §3 的「双侧词表」—— 严格说是「服务端定语义、客户端定视觉」，但**字段命名是隐式契约**，需在 PR 时双侧同步 |
| **备份信封 schema** | `data/CollectionExporter.kt`（导出 JSON 信封）+ `data/importFromJson`（导入） | `internal/backup/schema.go::Envelope`（`Version=1`, `App="badger"`） + `internal/backup/sanitize.go`（剥 api_key） | 双方契约稳定（version=1 字段固定）；服务端强制剥离 `apiKey/apikey/api_key` |
| **JWT refresh 失败时的降级** | `NetworkModule.tokenRefreshInterceptor` —— 失败直接抛 401，不递归 `chain.proceed`（`feedback_android_patterns.md` 详记） | 服务端 `auth/middleware.go::RequireAuth` 返回 401 | 双侧各自防御 |
| **OkHttp 同步调用必须切 IO 线程** | Android `Repository` 包 `withContext(Dispatchers.IO)`（`UserAuthRepository.kt:91-93` 等） | Go 用 `chi` 异步 + 协程天然 OK | 触发条件不同；Android 是 `NetworkOnMainThreadException` 兜底 |

---

## 5. 仍需迁移 / 待动工 ticket

### 5.1 P1（必修，分叉 / 死代码 / 撒谎契约）

| # | 任务 | 文件 | 说明 |
| --- | --- | --- | --- |
| ① | `ShortLinkService.api(context)` 切到 `ServerApiFactory.get()` | `network/ShortLinkService.kt:43-47` | 自建 `OkHttpClient()` 绕开 401 自动 refresh + baseUrl 热更；与全 app 的 ServerApi 不一致 |
| ② | `ShortLinkService.fetchDomains/createShortIoLink` 对应 `ServerApi` 新方法（`POST /v1/proxy/shortio/domains` + `links create`） | `network/ShortLinkService.kt:146-205`、`network/ServerApi.kt` | 当前 `fetchDomains` 构造 payload 但**没传出去**；`createShortIoLink` 走 update path 假装 create |
| ③ | `ContactNetworkResolver.api(context)` 切到 Factory；接入 `resolveQqAvatar` / `resolveFavicon`（或者删掉） | `network/ContactNetworkResolver.kt:29-33`、`network/ServerApi.kt:355-373` | 旧端点已删除，`resolveQqAvatar/favicon` 是死代码；要么删要么走 `POST /v1/resolver` |
| ④ | `ServerApi` 加 `DELETE /v1/backups/{id}` + `CloudSyncSettingsViewModel` 提供删除入口 | `network/ServerApi.kt:339-383`、`pages/settings/CloudSyncSettingsViewModel.kt` | spec §14 有 DELETE，客户端无入口 |

### 5.2 P2（创可贴，按用户规则要修）

| # | 任务 | 文件 | 说明 |
| --- | --- | --- | --- |
| ⑤ | 客户端备份 envelope 4 MiB 边界校验（按 spec §0.2 / §14） | `pages/settings/CloudSyncSettingsViewModel.kt::backup` | 超限要早失败，不要等服务端 413 |
| ⑥ | `AiOcrService.testApi/fetchModels` 永远 success / 空列表 → UI「测试连接」绿灯不反映真实健康 | `ocr/AiOcrService.kt:43-53` | 创可贴红线（用户规则） |
| ⑦ | `WorldRegionRepository` 决策：保留 + 缓存隔离，或切 `/v1/proxy/amap/district` | `data/repository/WorldRegionRepository.kt`、`network/ServerApi.kt` 加 amap 三件套 | **密钥已迁、客户端没切**——最可能的下一步合并点 |
| ⑧ | 删除 `ServerApi.resolveGitHub/Bili/Qq/Twitter/Telegram/QqAvatar/Favicon` 七个死方法 + 在 ContactDetailViewModel 把对它们的调用换成 `ContactNetworkResolver.identify` | `network/ServerApi.kt:336-373`、`pages/person/contact/ContactDetailViewModel.kt` 等调用点 | spec §13 已删老 GET 路由 |

### 5.3 不做的事（防回滚）

- **不要**新增 `/api/admin/*` 客户端入口（用户决定）
- **不要**新增 `/api/setup/*` 客户端入口（用户决定）
- **不要**在 `ShortLinkService` 或 `ContactNetworkResolver` 里再 `new OkHttpClient()` —— 必须走 `ServerApiFactory.get()`
- **不要**让 `AiOcrService.testApi/fetchModels` 继续返回 `Result.success` 占位（创可贴红线）
- **不要**把 `PlatformAdapterRegistry` 的 `kind` 字符串与服务端 `PlatformXxx` 常量解耦 —— 它们是隐式契约，改一处必改两侧

---

## 6. 关键文件索引

### Android 端（已迁业务的 shim 入口）

```
app/src/main/kotlin/top/mcxiafeng/badger/
├─ network/
│  ├─ ServerApi.kt                       # 所有 /v1/* 与 /api/auth/* 的 OkHttp 客户端
│  ├─ ContactNetworkResolver.kt          # /v1/resolver 包装
│  ├─ PlatformAdapterRegistry.kt         # ContactType + kind 词表（与服务端 PlatformXxx 同字面量）
│  ├─ LinkResolver.kt                    # 空壳（已迁，留兼容）
│  └─ ShortLinkService.kt                # short.io 包装（key 在服务端）
├─ data/repository/
│  ├─ UserAuthRepository.kt              # 注册/登录/me/logout
│  ├─ ServerUrlHolder.kt                 # 服务地址 StateFlow
│  ├─ ServerApiFactory.kt                # ServerApi 单例 + baseUrl 热更
│  ├─ ContactRepositoryImpl.kt           # 纯本地 Room（未迁）
│  ├─ CollectionRepositoryImpl.kt        # 纯本地 Room（未迁）
│  ├─ UserProfileRepositoryImpl.kt       # 纯本地（未迁）
│  └─ WorldRegionRepository.kt           # ⚠️ 仍直连 GitHub raw + jsDelivr（待决策）
├─ ai/AiTagGenerator.kt                  # /v1/proxy/ai/tasks/tag_generate
├─ ocr/AiOcrService.kt                   # /v1/proxy/ai/tasks/contact_ocr
├─ pages/auth/                           # 登录注册 UI
├─ pages/setupguide/SetupStepAccount.kt  # 引导内的登录注册
├─ pages/settings/
│  ├─ ServerSettingsPage.kt              # 改服务端 URL
│  ├─ AccountSettingsViewModel.kt        # 账号/登录态
│  └─ CloudSyncSettingsViewModel.kt      # /v1/backups
└─ di/NetworkModule.kt                   # TokenHolder + 401 refresh + OkHttp
```

### Go 端

```
Badger-Server/
├─ cmd/server/                           # 入口
├─ internal/
│  ├─ httpapi/
│  │  ├─ router.go                       # chi 总路由
│  │  ├─ user_auth.go / auth_shared.go   # /api/auth/*
│  │  ├─ admin_*.go / setup.go / dashboard.go   # 服务端自治
│  │  ├─ resolver.go                     # POST /v1/resolver
│  │  ├─ proxy_amap.go / proxy_ai.go / proxy_shortio.go   # /v1/proxy/*
│  │  └─ backup.go                       # /v1/backups
│  ├─ resolver/resolver.go               # 平台识别 + 上游抓取
│  ├─ proxy/{ai,amap,shortio}/           # 各上游代理
│  ├─ auth/{jwt,password,middleware}.go  # argon2id + JWT + 角色
│  ├─ repo/{users,backups,settings}.go   # sqlx CRUD
│  ├─ backup/{schema,sanitize}.go        # 信封 schema + api_key 剥离
│  ├─ secretbox/secretbox.go             # AES-256-GCM
│  ├─ prompts/prompts.go                 # tag_generate / contact_ocr 系统+用户 prompt
│  └─ webui/                             # 嵌入 SPA
├─ web/src/                              # React + Vite
│  ├─ pages/{Dashboard,Login,Users,Wizard,Settings/*}.tsx
│  └─ api/client.ts                      # 前端 fetch 客户端
├─ API_SPEC.md                           # 权威契约
└─ AGENT.md                              # 架构总览
```

---

## 7. 统计

| 维度 | Android | Go + SPA |
| --- | --- | --- |
| 业务实现覆盖（按端点数） | 仅 auth/me、AI 标签/OCR、resolver 单端点、短链 list/update、备份上传/列表/下载 | 13 大类、~50 个端点（含 admin/setup） |
| 持有上游 API key | 无（仅缓存 prefs） | Amap / AI provider / short.io（`api_keys` + `ai_providers` 表 + AES-256-GCM） |
| 本地数据存储 | Room（9 张表 + FTS4 + 拼音索引 + PagingSource） | SQLite（8 张表：users/auth_revocations/ai_providers/api_keys/backups/short_links/request_log/settings） |
| 离线可用 | 完全（Room + 相机 + NFC） | 无 |
| 多端同步 | 仅备份快照（upload+download） | 用户级隔离，无冲突解决 |
| 设备能力 | 相机、麦克风（未启）、NFC ReaderMode、PhotoPicker、Palette、GPU shader | 无 |
| 兼容 shim 数量（Android） | 4：`LinkResolver`、`ContactNetworkResolver`（部分）、`ShortLinkService`（仍走自建 OkHttp）、`AiOcrConfig`（空函数） | — |

---

## 8. 一句话总结

- **能迁的几乎都迁了**：auth + AI + resolver + 备份 + 短链 key
- **迁不了的全在 Android**：NFC + 相机 + Room + 拼音 + Palette + GPU
- **没迁干净的**：Amap（密钥已迁、客户端 `WorldRegionRepository` 没切）、7 个 `/v1/resolver/<kind>` 死方法、`ShortLinkService` 自建 OkHttp 分叉
- **服务端自治**：admin / setup 完全不接客户端
- **最大隐患**：`PlatformAdapterRegistry` 的 `kind` 字符串与服务端 `PlatformXxx` 常量的**隐式契约** —— 改名需双侧同步