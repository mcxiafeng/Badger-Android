# Badger 包结构重组方案

## 问题分析

当前 `app/src/main/kotlin/top/mcxiafeng/badger/` 有 **200 个 .kt 文件**分布在 **31 个包**中，主要问题：

| 包 | 文件数 | 问题 |
|---|---|---|
| 顶层 `badger/` | 9 | AppShell、DI、Legacy 混放 |
| `data/` root | 10 | Prefs/Models/DB/Utils/Importer 混放 |
| `pages/person/contact/` | **32** | 三个独立功能域挤在同一个扁平包 |
| `pages/settings/` | **39** | 10+ 个不相关的设置页挤在同一个扁平包 |
| `data/Models.kt` | 1 | 14 个 data class 挤在一个文件（226 行） |

## 重组策略

**原则**：只移动文件 + 改 package 声明 + 改 import。不改任何逻辑。每批移动后编译验证。

### 第一批：顶层 + DI（5 个文件移动）

| 文件 | 从 | 到 | 原因 |
|---|---|---|---|
| `NetworkModule.kt` | `badger/` | `di/` | 本质是 DI wiring，与 `KoinModules.kt` 同类 |
| `LegacyTagFixup.kt` | `badger/` | `data/` | 一次性数据修复，属于数据层 |

顶层保留 `App.kt`、`AppMainTabs.kt`、`AppRoutes.kt`、`AppTheme.kt`、`AppViewModel.kt`、`BadgerApplication.kt`、`MainActivity.kt`（共 7 个，都是 App Shell）。

### 第二批：`data/` 分组（6 个文件移动）

| 文件 | 从 | 到 |
|---|---|---|
| `AuthPrefs.kt` | `data/` | `data/prefs/` |
| `DeveloperModePref.kt` | `data/` | `data/prefs/` |
| `OnboardingPrefs.kt` | `data/` | `data/prefs/` |
| `ShortLinkPrefs.kt` | `data/` | `data/prefs/` |
| `CollectionExporter.kt` | `data/` | `data/importer/`（仅测试引用） |
| `QAuxvFriendImporter.kt` | `data/` | `data/importer/` |

`data/` root 保留：`AppDatabase.kt`、`AvatarStorage.kt`、`CollectionUtils.kt`、`Models.kt`（4 个）。

### 第三批：`pages/settings/` 拆分（30 个文件移动）

这是最大的重组，将 39 个文件按功能域分成 7 个子包：

**`pages/settings/account/`（6 文件）**
- `AccountProfilePage.kt`、`AccountSettingsDialogs.kt`、`AccountSettingsViewModel.kt`
- `ChangePasswordPage.kt`、`ChangePasswordViewModel.kt`
- `PlatformListPage.kt`

**`pages/settings/sync/`（5 文件）**
- `SyncStatusPage.kt`、`SyncStatusViewModel.kt`、`SyncStatusEvent.kt`、`SyncStatusUiState.kt`
- `ServerShortLinkPage.kt`、`ServerShortLinkViewModel.kt`

**`pages/settings/tags/`（7 文件）**
- `TagManagerSettingsPage.kt`、`TagManagerSettingsViewModel.kt`、`TagManagerComponents.kt`、`TagManagerSuccessBody.kt`
- `TagManagerEvent.kt`、`TagManagerUiState.kt`、`TagFormatting.kt`

**`pages/settings/notification/`（2 文件）**
- `NotificationPage.kt`、`NotificationViewModel.kt`

**`pages/settings/devices/`（2 文件）**
- `DeviceListPage.kt`、`DeviceViewModel.kt`

**`pages/settings/history/`（5 文件）**
- `OperationHistoryPage.kt`、`OperationHistoryViewModel.kt`
- `OperationHistoryEvent.kt`、`OperationHistoryUiState.kt`、`OperationHistoryOpFormatter.kt`

**`pages/settings/` 保留（9 文件）**
- `SettingsPage.kt`、`SettingsSubPage.kt`、`SettingsHomeViewModel.kt`、`SettingsComponents.kt`
- `NfcSettingsPage.kt`、`NfcSettingsViewModel.kt`
- `UiSettingsPage.kt`
- `AboutPage.kt`、`OpenSourceLicensePage.kt`
- `ContactUsPage.kt`、`LogViewerPage.kt`

### 第四批：`pages/person/contact/` 拆分（15 个文件移动）

将 32 个文件按功能域分成 3 个子包：

**`pages/person/contact/detail/`（12 文件）** — ContactDetail 功能域
- `ContactDetailPage.kt`、`ContactDetailViewModel.kt`、`ContactDetailDialogHost.kt`
- `ContactDetailComponents.kt`、`ContactDetailDialogs.kt`、`ContactDetailAvatar.kt`、`ContactDetailUtils.kt`
- `ContactFieldComponents.kt`、`BioEditDialog.kt`、`SyncOptionsSheet.kt`
- `TagChip.kt`、`TagPickerDialog.kt`、`TagQuickManageDialog.kt`、`AiTagPreviewDialog.kt`

**`pages/person/contact/dialogs/`（10 文件）** — 共享对话框
- `AddContactFieldDialog.kt`、`AddPlatformDialog.kt`、`AddPlatformComponents.kt`
- `AttachFieldDialog.kt`、`BasicInfoDialogs.kt`、`BatchImportPlatformsDialog.kt`
- `CollectionPickerDialog.kt`、`ContactPickerDialog.kt`
- `FieldDetailDialog.kt`、`PlatformDetailDialog.kt`、`PlatformGridSelector.kt`
- `ImportFromPlatformDialog.kt`、`RegionPickerDialog.kt`

**`pages/person/contact/` 保留（7 文件）**
- `CreateContactPage.kt`、`CreateContactViewModel.kt`
- `UserProfileDetailPage.kt`、`UserProfileDetailViewModel.kt`、`UserProfileDetailComponents.kt`

### 第五批：`Models.kt` 拆分（可选，影响 30+ 文件）

`data/Models.kt`（226 行，14 个 data class）拆为：

| 新文件 | 内容 | 外部引用数 |
|---|---|---|
| `data/model/PlatformEntry.kt` | `PlatformEntry` | 25 文件 |
| `data/model/ContactModels.kt` | `PersonWithFields`、`PersonFieldDisplay`、`LetterCount`、`DuplicateCheckResult` | 15 文件 |
| `data/model/CollectionModels.kt` | `CardCollectionWithCount` | 9 文件 |
| `data/model/FieldModels.kt` | `ContactField`、`ContactFieldValue`、`CustomField`、`FieldMergeEntry`、`MergeChoice` | 10 文件 |
| `data/model/QAuxvModels.kt` | `QAuxvFriendEntry`、`QAuxvConflictAction`、`QAuxvImportSummary`、`QAuxvImportProgress` | 5 文件 |

**影响**：30+ 文件的 import 语句需要更新。这是机械替换，但范围广。建议最后做。

## 实施顺序

```
第一批 → 编译 → 第二批 → 编译 → 第三批 → 编译 → 第四批 → 编译 → 第五批 → 编译 → 全量测试
```

每批是独立可编译的。每批完成后验证 `compileDebugKotlin`。

## 风险

| 风险 | 缓解 |
|---|---|
| 同包内隐式引用断裂 | 每批编译验证，修复遗漏的 import |
| Koin 注册路径变化 | `KoinModules.kt` 中的 FQN 引用需同步更新 |
| 测试中的 import | 测试文件同批更新 |
| 第五批影响面广 | 可选，建议单独 commit |

## 重组后的结构（预览）

```
badger/
├── App.kt / AppMainTabs.kt / AppRoutes.kt / AppTheme.kt / AppViewModel.kt
├── BadgerApplication.kt / MainActivity.kt
├── ai/
├── data/
│   ├── AppDatabase.kt / AvatarStorage.kt / CollectionUtils.kt / Models.kt
│   ├── cache/ (dao/ + entity/ — 不变)
│   ├── migrations/
│   ├── model/ (第五批)
│   ├── prefs/ (AuthPrefs / DeveloperModePref / OnboardingPrefs / ShortLinkPrefs)
│   ├── importer/ (CollectionExporter / QAuxvFriendImporter)
│   ├── queue/
│   └── repository/ (不变)
├── di/ (KoinModules + KoinComponentBy + NetworkModule)
├── domain/ (不变)
├── network/ (不变)
├── ocr/ (不变)
├── pages/
│   ├── auth/
│   ├── card/
│   ├── dashboard/
│   ├── person/
│   │   ├── PersonPage / PersonListComponents / PersonViewModel
│   │   └── contact/
│   │       ├── CreateContact* / UserProfileDetail*
│   │       ├── detail/ (ContactDetail* + Tag* + SyncOptions + Bio + FieldComponents)
│   │       └── dialogs/ (Add* / Attach* / Basic* / Batch* / Collection* / Contact* / Field* / Platform* / Import* / Region*)
│   ├── scanner/
│   ├── settings/
│   │   ├── SettingsPage / SettingsSubPage / SettingsHomeViewModel / SettingsComponents
│   │   ├── NfcSettings* / UiSettings* / About* / OpenSourceLicense* / ContactUs* / LogViewer*
│   │   ├── account/ (Account* / ChangePassword* / PlatformList)
│   │   ├── sync/ (SyncStatus* / ServerShortLink*)
│   │   ├── tags/ (TagManager* / TagFormatting)
│   │   ├── notification/ (Notification*)
│   │   ├── devices/ (Device*)
│   │   └── history/ (OperationHistory*)
│   ├── setupguide/
│   └── social/
├── sync/ (不变)
├── ui/ (不变)
└── utils/ (不变)
```
