# 待办工具栏、三级排序与多选

2026-11-27。待办顶栏使用同一胶囊中的搜索、新建、横向三个点，沿用 `GlassActionGroup` 的材质、主题色、48dp 触控目标、24dp 图标与整组按压反馈。

## 菜单与排序

一级菜单为“待办类型管理”“排序方式”“多选”。“排序方式”右侧使用锁定版 Miuix `DropdownImpl` 的 `hasSubmenu` 向右箭头。类型管理沿用原路由。排序方式从一级菜单对应行上沿叠放展开；二级标题只保留文字和右侧收起箭头，不再显示前置排序图标。

| 二级分类 | 三级方向 |
| --- | --- |
| 按创建日期 | 新到旧、旧到新 |
| 按修改日期 | 晚到早、早到晚 |
| 按待办日期 | 晚到早、早到晚 |
| 手动拖动 | 直接进入原手动排序草稿 |

前三个分类右侧同样有向右箭头。每级弹层保留原生 Popup 语义，沿用 Miuix 的测量与列表滚动，位置约束在安全区域；收起只关闭当前子级，选择最终方向后关闭全部菜单。

每层只对自身表面绘制直接子级所需的 **25% 黑色遮罩**。二级展开时，一级淡入至 25%；三级展开时，只给二级淡入至 25%，一级保持原有的 25%，不重复加深。所有遮罩使用独立的 **239ms** 单调非线性过渡，并在绘制阶段读取动画值。子菜单开始收起时，其直接父菜单的遮罩同步开始淡出，不等待 `onDismissFinished`。二级与三级分别管理自己的展开状态，关闭父级时后代并行退出。

排序以七个实际模式保存：原 `CREATED`、`UPDATED`、`SCHEDULED`、`MANUAL` 含义不变，新增创建升序、修改升序、待办日期降序。继续使用原 JSON 的 `mode` 和 `manualOrder`，不增加 Room 版本。旧字符串与 JSON 配置可继续读取；修改日期缺失时回退创建日期；未完成与已完成各组独立排序；无待办日期条目在两个方向均置底，同日继续比较钟点，手动顺序不因方向切换而丢失。

## 多选与删除

多选模式下，整行点击只切换选择，不进入编辑页或修改完成状态。顶部显示数量，通过取消或系统返回退出。选择至少一项后，底部出现“删除（数量）”，按钮沿用现有样式，使用 `LocalFloatingBarBottomPadding` 避让底栏，列表同步预留底部空间。

删除先确认不可恢复且关联名片不受影响。数据经 ViewModel 调用 Repository，在共享 Mutex 内重新读取并单次原子写入，同时同步提醒；提交期间阻止重复操作，失败保留选择以便重试。

## 本机验证方法

Gradle Wrapper 8.14.3，Oracle Java 21 由 `gradle/gradle-daemon-jvm.properties` 选择，依赖使用已有离线缓存。为保留当前模拟器并降低提交内存峰值，将 APK/测试编译与测试执行分阶段运行；仅通过本次命令调整内存，不改工程构建配置。

```powershell
.\gradlew.bat --offline --no-daemon --max-workers=1 --console=plain "-Dorg.gradle.jvmargs=-Xmx1024M -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:ReservedCodeCacheSize=128M -Dfile.encoding=UTF-8 -Djdk.attach.allowAttachSelf=true" "-Pkotlin.compiler.execution.strategy=in-process" -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:compileDebugUnitTestKotlin
```

测试运行 `:app:testDebugUnitTest`，筛选 `*Todo*Test`、`*GlassToolbarInteractionTest`、`*AnimatedListPopupTransitionTest`。覆盖六方向排序及持久化、旧记录兼容、三级叠放、方向文案和回调、父遮罩不叠加、退出期间的遮罩淡出、逐级关闭、原手动排序、类型管理、搜索、新建、多选和删除回归。

本轮验证已完成。`:app:assembleDebug` 与 `:app:testDebugUnitTest` 均返回 `BUILD SUCCESSFUL`。10 个测试类共 **67 项，0 失败、0 错误、0 跳过**：LocalTodoRepositoryTest 15、TodoDatePickerDialogTest 3、TodoEditorSaveTest 5、TodoMoreMenuTest 7、TodoPageInteractionTest 3、TodoRowInteractionTest 5、TodoSelectionToolbarTest 3、TodoViewModelTest 12、AnimatedListPopupTransitionTest 10、GlassToolbarInteractionTest 4。新增覆盖：日期滚轮弹窗保留原日期/非法回退当天/取消不改动、多选圆形删除按钮随选择数量切换标签并需确认、顶部圆形取消按钮退出当前模式。

期间本机提交内存被系统 DeviceAssociationService 泄漏（46.8GB）占满导致两次 JVM native malloc 失败；经用户确认重启该服务后恢复。全程不使用设备截图；用户提供的参考图可读取。元素树、日志和测试不能代替人工视觉验收。

排序分类文案统一为“按创建时间 / 按修改时间 / 按待办时间”。多选删除按钮改为圆形垃圾桶图标，沿用 `SaveIconButton` 的玻璃/实心几何与按压反馈，入场出场用 239ms 淡入淡出加缩放；右上角取消按钮同样改为圆形 X 图标。编辑器的日期与钟点选择均改为 `NumberPicker` 滚轮弹窗（日期三列年/月/日，钟点两列时/分，均带预览与取消/确定），样式对齐联系人详情生日选择；系统 `DatePickerDialog`、`TimePickerDialog` 不再使用。

多选模式下，取消按钮位于顶栏左侧（`navigationIcon` 槽），删除按钮位于顶栏右侧（`actions` 槽）。删除按钮使用与新建待办页保存按钮（`SaveIconButton`）完全相同的几何、玻璃填充不透明度、按压光晕与回弹参数：**始终可见**，未选中待办时为中性灰，选中后为错误红（`DeleteIconButton`，`ready` 即代表有选中项），灰红填充与图标色均以 **239ms** 平滑过渡；点击有选中项时弹出确认。进入/退出多选时整个顶栏行以 **239ms** 淡入淡出切换（`AnimatedContent`）。顶栏为二级页面同款居中标题栏（`TopAppBar`，title3 字号），显示“已选择 n 项”。

本轮把同一圆形删除按钮同步到其他界面的**独立危险操作**入口：联系人多选底部工具栏、收藏夹批量移除底部工具栏，均改用 `GlassButtonScope` + `DeleteIconButton`，与待办页参数完全一致（48dp、玻璃/实心随材质开关、50%/100% 填充、光晕与回弹、灰→红 239ms 过渡）。多动作工具栏（名片夹编辑/删除/分享、头像页保存原图/编辑/清空、联系人详情复制/同步/删除）保留原「图标+文字」动作样式，避免破坏组内统一布局。删除按钮的玻璃作用域问题也已修正：待办页是扁平按钮页，之前按钮落在页面作用域内导致实心 100%，现用 `GlassButtonScope` 显式提回玻璃模式，日志确认 `fillAlpha=0.5`。

`20261127-1813` 开发包（`app/build/outputs/apk/debug/`）：

| ABI | SHA256 |
| --- | --- |
| x86_64 | `61F8A722F048EA3EE18787E563926E4B2F56746575DEED227191D5129F91CC6A` |

API 36 模拟器（x86_64）已安装并验证：一级菜单含“排序方式”带向右箭头；二级“排序方式”标题无前置图标、四个分类（前三个带箭头）；三级“按创建日期 → 新到旧/旧到新”可选并立即生效（列表由新到旧变为旧到新）；日志确认 `CREATED_ASCENDING` 持久化与 239ms 遮罩过渡；进程 crash buffer 无记录。修改/待办日期的“晚到早/早到晚”已由 Robolectric 测试覆盖，未在模拟器逐项点验。

## 待办类型管理页（2026-11-27 续）

右上角改为与待办页同款的 `GlassActionGroup` 胶囊，共两项：左为原“新建类型”（行为不变），右为新“选择类型”。当没有自定义类型时选择按钮禁用，避免进入空选择态。进入选择后整行顶栏以 239ms 淡入淡出切换（与待办页 `AnimatedContent` 一致）：左上角圆形 X 取消，右上角 `DeleteIconButton`（48dp，玻璃/实心随材质，灰→红 239ms，`ready` 即已选中），标题“已选择 n 项”。列表中的自定义类型改为 `CheckboxPreference` 勾选，预设颜色行保持只读；系统返回键与取消按钮都先退出选择而非离开页面。删除前弹确认（“删除 n 项类型？”，说明受影响待办变为无类型），确认后在仓库共享 Mutex 内一次性校验全部快照并单次写入（`deleteTypes`），失败保留选择并显示冲突文案，成功后自动退出选择。**原有每行右侧删除按钮已移除**，行点击仍打开原编辑弹窗。

验证：`:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`、`:app:testDebugUnitTest --tests "*Todo*Test"`、`:app:assembleDebug` 全部 `BUILD SUCCESSFUL`，9 个测试类共 **66 项，0 失败**，其中新增 `TodoTypeManagerPageTest` 7 项、`LocalTodoRepositoryTest` 批量删除 1 项、`TodoViewModelTest` 类型删除 2 项。构建命令同上。

`20261127-2042` 开发包（`app/build/outputs/apk/debug/`，debug 签名校验通过）：

| ABI | SHA256 |
| --- | --- |
| x86_64 | `51C55FA40F95E4413926595B943152592E084BE99EAA63E0F85B65E8230296EA` |
| arm64-v8a | `3A04B5B82BF5E03083E90991E47E2B8D203C1E38A73B4D8DAF3D42A688B7749F` |
| armeabi-v7a | `8F8BF9F745D7FBE1631DBF1A6340A57AB6A7899C4011F03CBBE860292AA1215C` |

API 36 模拟器（x86_64）安装后按无障碍树逐项点验：待办类型页右上角为“新建类型 | 选择类型”双项胶囊；进入选择后顶栏标题“已选择 n 项”，左上角“取消”、右上角“删除n项类型”，勾选一行后计数与删除标签同步变化；“取消”与系统返回键均只退出选择、停留在本页；删除确认弹窗文案随数量在“该类型 / 这些类型”间切换，取消不删除；确认后类型从列表移除、自动退出选择、工具栏复原；原有每行删除按钮已不存在；行点击仍打开“编辑类型”弹窗；新建类型保存后立即出现在自定义分组。进程 crash buffer 无记录。未做人工目视（禁止截图），视觉效果待人工确认。

## 关联联系人改为整页选择（2026-11-27 续）

编辑/新建待办页的「添加联系人」不再展开内联搜索，改为 push 一个独立选择页 `TodoContactPickerPage`（路由 `Route.TodoContactPicker(token, selected)`，`glassButtons = false`）。页面沿用联系人 Tab 的列表能力：`PersonViewModel` 的分页 session（`personPagingSource`，FTS/标签分组搜索）、分页骨架、字母索引栏、`PersonContactItem` 行（选中圆圈 + 标签点），只是顶部换成待办编辑器同款按钮——左上角圆形取消（`TodoCancelButton`）、右上角 `SaveIconButton`（勾选图标、`contentDescription = "确定"`），标题「选择联系人」，下方一个搜索框。

选择状态保存在页面本地 `rememberSaveable` 列表里，不写进 `PersonViewModel.selection`：切换搜索词会重建分页 session，若复用 VM 选择态会被清空。整行点击只切换勾选，长按分享菜单在该模式下不出现（`PersonSelectionState(enabled = true)`）。

结果回传没有用 VM 共享状态，而是 `TodoContactPickResults`（进程内、按 token 的 `ConcurrentHashMap`）：确定 → `publish(token, picks)` → `pop()`；编辑器 `LifecycleEventEffect(ON_RESUME)` 调 `consume(token)` 写回草稿（单次消费，二次返回不会重复应用）。编辑器 `DisposableEffect` 在离开时 `discard(token)`，避免未确认的结果串到下一个编辑器。`PersonViewModel.enterSelection(initial)` 现支持预选集合。

同时移除了编辑器内联搜索的 `TodoContactPicker` 旧实现、`TodoViewModel.searchContacts/retryContactSearch/contactChoices` 与 `TodoContactChoices`（联系人选择改由 `PersonViewModel` 提供）。

验证：`:app:testDebugUnitTest` 相关 20 个测试类共 **129 项，0 失败**，其中新增 `TodoContactPickerPageTest` 4 项（预选保留、无改动确认、取消不回写、空态文案）、`TodoEditorPickerFlowTest` 3 项（打开时带上草稿预选、发布后在 ON_RESUME 写回且只消费一次、离开编辑器丢弃未确认结果）。

`20261127-2213` 开发包（`app/build/outputs/apk/debug/`）：

| ABI | SHA256 |
| --- | --- |
| x86_64 | `24DC95FEC52253CE87D388D9B3036555AC2137119E53E92A736B4143B1BCC25F` |
| arm64-v8a | `2C1ABB66A200AE334E8395C3CA33720E3BD1588E63074715C763F4C6D4F4B87A` |
| armeabi-v7a | `7A638B3BB4830E9E6984E002E5E2E0232A28E6FAEFE58E1A032000BD8CB885C3` |

API 36 模拟器（x86_64）按无障碍树点验：新建待办页点「添加联系人」进入「选择联系人」全屏页，左上角「取消」、右上角「确定」，下方搜索框、字母索引栏与联系人行；勾选两人后「确定」返回编辑器，关联联系人区出现两项（含标签与移除按钮），日志 `Published → Consumed → Applied contact picks; count=2`；再次进入该页两人保持勾选（`preselected=2`），取消一次勾选改动后返回草稿仍保留原两人；放弃草稿返回列表无异常。进程 crash buffer 无记录。未做人工目视（禁止截图），视觉效果待人工确认。

## 待办页副标题下缘对齐（2026-11-27 续）

`PageActionBar` 新增可选参数 `subtitleBottomAligned`：为 true 时标题行由居中改为底部对齐，副标题（“7条未完成”）的下缘与 title1 标题的下缘齐平；默认 false，保持联系人页等既有页面原有的居中配对不变。共享组件只加开关，不改变未传参页面的行为。

验证：`:app:testDebugUnitTest` 相关 13 个测试类共 **92 项，0 失败**，新增 `ScrollingPageChromeTest` 1 项（对齐模式底边齐平、默认模式仍居中）。Robolectric 会把两种字号压成同一高度，因此像素级差异以设备实测为准。

`:app:assembleDebug` 后在 API 36 模拟器（x86_64）读取无障碍树坐标：待办页标题 `[63,176][231,297]`、副标题 `[242,248][397,297]`，两者下缘均为 297（改前副标题垂直居中）；联系人页标题 `[63,176][315,297]`、副标题 `[326,212][531,261]`，中心线仍对齐（保留原样式）。进程 crash buffer 无记录。

完全贴底看起来偏低，副标题再上提 `SubtitleBottomLift = 2.dp`（负向 `offset`，不改布局高度）：设备实测标题 `[63,176][231,297]`、副标题 `[242,243][397,292]`，底边相差 5px；联系人页坐标不变。`ScrollingPageChromeTest` 改为断言“底边差在 1–3dp 之间”，仍校验默认模式中线对齐。

`20261127-2258` 开发包（`app/build/outputs/apk/debug/`）：

| ABI | SHA256 |
| --- | --- |
| x86_64 | `417B6B0E01C3E80101BE6DB3D3236BF185F01AD263488BD182A9BA2B076A1319` |
| arm64-v8a | `01E4B6C0B086509F4F47577F8F2B988F991D262632AE9D6E92B10C6F1DBFC915` |
| armeabi-v7a | `38B990C34E71195329529CA031C49B892C8BA3E8C4877D054D9EA9E1B2EC9ABC` |

## 待办标题截断规则按关联联系人分档（2026-11-27 续）

`todoUiState` 生成行标题时按是否有可解析的关联联系人选预算：没有关联联系人（行尾无头像/“…N 位”占位，文字可用整行宽度）→ `TODO_CARD_TITLE_LENGTH_BARE = 25`；有关联联系人 → 沿用原 `TODO_CARD_TITLE_LENGTH = 15`。截断仍走 `BreakIterator` 按字素切分，边界内不截断、不加省略号。判定依据是 `contactIds` 能匹配到的联系人（联系人被删后等同于无关联，与头像实际显示一致）。

验证：`TodoViewModelTest` 新增 2 项（25/15 分档与头像占位、边界整串不截断），相关套件 **BUILD SUCCESSFUL**。设备实测（`ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789` 36 字符）：无关联行显示 `ABCDEFGHIJKLMNOPQRSTUVWXY…`（25 字符 + 省略号），有关联行显示 `ABCDEFGHIJKLMNO…`（15 字符 + 省略号），同屏并存正确。测试待办已删除，crash buffer 无记录。

`20261127-2308` 开发包（`app/build/outputs/apk/debug/`）：

| ABI | SHA256 |
| --- | --- |
| x86_64 | `3AD1D3132246F6585CCEFA59FAD700267681DD25E887DF8EA9F0BD4B5CAF2D41` |
| arm64-v8a | `64C666F24B3704370137575A384C0DF8B9C157C5CAE0C5F2EFD0A4ECE769264E` |
| armeabi-v7a | `C7E1D4718C01E0A7994EA26013D777391A1F96C1E7826DE86A6D270EE4CA53C7` |

## 联系人页右上胶囊改为三项（2026-11-27 续）

联系人 Tab 右上角的 `GlassActionGroup` 由两项改为三项：搜索、**加号（新建联系人）**、更多。加号承接原溢出菜单里的“新建联系人”，一步直达 `CreateContact`；`PersonMoreMenu` 相应去掉 `onCreateContact` 参数，菜单现在只剩“多选”和可选的“从 QAuxiliary 导入 QQ 好友”。三项共享同一胶囊材质、48dp 触控目标与整组按压反馈，`contentDescription` 分别为“搜索联系人”“新建联系人”“更多”。

搜索页复用的 `PersonMoreMenu(onSelect = onSelect)` 因 `onImport` 默认 `null` 不受影响。

验证：`PersonSearchInputTest` 16 项全部通过，新增 `toolbarKeepsSearchCreateAndMoreAsThreeSiblingActions`（三者同屏，溢出菜单里不再出现“新建联系人”）；原“从工具栏新建联系人”用例改为直接点加号。

设备实测（API 36 模拟器，x86_64）按无障碍树点验：胶囊三项坐标 `搜索联系人 [692,205][755,268]`、`新建联系人 [818,205][881,268]`、`更多 [944,205][1007,268]`，同列同行即同一胶囊；点“更多”弹出的菜单只含“多选”与“从 QAuxiliary 导入 QQ 好友”；点加号进入“新建名片”页（标题、返回、保存名片、姓名/备注/介绍字段齐全），日志 `Open manual contact creation from the toolbar`；返回后胶囊三项复原；进程 crash buffer 无记录。未做人工目视（禁止截图），视觉效果待人工确认。

`20261127-2326` 开发包（`app/build/outputs/apk/debug/`）：

| ABI | SHA256 |
| --- | --- |
| x86_64 | `197057D133949AAA2772F385AE309AFEA82D460794A379D2313701312DFCF1F6` |
| arm64-v8a | `7E559FE257A1D4B2559C5C1A2264999BB964E71C56942B3A7E3466B11DA95376` |
| armeabi-v7a | `268BF7A1255CB66095221AF11DA42C84A13A5ECD169539C4BAA2FF747AAE3939` |

## 选择联系人页搜索框随列表滚动（2026-11-27 续）

`TodoContactPickerPage` 的搜索框从列表外的固定行移入 `LazyColumn`，成为列表第 0 项（`key = "picker_search"`，`SEARCH_HEADER_ITEMS = 1`），与联系人行同一内容层级、一起滚动。字母索引的 `fixedItemCount` 同步由 0 改为 1，跳转偏移保持正确。

空态保持原规则（居中、不可滚动）：列表无行时该项用 `fillParentMaxHeight()` 占满视口，搜索框在上、加载骨架 / 错误重试 / “还没联系人”或“未找到联系人”在剩余空间居中；此时底部 `contentPadding` 置 0，保证内容高度不超出视口、不能滚动。该项 `key` 在空/非空两态始终一致，输入导致结果从有到无时搜索框不会重建，焦点和键盘不丢。

验证：`TodoContactPickerPageTest` 4 项、`TodoEditorPickerFlowTest` 3 项全部通过；空态用例新增断言搜索框在有/无联系人时都保持显示。

设备实测（API 36 模拟器，x86_64）按无障碍树点验：进入「选择联系人」后搜索框位于列表内容顶部 `[105,376][545,440]`；上滑列表后搜索框随联系人一起滚出视口（节点消失，A 段标题由 524 上移到 333）；滑回顶部搜索框复原；点击并输入 `zzz` 后显示“未找到联系人”且搜索框仍在、输入未丢（日志 `empty=true searching=true`）；清空后恢复“共 8 位联系人”的字母分组（`empty=false`）；字母索引点击 T 后定位并受列表长度钳制（与联系人页同款行为）；进程 crash buffer 无记录。未做人工目视（禁止截图），视觉效果待人工确认。

`20261127-2351` 开发包（`app/build/outputs/apk/debug/`）：

| ABI | SHA256 |
| --- | --- |
| x86_64 | `7D28FB3766118DB37B91F8D5F2531C03B35A753DDC4DB197B3F67EAA79570A22` |
| arm64-v8a | `D16B56AC8971F8939E264F6497782BDEEE7B564C235772B32070CCC4C9C6F008` |
| armeabi-v7a | `DED181470C3AB930B614E011E70BEC7F428900E9A4B74AB48163E19805F9802C` |
