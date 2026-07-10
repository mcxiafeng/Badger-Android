# Badger - AI Agent 开发指南

## 项目概述

Badger 是一款 Android 电子名片夹应用，核心功能：扫码存联系人、管理数字名片、NFC 写入、WebDAV 云同步。全中文 UI，数据本地存储。

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| 语言 | Kotlin，Java 17 |
| UI | Jetpack Compose + Miuix（MIUI 风格组件库） |
| 架构 | MVVM + Use Cases（轻量 Clean Architecture） |
| DI | Hilt（KSP 注解处理） |
| 数据库 | Room v3（9 实体，含 FTS4 全文检索） |
| 图片 | Coil 3 |
| 分页 | Paging 3 |
| 相机/扫码 | CameraX + WeChatQRCode + ML Kit Chinese |
| 网络 | OkHttp |
| NFC | ReaderMode（非 ForegroundDispatch） |
| 导航 | 自定义栈式导航器（非 Jetpack Navigation） |

## 项目结构

```
app/src/main/kotlin/top/mcxiafeng/badger/
├── BadgerApplication.kt      # @HiltAndroidApp
├── MainActivity.kt            # @AndroidEntryPoint, edge-to-edge
├── App.kt                     # HorizontalPager(4 tabs) + AnimatedContent 路由栈
├── AppTheme.kt                # Miuix 主题
├── data/                      # 数据层
│   ├── Models.kt              # Room 实体定义
│   ├── Daos.kt                # 全部 DAO
│   ├── AppDatabase.kt         # @Database(version=3)
│   └── repository/            # Repository 接口 + Impl
├── di/                        # Hilt 模块
│   ├── DatabaseModule.kt      # Room @Provides
│   ├── DataModule.kt          # @Binds Repository
│   ├── NetworkModule.kt       # OkHttpClient
│   └── ImageModule.kt         # Coil ImageLoader
├── domain/                    # Use Cases（纯业务逻辑）
├── network/                   # 网络层
│   ├── adapter/               # 13 个 PlatformAdapter
│   └── ShortLinkService.kt   # short.io API
├── ocr/                       # OCR + AI 识别
├── pages/                     # UI 页面（按功能分包）
│   ├── card/                  # 名片夹
│   ├── person/                # 联系人列表（Paging 3）
│   ├── scanner/               # 扫码
│   ├── settings/              # 设置
│   ├── setupguide/            # 首次引导
│   └── social/                # 我的名片 + NFC
├── ui/                        # 通用 UI 组件
│   ├── LiquidGlassNavBar.kt   # 浮动导航栏
│   ├── components/            # 头像、对话框、空状态等
│   └── navigation/            # Route、AppNavigator、NavBarConfig
└── utils/                     # 工具类（拼音、颜色、HTTP 等）
```

## 架构模式

### MVVM + Use Cases
- **UI 层**：Compose Screen + ViewModel（`@HiltViewModel` + `@Inject`）
- **ViewModel** 暴露 `StateFlow<UiState>`，UiState 为 `@Immutable data class`
- **Domain 层**：Use Case 类，`operator fun invoke()`，纯业务逻辑
- **Data 层**：Repository 接口在 `data/repository/`，Impl 通过 Hilt `@Binds` 注入

### 架构边界（红线）
- **UI 层禁止直接访问 Repository**：所有数据操作必须通过 ViewModel，不得使用 `rememberXxxRepository()` 或 `EntryPointAccessors` 绕过 Hilt 注入
- **ViewModel 禁止暴露 Repository 给 UI**：Repository 必须为 `private`，UI 只能通过 ViewModel 的方法/StateFlow 获取数据
- **Composable 禁止包含业务逻辑**：数据库操作、网络请求、数据转换必须在 ViewModel/UseCase 中，不得在 `scope.launch(Dispatchers.IO)` 中执行
- **ViewModel 禁止持有 Activity 引用**：方法参数不得为 `Activity` 类型；需要 Activity 操作时通过接口/callback 回传 UI 层

### 导航
- `Route` sealed class 定义路由（MainTabs、Scanner、ContactDetail 等）
- `AppNavigator` 手动路由栈（`MutableStateFlow` + `mutableListOf`）
- `HorizontalPager` 实现 4 个主 Tab
- `AnimatedContent` + 自定义 `NavTransitionEasing`（弹簧阻尼）处理转场

### 平台适配器模式
新增社交平台需要：
1. `PlatformFields.kt` 添加 `PlatformFieldDef`
2. 创建 `PlatformAdapter` 实现
3. `PlatformAdapterRegistry` 注册
4. 添加图标 drawable

## 构建配置

- `compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`
- 三种构建类型：`debug`（无混淆）、`beta`（混淆）、`release`（混淆）
- ABI 分包：`arm64-v8a`、`armeabi-v7a`、`x86_64`
- KSP 处理 Room 和 Hilt 注解
- 签名从环境变量读取：`KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`

---

- LinkTemplate 必须是 HTTPS URL（不能是 `mqq://`、`weixin://`、`tg://` 等自定义 scheme）。iOS 后台 NFC 仅识别 HTTPS URI record，自定义 scheme 写入 NFC 后在 iOS 不会跳转
- AI OCR 仅做 DeepSeek 预设；云端备份不做服务商预设，用户自填 NAS/服务器地址

---

## 开发规范（必须遵守）

### 工作流程
1. **先看后做**：操作前先查看 `libdocs/`（Miuix 源码）和项目已有代码，了解已有组件
2. **理解后重写**：参考 libdocs 示例时理解其原理后用自己的方式重写实现，不要直接复制粘贴
3. **复用优先**：优先使用 Miuix 组件和 `Methods.kt` 等已有工具，不重复造轮子
4. **不动现有库**：不要修改项目已有的图标库、组件库等基础设施文件
5. **保持简洁**：严禁冗余代码；单 Composable 文件不超过 500 行（超 700 行必须拆分为 Page+Components+Dialogs）；单函数不超过 50 行；代码相同模式出现 2 次以上必须抽取通用函数
6. **日志调试**：关键逻辑加 `Log.d("当前类名Tester", ...)`，不凭猜测改代码

### 空状态 UI 规范
- 空列表页面显示简洁提示文字，居中显示，**禁止空列表可滚动**
- 模板："还没有XXX" + 主题色可点击的"点击添加"链接
- 不要使用灰色占位符、复杂的空状态插图设计

### 创建联系人规范
- 创建联系人应复用 ContactDetailPage 的添加字段/平台能力（create-then-edit 模式），不要新建独立的表单页面

### 数据库迁移规范
- **重构代码时不得升级 Room 数据库版本号**，确保现有用户数据兼容。仅在模型字段变更时才升级版本
- **模糊搜索必须使用 FTS4**：`LIKE '%' || :query || '%'` 阻止索引使用，大数据集全表扫描。搜索查询应走 FTS 表而非 LIKE fallback

### 日志规范
- 所有新增逻辑必须加 `Log.d("当前类名Tester", ...)` 日志
- 关键分支、状态变化、异常捕获都要记录
- **禁止 `e.printStackTrace()`**：release 构建中 stderr 输出丢失，必须用 `Log.e("当前类名Tester", "xxx失败", e)` 替代
- **Why:** 用户要求"只要是你做的，全部都加日志方便排查问题"

### 自检清单（功能完成后）
1. 点击流程是否正常（modifier 冲突可能导致点击失效）
2. 导航是否正常（页面跳转后导航栏/返回键行为）
3. 对话框 UI 是否与项目风格一致
4. 排版/文字是否正确

### 代码风格
- 禁止全限定类名调用，必须先 import 再用短名
- 改完代码不要自动 commit；完成完整功能后主动问用户是否 commit
- CI workflow 只负责构建 APK，不需要跑测试
- **魔法数字必须提取为命名常量**：防抖间隔、超时时间、HTTP 状态码等不得写裸数字，如 `SCAN_DEBOUNCE_MS = 500L`
- **错误处理禁止吞异常**：`catch (_: Exception)` 或 `catch` 返回 `null/false` 而不记录日志的行为禁止；至少 `Log.e` 记录，或用 sealed class 区分错误类型

### 版本发布
- 用户希望自动规划 Dev 和 Beta 版本的发布流程，并编写更新日志（changelog）
- 上传 artifact 时按 ABI 分割（arm64-v8a、armeabi-v7a、x86_64）

---

## Miuix 组件使用规范

### 点击/反馈
- Card 自带 `onClick`/`onLongPress` 参数，**不要在 modifier 上加 `combinedClickable`/`clickable`**，会冲突导致点击无响应
- 内部有可交互子组件时必须用无点击版 Card
- `LocalIndication` 全局注入 `MiuixIndication`（按压/悬浮/聚焦半透明覆盖层）
- `pressable` Modifier 整合 indication，内置 150ms 防误触延迟
- `SinkFeedback`（按压缩小）、`TiltFeedback`（3D 倾斜）可选反馈

### ArrowPreference
- **不可点击的纯信息展示行不得用 ArrowPreference**（箭头暗示可点击）
- 纯信息展示（版本号、构建日期等）用 `BasicComponent`
- 需要导航/弹窗/操作的行才用 ArrowPreference
- ArrowPreference 不可嵌套 combinedClickable

### 文字样式
- 优先用 `MiuixTheme.textStyles` 替代硬编码 `fontSize`+`fontWeight`
- 层级：title1(32sp) > title2(24sp) > title3(20sp) > title4(18sp) > subtitle(14sp Bold) > body1(16sp) > body2(14sp) > footnote1(13sp) > footnote2(11sp)

### 卡片间距
- 纵向间距不低于 12.dp，横向不低于 8.dp
- 优先用 `Arrangement.spacedBy(X.dp)` 或容器级 `padding`

### 弹窗/下拉
- **WindowDialog 必须用 Pattern A**：`if (showXxx) { WindowDialog(show = true, ...) }`，然后通过外层 `if` 控制挂载/卸载
- **不得用 Pattern B**：`WindowDialog(show = showXxx, ...)` 会导致首帧不渲染
- `DialogLayout` 需手动套 `Box(fillMaxSize, contentAlignment = Center)` 居中
- `OverlayListPopup` 需放在 Scaffold 内部；菜单项禁用 `BasicComponent`，改用 `Box+clickable+Text`
- WindowDialog 内部无 Scaffold，需用 `WindowDropdownPreference` 替代 `OverlayDropdownPreference`（否则弹窗找不到 MiuixPopupHost 挂载点）

### 其他
- 禁用 `MiuixIcons`，统一使用 Material Icons
- `Color.Transparent` 的 RGB 是 (0,0,0)，`copy(alpha)` 渲染为半透明黑色。需要半透明主题色用 `MiuixTheme.colorScheme.surfaceContainer.copy(alpha=...)`
- `MiuixTextButton` 没有 content lambda，需要加载动画时改用 `Button`
- `MiuixCircularProgressIndicator` 没有 color 参数，用 `colors: ProgressIndicatorColors`
- `ToggleableState` 包路径：`androidx.compose.ui.state.ToggleableState`
- Miuix 0.9.0 已自带 `ColorPalette`（HSV 色格）和 `ColorPicker`（色相环+滑块），无需升级版本即可使用

---

## 对话框规范

### 按钮
- 最多 2 个按钮（取消+确认），3 个太丑
- 单个按钮必须 `Modifier.fillMaxWidth()` 顶满宽度

### flag 重置
- 所有 `showXxxDialog` 状态必须在三条路径都重置为 `false`：`onDismissRequest`、取消按钮、确认按钮
- 选项型对话框每个选项的 `onClick` 也必须重置 flag

### 选项型对话框
- 用 `BasicComponent` 列表 + `WindowDialog`，不需要 `DialogButtonRow`
- 点选项直接执行，点外部关闭

### 拍照处理中 Dialog
- `isProcessingPhoto` 时 Dialog `onDismissRequest` 置空 + `BackHandler` 拦截，防止误触关闭丢失拍照结果

---

## 布局规范

### 不得重叠
- 所有 UI 组件之间绝对不得出现视觉重叠
- 浮动组件必须考虑与其他组件的偏移
- 使用 `LocalFloatingBarBottomPadding` 等 CompositionLocal 传递高度信息

### FloatingToolbar 适配
- 主 Tab 页面的 `floatingToolbar` 槽必须用 `Box(modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current))` 包裹
- 二级页面不受影响

---

## Android/Compose 关键模式

### BackHandler
- 所有多选/编辑/特殊模式页面必须添加 `BackHandler(enabled = isInSpecialMode) { exitSpecialMode() }`
- 适用：多选模式、编辑模式、搜索展开状态、拍照处理中 Dialog

### CameraPreview
- 用 `rememberUpdatedState(isScanningPaused)` 缓存扫描暂停状态，不要作为 `LaunchedEffect` key
- `cameraProviderFuture`（lambda）作为 key 不稳定，Dialog 重组会导致相机重绑定

### NFC
- **必须用 ReaderMode**，不要用 ForegroundDispatch（Android 12+ BAL 限制会清空 Tag 数据）
- 写入成功后延迟 3s 再 `disableReaderMode`

### API 陷阱
- short.io 更新链接用 POST（非 PATCH）
- Gson 解析 JSON 数字默认为 Double：`code: 0` → `0.0`
- B 站 API 返回结构是 `data.card.face`（非 `data.face`）；CDN 头像需 `Referer` 头
- `LaunchedEffect` 中 `Flow.collect` 是无限挂起，会阻塞后续调用；拆分为独立 `LaunchedEffect`

### Bitmap 内存管理
- **不能手动 recycle 展示中的 Bitmap**（被 `asImageBitmap()` 引用的 bitmap），否则 Compose Image 可能渲染已回收的 bitmap，导致 `Canvas: trying to use a recycled bitmap` crash
- 只有临时 bitmap（缩放中间产物、保存前的编码 bitmap）才 recycle，且必须在 `try/finally` 中回收，确保异常路径不泄漏
- SocialPage、AvatarComponents 等项目已有模式均不 recycle 展示中的 bitmap，依赖 GC 回收
- **状态变量中的 Bitmap 旧引用必须在赋值前回收**：`val old = state.value; state.value = newBitmap; old?.recycle()`，避免无主 Bitmap 等待 GC
- **ML Kit TextRecognizer 必须复用实例**：不得在每帧创建新 `TextRecognition.getClient()`，模型加载开销 50-100ms，应在页面级 `remember` 创建一次

### Color ↔ ARGB Long 转换
- Compose `Color` → Long 存入数据库时统一用无符号格式：`color.toArgb().toLong() and 0xFFFFFFFFL`
- Long → Color：`Color(longValue)` 两种格式都正确
- 平台品牌色（如 `0xFF12B7F5L`）是正数 Long，`Palette.Swatch.rgb` 是负数 Long，统一后避免同一颜色存为不同值

### 通用陷阱
- `hashCode() % array.size` 可能返回负数索引 → 加 `abs()`
- 协程线程：Toast 需在主线程，IO 操作切 `Dispatchers.IO`
- WindowDialog 切换（两个交替显示）会导致闪烁 → 在同一个内根据状态切换内容
- 拼音排序：Han-Latin Transliterator 返回带声调拼音，需 `Normalizer.NFD` 去声调
- `HttpUtil.downloadBitmap` 有额外 headers 时不使用内存缓存（避免缓存污染）
- `aspectRatio` + `padding` 交互陷阱：`fillMaxWidth().aspectRatio(1f).padding(horizontal=16.dp)` 会使内部容器变矩形。应将 padding 移到外层 Box，aspectRatio 保留在内层

### CameraX 资源清理
- 所有 CameraX 资源（flashlight、camera、analyzer）离开页面时必须通过 `DisposableEffect` 清理
- 手电筒在离开扫描页后不关是已知 bug，需 `DisposableEffect(Unit) { onDispose { camera?.cameraControl?.enableTorch(false) } }`
- **`Tasks.await()` 禁止阻塞 CameraX analyzer 线程**：ML Kit 文字识别必须用 `suspendCancellableCoroutine` 包装异步 API，不得同步阻塞帧处理

### Compose 状态管理
- **单 Composable 函数 `mutableStateOf` 不超过 10 个**：超 10 个必须拆分子组件，每个子组件只管理自己相关的状态
- **无关状态必须隔离到独立 Composable**：相机预览、扫描结果、对话框等独立 UI 区域各自成子组件，避免一个状态变化触发整页重组
- **Regex、Color 转换等计算必须在 `remember` 中**：不得在 Composable body 中每帧重新创建
- **`UiState` 必须标注 `@Immutable`**：确保 Compose 重组优化生效

---

## 协程与数据一致性

### Mutex 保护
- Repository 中的 read-modify-write 操作必须用 Mutex 保护：`contactMutex`、`userProfileMutex`、`collectionMutex`
- 多个协程同时修改同一数据时可能竞态导致覆盖

### 写前重读（防 stale snapshot）
- 更新 Contact/UserProfile/CardCollection 前，必须从 DB 重新读取最新数据，不能直接使用 UI state 中的快照
- 模式：`val latest = repository.getXxxById(id); repository.updateXxx(latest.copy(...))`

### 对话框状态管理
- 编辑对话框需要独立的状态变量（`showEditPlatformDialog` + `editingPlatform`），不要和新增对话框共用 `showAddPlatformDialog`
- 编辑初始化需要用一次性 flag（`editInitialized`），不能用 `mainInput.isEmpty()` 条件判断（用户清空输入会触发错误重填）
- `remember { mutableStateMapOf(...) }` 需要传入 key（如 `remember(currentCollectionIds)`），否则切换页面时不会重置

---

## AI OCR API 陷阱

- ModelScope API 默认 SSE 流式响应，`HttpUtil` 无法解析。必须在请求体明确 `"stream": false`
- AI OCR 文字模式的超时时间必须和视觉模式一致（60s），否则慢模型（Qwen3-235B）会超时
- Gson `getAsJsonArray("choices")` 遇到 `"choices": null`（JsonNull）会抛 ClassCastException。必须先检查 `isJsonNull`

---

## 安全已知问题

- AI OCR API key 和 short.io API key 存储在明文 SharedPreferences（`ai_ocr_config.xml`、`short_link_settings`）。只有 WebDAV 凭据使用了 EncryptedSharedPreferences
- `network_security_config.xml` 中 `cleartextTrafficPermitted=true` 在 `<base-config>` 中，对所有构建类型生效（包括 release）
- CloudSyncManager 备份 SharedPreferences 到 WebDAV 时 API key 以明文 JSON 上传
- `allowBackup=true` 可通过 ADB 导出完整数据库和 SharedPreferences
- ProGuard 规则缺少 Gson TypeToken、OkHttp、Hilt 的反射 keep 规则，release 构建可能崩溃

## Android 系统兼容性

- Android 16 "减弱模糊效果" 无障碍设置会静默禁用跨窗口模糊。NavBarConfig 必须在 API 31+ 检查 `WindowManager.isCrossWindowBlurEnabled`，不能只检查 SDK 版本
- 模糊/液态玻璃效果可能导致 LazyColumn 无法滚动到底部（PersonPage、设置页、CardPage）

---

## 已知 UI 问题

- 扫码添加的 QR 码在浅色模式下与卡片背景色有明显色差（环形边框），需在渲染时消除背景色差异
- `MiuixTextButton` 没有 `color` 参数，使用 `ButtonDefaults.textButtonColorsPrimary()` 获取主题色；默认是 secondaryVariant（中性色）
- `MiuixTheme.colorScheme.*` 属性只能在 `@Composable` 作用域内调用，不能在 `remember {}` 块内直接访问。需要时先 `val color = MiuixTheme.colorScheme.xxx` 再到 `remember` 中使用
- Miuix 的 colorScheme 使用 `onSurfaceVariantSummary` / `onSurfaceVariantActions`，不是 Material3 的 `onSurfaceVariant`
- `CollectionTheme.kt` 中 fallback 文字颜色 `Color(0xFF1C1B1FL)` 在暗色背景下不可见，需用主题感知颜色

---

## 构建环境

- KSP/Dagger 跨盘符构建可能失败（Gradle 缓存在 C: 但项目在 F:）。错误信息："this and base files have different roots"

---

## 已知性能问题

### AnimatedContent 转场 jank
- `App.kt` 使用 `tween(500)` + `NavTransitionEasing(0.8f, 0.95f)`，弹簧过冲振荡 500ms，同时保持新旧 composable 在 composition tree 中。应改为 300ms

### HttpUtil Bitmap 缓存无上限
- `ConcurrentHashMap<String, Bitmap>` 无 eviction/size limit，`clearBitmapCache()` 从未自动调用。长期运行导致 OOM。应用 LruCache 或 WeakReference

### AppNavigator 竞态
- `synchronizedList.isNotEmpty()` + `removeAt()` 不原子。并发 `popBackStack()` → IndexOutOfBoundsException。需 synchronized 块包裹 check+remove

### material-icons-extended 膨胀
- 引入 2000+ 图标但只用 4 个（QrCodeScanner、Person、CreditCard、Settings），增加约 2MB class loading 开销

### 扫码预处理 GC 压力
- `QrCodeUtils.detectQrCodesFromBitmap()` 每帧创建 6+ 预处理变体 x 9 网格区域 x 2x 缩放，大量临时 bitmap 导致 GC

### WebDavClient 错误吞噬
- 所有方法 catch 通用 Exception 返回 null/false。调用方无法区分 "未找到" vs "超时" vs "认证失败"

### e.printStackTrace() 在 release 中丢失
- `ContactNetworkResolver` 等多处使用 `e.printStackTrace()` 写 stderr，release 构建不捕获。应用 `Log.e()`

### 无 Baseline Profile
- 无 `BaselineProfile` 或 `Macrobenchmark` 模块。冷启动和首次导航无 AOT 预编译路径

---

## 代码质量红线（新增代码必须检查）

| 红线 | 说明                                                                  |
|------|---------------------------------------------------------------------|
| UI 层直接访问 Repository | 必须通过 ViewModel，不得 `rememberXxxRepository()` 或 `EntryPointAccessors` |
| ViewModel 暴露 Repository 为 public | Repository 必须 `private`，UI 只看 ViewModel 的公开接口                       |
| Composable 中 `scope.launch(IO)` 执行 DB/网络 | 业务逻辑必须在 ViewModel/UseCase，Composable 只调 ViewModel 方法                |
| `e.printStackTrace()` | 必须用 `Log.e("当前类名Tester", msg, e)`                                         |
| `catch` 返回 null/false 不记录日志 | 至少 `Log.e`，或用 sealed class 区分错误                                     |
| 裸数字（500L、2000L） | 必须提取为命名常量                                                           |
| 单文件超 700 行 | 必须拆分为 Page+Components+Dialogs                                       |
| 单函数超 50 行 | 必须拆分或提取子函数                                                          |
| 相同代码模式出现 3 次 | 必须抽取通用函数                                                            |
| `Tasks.await()` 阻塞 CameraX 线程 | 必须用协程包装异步 API                                                       |
| 临时 Bitmap 未在 finally 中回收 | `try/finally` 包裹，确保异常路径不泄漏                                          |
| ML Kit 每帧创建新实例 | 必须页面级 `remember` 复用                                                 |
| `LIKE '%' |                                                                     | query || '%'` 搜索 | 必须走 FTS4 全文检索 |
| ViewModel 方法接收 Activity 参数 | 改用接口/callback 回传 UI 层操作                                             |
| Application.onCreate 同步初始化重库 | OpenCV/WeChatQRCode 等必须懒加载                                          |

---

## 禁止截图

用户明确禁止使用截图/截屏工具读取设备 UI。使用以下替代方案：
- `mobile_list_elements_on_screen` — 获取 UI 元素文字列表
- `adb shell dumpsys` — 获取系统状态
- `adb shell logcat` — 读取日志
- accessibility tree dump — 获取无障碍树

永远不要使用 `mobile_take_screenshot` 或 `mobile_save_screenshot`。

---

## 测试

- 框架：Robolectric + MockK + Truth + Turbine + Coroutines Test
- 自定义 `InMemoryDatabaseRule` 创建 Room 内存数据库
- 测试文件在 `app/src/test/`，共 26 个

## 重要参考文件

| 文件 | 用途 |
|------|------|
| `app/build.gradle.kts` | 构建配置、依赖、签名 |
| `gradle/libs.versions.toml` | 版本目录 |
| `data/Models.kt` | 所有 Room 实体定义 |
| `data/Daos.kt` | 所有 DAO |
| `di/DatabaseModule.kt` | 数据库初始化 + 默认数据 |
| `ocr/PlatformFields.kt` | 平台字段定义注册表 |
| `network/adapter/PlatformAdapterRegistry.kt` | 平台适配器注册 |
| `ui/navigation/Route.kt` | 路由定义 |
| `ui/navigation/AppNavigator.kt` | 导航器实现 |
| `ui/navigation/NavBarConfig.kt` | 导航栏配置 |
| `App.kt` | 主入口、Tab + 路由组合 |
| `libdocs/` | Miuix 框架源码和示例 |