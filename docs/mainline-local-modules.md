# 主线本地功能迁移

2026-11-24。目标分支：`feature/local-modules-mainline`。

## 基线与保留

远端没有 `main`，`git ls-remote --symref origin HEAD` 确认默认分支为 `master`。新分支从 `origin/master` 的 `1030d7e5f727c4cbba14c545d6cd087ed453c291` 创建。

原 `design/figma-local-ui` 及其全部未提交内容保存在相邻工作树 `../badger-ui-migration-source`，另保留名为 `preserve design figma local ui before selective mainline migration 2026-11-24` 的 stash 快照。迁移不自动提交或推送。

## 迁移范围

- 首页、名片夹、待办、我的四个主标签。保留 Pager 动画、底栏滑块与拖动、Miuix 页面转场；悬浮与经典底栏都支持四栏。
- 名片夹标签保留主线分页、独立搜索、多选、平台二维码分享和同步刷新，增加横向名片夹入口，继续复用既有管理和详情页面。
- 完整新建名片草稿，在保存前编辑资料、平台、标签与归属，保存完成后替换为详情路由。
- 本地待办的创建、编辑、关联名片、完成与重新打开、删除、持久化。
- 全局外观模式与强调色偏好，沿用主线的中性界面和既有功能入口。

不迁入原分支的整套首页、名片详情视觉改写与本地备份模块。现有账号、同步、搜索和分享以主线为准。Room 版本和已有实体模型保持兼容。

## 构建环境

使用工程 Gradle Wrapper 8.14.3，JVM target 21，`compileSdk = 37` / minor 0。守护进程 JVM criteria 要求 Oracle Java 21。移除工程中开发机的固定 JDK 路径和过时 Build Tools 34 约束，让 AGP 使用已安装的兼容版本。

工程 `libdocs/` 当前不存在，组件 API 以主线现有实现和 Gradle 缓存的锁定版 Miuix 源码为准。验证仅使用本机已有依赖和工具；如需使用本机的已校验依赖仓库，可以通过外部 init script 指定，不将开发机路径写入工程依赖配置。

## 验证结果

最终 `:app:assembleDebug :app:testDebugUnitTest` 返回 `BUILD SUCCESSFUL`。13 个选定测试类共 **88 项，0 失败、0 错误、0 跳过**，以 `app/build/test-results/testDebugUnitTest/TEST-*.xml` 为准。

| 测试类 | 项数 |
| --- | ---: |
| AppearancePreferencesTest | 5 |
| LocalTodoRepositoryTest | 8 |
| TodoViewModelTest | 3 |
| CreateContactPersistenceTest | 11 |
| PersonSearchInputTest | 11 |
| FloatingNavBarTest | 7 |
| MiuixNavDisplaySmokeTest | 3 |
| MyPageTest | 5 |
| SocialCardInteractionTest | 5 |
| CardViewModelTest | 5 |
| PersonViewModelTest | 12 |
| ContactDetailInteractionTest | 5 |
| AddPlatformScreenTest | 8 |

覆盖草稿事务回滚、重复保存、账号保护、待办并发和编辑冲突、联系人失效关联、外观持久化，以及主线搜索、分享、导航和详情交互。它是与本次改动相关的回归集合，不是仓库全部测试。

普通构建命令为：

```powershell
.\gradlew.bat --offline --console=plain "-Pandroid.injected.testOnly=false" :app:assembleDebug
```

测试运行 `:app:testDebugUnitTest`，使用上表类名对应的 `--tests "*类名"` 筛选。当前开发机额外使用工程外的 `local-maven.init.gradle` 读取已校验的本地依赖；项目未加入该机器路径。

切换分支后的首个增量产物存在生成基类缺失和旧的 IDE 安装限制，不能交付。已执行 `:app:clean` 并关闭构建缓存完整重建，最终 APK 内确认存在 `Hilt_BadgerApplication` 和 `Hilt_MainActivity`。最终打包不传 `android.injected.build.abi`，该 IDE 参数会选择第一个适配 ABI，并不能用于常规三架构分包。

## APK

位置：`app/build/outputs/apk/debug/`。三个 APK 均为 `top.mcxiafeng.badger.debug` / `1.0.0-dev`，ABI 与文件名一致，`apksigner verify --verbose` 通过，v2 签名有效，未设置 `testOnly`，模拟器包可直接普通安装。

| 文件 | SHA256 |
| --- | --- |
| Badger-1.0.0-dev-3-arm64-v8a-20261124-0356.apk | `a26cad54d4ac5639130a3dd7e99fd14aab4c2488750024c8839b73ad4ed5be3b` |
| Badger-1.0.0-dev-3-armeabi-v7a-20261124-0356.apk | `e1177ef5a49273a66f4f092231feecfe373b127e6b795eaa10b8ee3626329c6e` |
| Badger-1.0.0-dev-3-x86_64-20261124-0356.apk | `1327d64e0d665d2f6472811b285bd981de1495951929eb80a558ef6b08f701b2` |

## Android 16 模拟器检查

设备 `Badger API 36` / x86_64，1080 × 2400、420dpi、字号 1.0。使用无障碍元素、`dumpsys` 和 `logcat`；没有使用截图或录屏。

- 普通安装最终 x86_64 APK 成功，`MainActivity` 处于 `RESUMED`。
- 四个主标签均可打开，名片夹显示横向夹入口与主线联系人列表，原有测试联系人和待办保留。
- 新建 `MigrationCard11`，填写备注并通过复用字段弹窗添加电话。返回提示放弃，选择继续填写后草稿保留；保存后进入详情，电话和备注可见；返回直接到名片夹，新记录可见。
- 新建 `MigrationTodo1124`，填写说明和地点、选择 2026-11-25、关联 `MigrationCard11`。联系人弹窗可搜索，键盘弹起时列表和确认按钮均可操作；保存后摘要包含日期、地点和关联人。
- 待办完成后强制停止应用再重启，完成状态、日期、地点和关联保留；重新打开、编辑地点并保存正常。最后通过确认删除流程清理本次验证待办，原 `FollowUpTest` 保留。
- 深浅模式改为深色、强调色改为森林绿，选项立即关闭；重启后偏好保留。日志确认全局 `AppTheme` 使用 `Dark/Forest`，系统栏图标同步 `dark=true`。模拟器当前保留此主题和验证名片。
- 最终重启后的应用进程崩溃日志为空。

以上不是视觉设计验收。真机相机、实际扫码和 NFC 硬件未在本次模拟器检查中验证。新建名片文本草稿可恢复；头像沿用源行为，仅在当前 ViewModel 内存中保留，进程结束后需要重新选择。

## 待办编辑扩展

待办日期可细化到分钟。提醒时间按本地日历计算，支持当天或提前任意天数，并可单独选钟点。设置过期时编辑器会显示状态。通知使用通用文案以保护任务内容，精确闹钟权限未开启时系统可延迟送达。待办列表标题前的标记随优先级变化，低优先级不加标记，中、高优先级分别加 `！`、`！！`。

类型管理可从待办顶栏和编辑器打开。七种预设为红、橙、黄、绿、青、蓝、紫。自定义类型可修改名称和颜色，删除后使用此类型的待办显示为无类型。裁剪框和待办列表的卡片共用设计稿 `1080:230` 的宽高比，对应 Figma 待办页节点 `37:536`。裁剪图在保存前留在本机缓存，保存后写入对应空间的私有目录。

```powershell
.\gradlew.bat --offline --console=plain :app:assembleDebug :app:testDebugUnitTest --tests '*Todo*' --tests '*MiuixNavDisplaySmokeTest' --tests '*SetupStepProfileCropTest'
```

本机配置同上，Gradle 8.14.3 使用 Java 21，通过工程外的 `local-maven.init.gradle` 使用已缓存依赖。构建成功，测试 XML 共 23 项通过（待办持久化 11 项、待办 ViewModel 3 项、导航 3 项、头像裁剪回归 6 项）。

本轮 `1.0.0-dev` APK 均通过 v2 验签，应用 ID 为 `top.mcxiafeng.badger.debug`，文件名中的 ABI 与包内原生库一致。文件在 `app/build/outputs/apk/debug/`。

| 文件 | SHA-256 |
| --- | --- |
| `Badger-1.0.0-dev-3-arm64-v8a-20261124-1915.apk` | `a831e58e240fea0bb015801319224c1f8f9d014ef74be8e4a0886cf6502c9125` |
| `Badger-1.0.0-dev-3-armeabi-v7a-20261124-1915.apk` | `0cdaca7bcb884084a58240e7f83672c6ddc818dec461d25e42698c28a83048b4` |
| `Badger-1.0.0-dev-3-x86_64-20261124-1915.apk` | `d21ed3960a3cd16e6a0a5c11eb632fab22b488a0e9161d1b8a2a77dd0b735545` |

Android 16 模拟器覆盖安装成功。已通过无障碍树和日志核对旧待办可读、管理类型后返回保留编辑草稿、设置两天前 09:00、高优先级的列表标记、取消裁剪保留原状态、拖动并保存 1080×230 裁剪图，以及重启后重新打开待办显示已保存字段。

在编辑页授予通知和精确闹钟权限后，系统将测试待办的提前两天提醒登记为 2026-11-24 19:32。应用退到后台后，接收器于 19:32:00 发出通知，通知栏显示「待办提醒」，点击可回到 Badger。验证使用无障碍树、`dumpsys` 和 `logcat`。

### 待办卡片与详情验收版

列表把类型色应用到未完成圆环和完成勾选。标题最多保留 15 个字符，后接最多三个头像，额外关联人用省略号表示。详情在标题后放置备注，直接列出全部已关联联系人、头像和标签。添加联系人在同页搜索，关联修改随待办保存。

背景以卡片宽度的 75% 为图像中心。「填充」覆盖右半区，「适应」在右半区完整显示图像。左侧叠加渐变过渡的高斯模糊层与随实际应用主题切换的黑白遮罩。两种模式沿用 `1080:230` 裁剪图，编辑预览与列表共用同一渲染。

沿用上面的 Gradle 命令，构建成功。共 24 项测试通过，新增覆盖标题字符边界、前三个联系人及超出数量，并扩展背景存储测试以核对显示方式。Android 16 模拟器覆盖安装成功，通过无障碍树核对三头像及省略号、四位联系人完整展开、标签、备注顺序、完成与重新打开流程。对已有图像确认了高斯模糊处理日志，并验证「适应」保存后重开仍选中。图像的视觉效果留待模拟器目视验收。

| 已验签 APK | SHA-256 |
| --- | --- |
| ARM 64 位（`20261124-2011`） | `eb2a11e6c6aef9e8b50c5b511669843374f8caefcf5fa1c71e4f07a38691f121` |
| `Badger-1.0.0-dev-3-armeabi-v7a-20261124-2011.apk` | `f59bc5235b138287f11da5ea5cff0888848baaf9169fbbe0e0e1bb8bfb5aea43` |
| `Badger-1.0.0-dev-3-x86_64-20261124-2011.apk` | `23af115f70f0ab31a07c7d4dccceb118daa50a57bff3baa07f156848328940be` |

### 待办排序

待办顶栏的排序入口位于新建按钮右侧。按创建日期、修改日期时最新在前，旧记录缺少修改日期时使用创建日期。按待办日期时从早到晚，同一天细分到钟点，未设日期排在后面。未完成与已完成各组分别应用所选排序。

手动模式可通过卡片右端的把手拖动，也可使用读屏的上移、下移操作。拖动只改变同组条目的位置。应用将选中的排序方式和手动顺序保存在当前账户空间。条目编辑保留其手动位置，切换排序方式后可以恢复原手动顺序。

沿用前面的 Gradle 命令，构建成功，26 项回归测试通过。排序测试覆盖两组各自应用四种模式、缺少修改时间的旧记录、未设置待办日期的条目，以及手动顺序在编辑和切换模式后保留。Android 16 模拟器覆盖安装后验证了四种菜单选项、两组内拖动、拖过分组边界仍留在原组，以及重启后恢复手动顺序。验收模拟器中已完成组保留 `SortDemoA` 和 `SortDemoB` 两条测试待办。

三种架构的 `20261124-2048` 开发版 APK 均通过 v2 验签，应用 ID 为 `top.mcxiafeng.badger.debug`。文件位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `aba3c820fad55944f022b19d8cdeafddb515baef00b8e5413edd09ebd82428c9` |
| armeabi-v7a | `ea52447ca04cdc59f4d95d4081dde58dcecc2342001c693cfc7bf89629b35b09` |
| x86_64 | `7ca80cbe2c2390bf2e35418fb0b34bf280a5365a5fb0e5ec2275c3352e344c27` |

### 待办背景与手动排序确认

卡片右四分点（距左端 75%）保持清晰，向左逐渐加大高斯模糊半径。遮罩沿用深浅主题对应的黑白色，距左端 50% 处透明，向左渐变到 25% 处完全不透明。联系人头像与卡片垂直中心对齐。

从排序菜单选择「手动拖动」会打开排序草稿并显示把手，拖动只预览同组的新顺序。点击右下角「完成」后，应用把手动模式与新顺序一起写入当前账户的排序配置，隐藏把手。取消或返回会放弃草稿。旧版保存的手动顺序可直接继续使用。

沿用待办构建命令，27 项回归测试通过。Android 16 模拟器覆盖安装后，核对了头像中心与卡片中心对齐、渐进高斯处理日志、拖动和取消期间排序文件保持原样，以及点击「完成」后排序文件更新、待办内容文件不变。重启后恢复排序并隐藏把手。背景的颜色和模糊观感留待目视验收。

`20261124-2112` 开发版 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`。三种 ABI 均通过 v2 验签，文件在 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `302268abef69d350815a1400c622f9b0667388a884002f5f5415a7c3daf8e7c6` |
| armeabi-v7a | `bba4f888cb56867f5c3a8ed3573c95711319f2f24965b8e471327d28adae5f2f` |
| x86_64 | `910fe3a2b24d93e08bd30f02842494a2fa24dc9ffeb301cf97408973203f8414` |

### 待办背景压暗

待办卡片和详情预览在图片及渐进模糊层上叠加 19% 不透明度的黑色遮罩，再绘制随主题变化的渐变遮罩。沿用待办构建命令，27 项回归测试通过。Android 16 模拟器覆盖安装后已打开待办列表，渐进模糊层正常加载。

`20261124-2125` 开发版 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`。三种 ABI 均通过 v2 验签，文件在 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `06e27a989073e89f28eabd1f1333bff2a22ef34842056f00c8dabb0f0724ee16` |
| armeabi-v7a | `45d1648e7d6c8a2ed5f0a3dc1091cd85571e728fbd33f0369f9ef6f453498b60` |
| x86_64 | `b057bb32f75fea17c5caf246e02c246cf0c54695c0ffdaeee9ecc1b730259167` |

### 待办背景主题遮罩

均匀遮罩改为浅色主题用白色、深色主题用黑色，叠加比例为 25%。列表卡片与详情预览共用这层遮罩。沿用待办构建命令，27 项回归测试通过。Android 16 模拟器已切换浅色与深色并打开待办列表，日志未出现背景加载错误。

`20261124-2141` 开发版 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`。三种 ABI 均通过 v2 验签，文件在 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `e07da0364576db17f88d25f27804b047ef168492f37fcfe5b29276a3c278325b` |
| armeabi-v7a | `4c9f8ceaaa339624fa392972285486d5181d2a2f8dffe7e83329d385385b9e55` |
| x86_64 | `25b72a873076880afe96dbf15227c36f6e2a4f7ba7d6577c1fdf7d24734fd901` |

### 悬浮导航栏与玻璃控件

`20261124-2304` 初版的「设置 → UI 设置 → 悬浮导航栏」同时控制悬浮底栏和应用内玻璃样式。`ui/glass/GlassOptics.kt` 按 `badger-glass-sample` 的 `GlassView.java` 迁入顶部光源、折射 130、边缘深度 79、色散 50 和霜冻 9。霜冻模式用四分之一分辨率做高斯模糊，再做边缘光学处理。初版底栏和菜单的遮罩随应用主题变成黑色或白色，不透明度为 19%；最新填充和光照参数见下文「合并操作条与玻璃光照」。玻璃按钮使用 50% 不透明度的当前强调色。底栏选中框闲置时沿用主题色样式，拖动时以原分辨率采样并只应用光学处理。

待办列表、编辑页和类型管理页的独立按钮使用原有扁平样式。待办弹窗和背景裁剪窗口内的按钮也继承这一设置；最新待办顶栏的合并操作条单独使用一块玻璃表面。弹窗与下拉菜单的表面继续使用悬浮导航栏开关控制。

顶栏标题使用半粗字重。二级返回位于固定顶栏，按钮继续调用各页原有的返回逻辑。Android 13 起使用 RuntimeShader；旧系统使用透明填充和轮廓。可采样的 Compose 区域使用本地 GPU 绘制层。相机原生预览和没有独立背景层的内嵌按钮使用主题或所在面板底色，Android 系统选择器继续使用系统样式。

使用上述离线构建环境运行 `:app:assembleDebug :app:testDebugUnitTest`。`FloatingNavBarTest`、`MiuixNavDisplaySmokeTest`、`AppearancePreferencesTest`、`MyPageTest`、`AddPlatformScreenTest`、`ContactDetailInteractionTest`、`SetupStepProfileCropTest` 和 `*Todo*` 共 58 项回归测试通过。

Android 16 模拟器通过无障碍树和日志验证悬浮开关、深浅主题和强调色切换、底栏拖动、二级页滚动与固定返回、待办排序和背景方式下拉、确认窗取消。最终包日志确认普通玻璃以 0.25 倍分辨率采样，拖动时切换为原分辨率且关闭模糊。玻璃观感、真机帧率和相机画面的表现待目视与硬件验收。

`20261124-2304` 开发版应用 ID 为 `top.mcxiafeng.badger.debug`。三种 ABI 均通过 v2 验签，APK 位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `833430a50c0df437352c5d6fcdf988027c20189c895b19592b919c4c76530284` |
| armeabi-v7a | `5c88c722781bd2ae80d52b563bb66dbc771f0fea9b3b5fdd5dc523f2d5ea30eb` |
| x86_64 | `519a1665e4986278b8d57aa6f2173746f5fa1eda27d1fe00e15d26b25d1d5a8c` |

#### 待办扁平按钮

`20261124-2316` 开发版禁用了待办列表、编辑页和类型管理页的玻璃按钮，并恢复空态新建与重试的文字按钮。构建使用 `:app:assembleDebug :app:testDebugUnitTest --tests '*Todo*' --tests '*FloatingNavBarTest' --tests '*MiuixNavDisplaySmokeTest' --tests '*SetupStepProfileCropTest'`，共 34 项测试通过。

Android 16 模拟器覆盖安装后，以无障碍树和日志核对了底栏拖动、排序菜单、编辑待办、固定返回、取消删除及取消新建类型。三个待办入口均采用扁平按钮配置，排序菜单和确认弹窗的玻璃渲染仍然生效。三种 ABI 均通过 v2 验签，应用 ID 为 `top.mcxiafeng.badger.debug`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `73577f360defe5a4161f8cb0393defc26ba488a02a076c7da5ec6a76c7f4345c` |
| armeabi-v7a | `366919bb34fb3b8df6b8c99ed72458b8abbeeecdee68cbe24b86593088f795e6` |
| x86_64 | `c278f28a2eb1d22b58011b1e38a9a49044c42d01efc83074be5ab72fa153c2bf` |

#### 合并操作条与玻璃光照

底部「名片夹」标签对应联系人列表页，其右上角搜索、更多入口共享一个圆角矩形；待办顶栏的搜索、类型管理、新建、排序共享另一个圆角矩形。各入口保留独立的点击与读屏语义，触控目标至少 48dp。整个操作条只绘制一层玻璃，填充沿用当前强调色的 50%，待办其他独立按钮保持扁平。

光学着色器将上下边缘同时加亮，取消底边乘暗，通过加法混合的高亮描边增强发光。折射、色散、深度与高斯模糊参数沿用小样。底栏填充改为深色白色、浅色黑色，不透明度 19%；展开菜单采用相同的颜色方向、不透明度 29%，玻璃模式下不再绘制全屏暗幕。

底栏拖动时，选中框宽高均以原中心放大到 125%，描边覆盖完整一圈。缩放、光学层与发光在 300ms 内渐变，松手和取消拖动都平滑恢复原尺寸与原有静态填充。光学层保持无填充、无模糊，四个标签的点击范围和文字大小不随放大改变。底栏预留顶部空间并解除整体裁切，保持描边完整。

`20261125-0001` 开发版应用 ID 为 `top.mcxiafeng.badger.debug`。`:app:assembleDebug` 成功，相关单元与 Compose 回归共 59 项通过、0 失败、0 错误、0 跳过，包含合并操作条的独立点击、菜单选择与外部关闭，以及开启玻璃后的底栏拖动、取消和 RTL 导航。两项联系人标题测试已按当前顶栏的可见文字语义更新，取消对旧 Miuix 双份文字的数量依赖。

Android 16 模拟器已覆盖安装。无障碍树核对两页的操作入口、联系人搜索、更多菜单、待办搜索与排序菜单；日志确认合并操作条使用 50% 强调色、深浅主题切换、菜单关闭暗幕并使用 29% 填充，以及拖动时进入 125% 放大、原分辨率无模糊分支、释放后恢复。未使用截图，发光强度与平滑程度留待目视和真机帧率验收。三个 APK 的包名、ABI 与 v2 签名均已校验。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `39b82889bb0b1fb6d98a3f315363c4aad0dbbb3d2614d51046bcf068cdcfa54a` |
| armeabi-v7a | `8d34950987009cbbaf6d7511879dbc91430dbf7ed435c6dc5f2ee0fe2911df8b` |
| x86_64 | `7d2ded2a04d1d2e1c2b94af7a27d05606b92343d3707df396ed922bad14b162e` |

#### 底栏叠层与按下反馈

`20261125-0033` 开发版移除悬浮底栏的外部阴影。底栏玻璃先绘制到独立 GPU 层，选中框随后采样这块已经包含底栏填充和光学效果的表面，最后绘制图标与文字；采样层不包含选中框自身，避免循环采样。选中框始终保留玻璃效果与原有选中色，额外高斯模糊为 0，按原分辨率采样；折射 130、边缘深度 79、色散 50 和上下边缘光照沿用当前着色器。底栏自身的霜冻参数保持原值。

触摸观察在 pointer Initial 阶段接收按下，不消费事件。按住当前标签时选中框宽高立即开始向 125% 过渡，进入拖动后保持放大；按下其他标签时开始向宽度 115%、高度 75% 过渡，点击切页期间保持该形状，转为拖动时切换为 125% 放大。位移与形变使用 300ms 的 FastOutSlowIn 非线性缓动。松手、取消与切页结束恢复原尺寸，标签热区和文字尺寸不随形变变化。名片夹和待办右上角的合并操作条改成两端半圆的胶囊轮廓，光学半径改为 24dp，保留独立入口及至少 48dp 的点击区域。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNavBarTest" --tests "*GlassToolbarInteractionTest" --tests "*MiuixNavDisplaySmokeTest"
```

Gradle 返回 `BUILD SUCCESSFUL`。测试 XML 记录 15 项通过、0 失败、0 错误、0 跳过：底栏 10 项、合并操作条 2 项、导航宿主 3 项。新增触摸回归覆盖按下未移动即变形、点击其他标签期间保持压缩、从未选中标签开始拖动时改为放大，以及取消后复位；原有 RTL、双倍字号、页面滑动和读屏点击测试继续通过。

Android 16 模拟器已覆盖安装，通过无障碍元素与日志核对：按下未移动触发 Pressed；点击其他标签触发宽 1.15/高 0.75；拖动触发 1.25 并正确切换首页和我的；名片夹搜索与更多菜单、待办排序菜单可以打开和关闭。当前进程崩溃日志为空。GPU 日志确认底栏无阴影、选中框来源为已渲染底栏、选中框 `frosted=false; sampleScale=1.0`，操作条 `capsule=true`。未使用设备截图；玻璃透光层次、发光观感与实际帧率仍需目视和真机验收。

三个 APK 的应用 ID 均为 `top.mcxiafeng.badger.debug`，版本 `1.0.0-dev`，ABI 与文件名对应，v2 验签通过，位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `3811504f2bb4c5662dd40cec265fcd0d070c0322d0aff736c86476b6ea20ee12` |
| armeabi-v7a | `a308864b3e9ffcd174c532f24ac51b3438a2f57fa16e6d96294d27e7b656fdd9` |
| x86_64 | `7897dd56b8060265d8ebbbf82aa32615d27adc35ab14231841427ee0a1a0b444` |

#### 选中框覆盖图标与全屏滚动页面

`20261125-0121` 开发版替代上一节的选中框层级、空闲效果和 300ms 动画。底栏先录制自身玻璃及全部图标、标签，选中框在这个录制层之外最后绘制；交互透镜采样底栏与图标，不采样自己。空闲选中框仅保留强调色填充，透明度沿用 15%，没有透镜、描边光照和发光绘制。按下、点击、拖动及动画过程中才渐入玻璃，沿用折射、色散、边缘深度和光照参数，额外背景模糊仍为 0。

形变、横向定位和点击切页动画缩短为 150ms，并分别运行。按住当前标签立即向宽高 125% 过渡；点击其他标签时宽 115%、高 75% 的形变约在 50ms 到达峰值，并在本轮 150ms 内回到原尺寸，横向移动同时进行。拖动释放后的缩放复位与对齐目标也同时进行，保留 FastOutSlowIn 非线性缓动。新增测试在同一时间点检查位置与形变均已变化，结束后回到原尺寸并关闭光学状态。

首页、名片夹、待办、我的四个主页面改为内容中的大标题，和对应列表一起滚动，不再在按钮行中显示固定的小标题。主页面 Pager 在悬浮模式下延伸到屏幕上下边缘；列表只通过 contentPadding 预留初始按钮空间、系统导航栏与浮动底栏空间，不再裁掉整块列表的上下区域。空内容继续使用不可滚动的静态布局。联系人页同步更新分页锚点与字母索引的头部条目计数，独立搜索页的原有行为保留。

固定按钮行下方新增独立的渐进模糊表面，只采样页面内容。Android 13 及以上在 GPU 上做两次一维高斯采样，模糊半径从顶部 12dp 平滑减至底部 0，采样比例 1/4；Android 12 使用高斯模糊加渐隐遮罩，旧系统使用主题色渐隐保护。按钮不包含在模糊源中，也在模糊层之后绘制，保持独立点击。该效果不改变页面菜单与独立按钮原有的玻璃开关规则。

待办搜索框和标题同在滚动内容中。列表滚离顶部后打开搜索，会先滚回输入框；IME 避让只施加一次，主 Activity 声明 adjustResize，并关闭系统导航栏的额外对比遮罩。浮动底栏测量值去掉已由 Scaffold 处理的导航栏高度，避免按钮被重复抬高。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNavBarTest" --tests "*GlassToolbarInteractionTest" --tests "*ScrollingPageChromeTest" --tests "*MiuixNavDisplaySmokeTest" --tests "*MyPageTest" --tests "*SocialCardInteractionTest" --tests "*PersonSearchInputTest" --tests "*Todo*"
```

最终 Gradle 返回 BUILD SUCCESSFUL。9 个测试类共 59 项通过、0 失败、0 错误、0 跳过：待办存储 12 项、待办 ViewModel 6 项、联系人搜索 13 项、我的页面 6 项、名片交互 5 项、底栏 11 项、操作条 2 项、全屏滚动标题 1 项、导航宿主 3 项。新增布局测试确认列表 bounds 覆盖完整页面、标题滚走后按钮位置不变且仍可点击。

Android 16 模拟器已覆盖安装最终 x86_64 APK。无障碍树确认待办标题由 y=305 随列表滚至屏幕顶端并离开可见区域，四个顶栏按钮保持原位置；列表末尾两条已完成待办可完整滚入底栏上方。列表滚动后点击搜索可恢复页首输入框，并成功筛选 FollowUpTest；系统显示 IME 覆盖 y=1517..2400，输入框和结果位于其上方。日志确认变量半径 GPU 模糊启用、按钮不在模糊源中、选中框采样包含图标、150ms 并行动画。当前应用进程崩溃日志为空。未使用设备截图，选中框覆盖图标的观感、模糊边界和真机动画手感仍需目视验收。

三个最终 APK 应用 ID 均为 top.mcxiafeng.badger.debug，版本 1.0.0-dev，包内 ABI 与文件名对应，apksigner 验签通过。产物在 app/build/outputs/apk/debug/。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `4cd676fecc1952f59df8ddf3115440e330f90daf8b619fdb11e53c0c52a51a4e` |
| armeabi-v7a | `a230ce67423e797ac5140ee10e8327124babb802bd5fab7b1d950d0b6f11eb4d` |
| x86_64 | `fa63b676e30c59b674ff54f6f413c802afcc7de957e2f17b1bc9464b34f107f2` |

#### 130% 放大、光学参数渐退与屏幕顶边高斯峰值

`20261125-0144` 开发版将按住当前标签及拖动时的选中框宽高放大改为 130%。选中框单独使用深度 60（上一版为 79），折射 130、色散 50；底栏、按钮和菜单仍使用原先的默认光学参数。玻璃退为空闲时，深度、折射、色散和光学层透明度共用同一个 150ms FastOutSlowIn 进度，参数按进度实际传入着色器并全部降到 0。缓存键包含光学参数，避免只降低透明度却继续使用旧着色器强度。零深度分支跳过 smoothstep，避免零区间计算产生异常值。空闲强调色填充仍为 15%。

顶部渐进模糊使用归一化的 13 点高斯核，按真实 sigma 计算权重，GPU 上执行横、纵两个方向的可分离采样，采样比例为 1/4。采样 patch 的边距与图层实际屏幕纵坐标均纳入坐标换算：高斯半径最大值 12dp 明确对应物理屏幕 y=0，向下平滑减至模糊区域底部的 0。渐隐遮罩也以屏幕顶边为起点，并在区域底部归零，消除低分辨率采样与清晰原画面的边界。按钮继续在模糊层之上绘制，不参与模糊采样。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNavBarTest" --tests "*GlassToolbarInteractionTest" --tests "*ScrollingPageChromeTest" --tests "*MiuixNavDisplaySmokeTest"
```

Gradle 返回 BUILD SUCCESSFUL，测试 XML 记录 17 项通过、0 失败、0 错误、0 跳过：底栏 11 项、合并操作条 2 项、全屏滚动标题 1 项、导航宿主 3 项。按下回归增加了宽高精确达到 1.30、取消后恢复 1.0 的断言；点击形变与定位并行、RTL 与读屏点击仍通过。

Android 16 模拟器已覆盖安装最终 x86_64 APK。GPU 日志确认新的高斯着色器正常编译和运行，sampleScale=0.25、maxBlurScreenY=0、surfaceScreenTop=0.0。按住首页标签时，日志记录三个光学参数实际达到 60/130/50；拖动日志 targetScale=1.3，释放后同一 150ms 过渡状态的参数结束值为 0/0/0。待办滚动后标题离开顶部、固定按钮的位置保持不变，当前应用进程无崩溃日志。验证未使用设备截图；模糊边缘与光学渐退的视觉手感仍需目视确认。

三个 APK 的应用 ID 为 top.mcxiafeng.badger.debug，版本 1.0.0-dev，包内 ABI 与文件名对应，apksigner 验签通过。产物在 app/build/outputs/apk/debug/。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `11e38deb7966fdeffd0c7075efe965b19da909ab658e1c65c3ccba9e1f344002` |
| armeabi-v7a | `e621bac4e9cbcad6a91299ecb64b9a02dd09c3ee51081da736ff239841a3c44a` |
| x86_64 | `e751e025b8056ba98b4576b83d581e0e967b6dac5f36dd86a3f7f1e6d0d2bdcf` |

#### 绝对触摸定位、详情按钮与主题色遮罩

`20261125-0216` 开发版将顶部高斯模糊最大半径由 12dp 调到 16dp，继续使用 1/4 采样，峰值仍固定在屏幕最上边缘 y=0。

底栏选中框改为绝对触摸坐标：按下时立即把中心放到触点，每次移动重新计算位置，不再累加手指位移。坐标覆盖整条轨道，包括两端；RTL 使用镜像映射。手指按住时屏蔽页面变化引起的自动归位，松手或取消后再对齐对应标签。非拖动点按保留原有的宽 115%/高 75% 形变，按住当前标签和拖动保留 130% 放大以及 150ms 光学渐退。两个新增 Compose 回归从其他已选标签开始按下，验证中途移动、左右端点与取消复位，分别覆盖 LTR/RTL，防止相对位移偏移再次出现。

联系人详情路由使用扁平内容按钮；返回键通过共享 GlassBackButton 单独启用玻璃，右上角的名片夹和更多按钮复用 GlassActionGroup，共享一块两端半圆的玻璃操作条。新建、编辑待办的返回键使用同一个 GlassBackButton，原有未保存检查、禁用状态和返回回调保留。待办的其余内容按钮继续使用扁平样式。

有背景图待办的左侧渐变遮罩直接读取 CardDefaults.defaultColors().color，和无背景图待办使用同一个 surfaceContainer 来源，随应用主题更新；左侧不再按黑白常量选择颜色。右侧图片原有的 25% 明暗主题遮色保持原行为。列表与编辑器背景预览复用这一实现。

浅色玻璃弹窗、弹出菜单的填充 alpha 改为 0.09；深色保持 0.29。模式选项窗口和列表菜单共用 GlassStyle.menuTint，日志输出实际 alpha。8 位色通道量化后，日志分别约为 0.090196 和 0.290196。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNavBarTest" --tests "*GlassToolbarInteractionTest" --tests "*ScrollingPageChromeTest" --tests "*MiuixNavDisplaySmokeTest" --tests "*ContactDetailInteractionTest" --tests "*Todo*" --tests "*MyPageTest" --tests "*AppearancePreferencesTest"
```

Gradle 返回 BUILD SUCCESSFUL。9 个测试类共 53 项通过、0 失败、0 错误、0 跳过：底栏 13、操作条 2、滚动标题 1、导航 3、联系人详情 5、待办存储 12、待办 ViewModel 6、我的页面 6、外观偏好 5。

Android 16 模拟器已覆盖安装最终 x86_64 APK。已通过界面元素与日志核对联系人详情内容/返回/操作条的样式配置、更多菜单开关、新建与编辑待办的玻璃返回键。深浅模式选项菜单分别记录约 29% 与 9% 填充；有背景图待办的左侧遮罩随主题在当前深色卡片色 #242424 与浅色卡片色 #FFFFFF 之间切换，最后恢复原深色主题。由“我的”页从屏幕 x=500 按下并移动到 x=585，选中框绝对位置由约 1.328 变为 1.693，释放选中待办页，没有从原标签 3 累加偏移；拖动放大日志为 1.3。GPU 日志确认高斯半径 16dp、采样 0.25、峰值 y=0；当前进程崩溃日志为空。未使用设备截图，颜色边界、玻璃和动画观感仍待目视验收。

三个 APK 的应用 ID 为 top.mcxiafeng.badger.debug，版本 1.0.0-dev，包内 ABI 与文件名一致，apksigner 验签通过。文件位于 app/build/outputs/apk/debug/。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `d02404683e3c78ab722b193eb7a088493fd5d975d24fd9f602aca291bed3dda4` |
| armeabi-v7a | `bce8b7b9b97e292d439f3bf0b6d846ab5c2ece63dc3f68c4941758c0e51e827d` |
| x86_64 | `2cd3bf8d478353a7be042cba5efc56b7b5b4ade18124e01cc2466916c16b3512` |

#### 底栏过渡与边界修正

`20261125-0241` 开发版修正操作态退场时的串行动画。缩放与光学进度都由选中框控制器直接启动，在松手或取消的同一次状态变化中分别执行 150ms FastOutSlowIn 动画，不再由“移动/缩放动画是否仍在运行”决定玻璃何时退场。深度、折射、色散及光学透明度跟随光学进度归零，与缩小同时结束。点按其他标签的形变脉冲与光学脉冲使用同一组关键帧时刻，保留原来的点按反馈。

首次按下改为从当前绘制位置启动 150ms 非线性动画，平滑靠近绝对触点；动画期间若触点移动，继续使用最新的绝对目标，不跳变到目标位置。首次靠近结束后，拖动直接按绝对坐标跟随。释放目标仍根据触点对应标签确定，不会因绘制位置尚在过渡中而选错页面。

横向触摸目标限制在第一个和最后一个标签之间；实际框体绘制进一步按当前放大宽度限制 left/right，保持在底栏内侧轨道中。即使手指越过栏边或选中框放大到 130%，框体也不会横向移出底栏；垂直放大效果保留。内侧轨道的 4dp 留白同时为外圈发光预留空间。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNavBarTest" --tests "*GlassToolbarInteractionTest" --tests "*ScrollingPageChromeTest" --tests "*MiuixNavDisplaySmokeTest"
```

Gradle 返回 BUILD SUCCESSFUL。4 个测试类共 22 项通过、0 失败、0 错误、0 跳过：底栏 16、操作条 2、滚动标题 1、导航宿主 3。新增回归使用受控动画时钟，验证退场 80ms 时缩放剩余比例与光学进度一致，结束时分别回到 1.0 与 0；验证首次按下不直接跳到远端、过渡中存在中间位置；验证 LTR/RTL 下放大框在边缘和越界输入时的 left/right 均留在轨道内。原有绝对触点跟随、取消复位和导航测试继续通过。

Android 16 模拟器已覆盖安装最终 x86_64 APK。按下远端标签日志记录 from=0、absoluteTarget=3、smoothArrival=true，随后记录到达目标；向右拖至屏幕 x=1050、向左拖至 x=30 时，选中框逻辑位置分别限制为 3 与 0。释放日志确认 shrinkOpticsAndPositionParallel=true，光学参数以 150ms 过渡归零；当前进程无崩溃日志。未使用设备截图，最终视觉手感仍需目视确认。

三个 APK 的应用 ID 为 top.mcxiafeng.badger.debug，版本 1.0.0-dev，ABI 与文件名一致，apksigner 验签通过。产物位于 app/build/outputs/apk/debug/。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `13e4ecc97d84924d130991ddd034d7e6a26b9997cb87d3f01128cd18a316f45e` |
| armeabi-v7a | `d6df0d7eb1c6f847ea47fb8b1c68c7c25be8f640c730b1086284c3e39c96db07` |
| x86_64 | `c73a939e573d30d7b99842627b6a975ed9d629b07942c4d9fb5b21c1bfd78f8a` |

#### 两端固定中心与完整退场检查

`20261125-0254` 开发版按最新要求重新定义横向限位：限制选中框中心在首末标签的空闲中心之间。放大、缩小时都围绕同一个中心展开，不再因为放大宽度而把两端框体向内推。几何计算先限制逻辑中心，再以中心计算左右边界；RTL 同样适用。放大框允许围绕端点中心对称向外扩展，替代上一节对整个放大框体边缘的限制。

退场保持原来的 150ms 并行动画。本轮新增完整状态检查，覆盖长按后正常释放、快速轻点、拖动到另一端释放、取消、快速切换标签及退场中再次按下。结束状态同时检查：位置归位、dragPage 清空、deformation 为 Rest、scale.value/targetValue 均为 1 倍、光学 value/targetValue 均为 0、两类动画均停止、opticsActive=false；之后再推进 300ms 确认没有旧回调重新改动空闲状态。两端中心还在连续 16ms 采样中与空闲中心比较，覆盖 LTR/RTL。

首次 :app:assembleDebug 已成功产出三个 APK。新增长按释放用例首轮在放大尚未完成时就冻结时钟，导致“缩放剩余量应大于 0、实际为 0”的前置状态问题；补充放大到 130% 且光学进度到 1 的断言后，将快速轻点拆为独立用例，复跑如下测试命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" :app:testDebugUnitTest --tests "*FloatingNavBarTest" --tests "*FloatingNavTransitionTest" --tests "*GlassToolbarInteractionTest" --tests "*ScrollingPageChromeTest" --tests "*MiuixNavDisplaySmokeTest"
```

最终 Gradle 返回 BUILD SUCCESSFUL，5 个测试类共 28 项通过、0 失败、0 错误、0 跳过：底栏 16、完整退场 6、操作条 2、滚动标题 1、导航宿主 3。测试时序修正只涉及测试源码，交付 APK 的生产代码没有进一步变化。

Android 16 模拟器已覆盖安装最终 x86_64 APK。启动日志确认 endpointCenters=idleTabCenters；两端拖动释放后，位置分别落在首末标签，光学参数随 150ms 并行退场归零，当前进程无崩溃日志。未使用设备截图，端点中心一致性由绘制几何测试和逐帧状态测试核验，最终视觉手感仍待目视确认。

三个 APK 的应用 ID 为 top.mcxiafeng.badger.debug，版本 1.0.0-dev，ABI 与文件名一致，apksigner 验签通过。产物位于 app/build/outputs/apk/debug/。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `c547f85389df4bcc5108a1862983c24157e093ca34c49616b2ec893c275b5ad5` |
| armeabi-v7a | `9941e173265258c5323100a920c8fc16db86097f34646e0208cd4b1d5b42d00e` |
| x86_64 | `33181819629301bda3b02f3af8907963e5034d7dd24ce94db0479035a00f2980` |

#### 选中框速度形变与局部强调色

空闲框的宽高基准都缩小到上一版的 91%，中心位置不变。按住当前标签和拖动仍按新基准等比放大到 130%，位移、放大与光学进退场继续使用 150ms FastOutSlowIn。移除点按固定宽高形变关键帧，按显示帧采样选中框实际逻辑位移速度，统一覆盖触点靠近、拖动、点击/读屏切页及释放归位。速度绝对值线性映射形变，达到每秒 12 个标签槽时封顶：宽度最多增加 20%，高度最多缩小 25%；拖动放大与形变相乘。拖动位置停止后下一帧归零，位移动画到达目标时在同一帧归零，不添加形变退场动画；采样任务在静止时结束，下次位移重新启动。

空闲填充固定使用黑色、19% 不透明度，不依赖深浅主题。交互玻璃参数和强调色沿用当前配置。图标与标签先绘制中性色，再将同一内容按选中框的实时 RoundRect 裁切成当前强调色；该内容仍参与底栏背景采样，最后由同一边界绘制选中透镜。框外内容不再按当前/拖动标签整项变色，点击热区与文字尺寸不变。

新增速度回归覆盖静止按住、移动后暂停、慢快拖动差异、20%/25% 上限、130% 放大叠加、读屏点击、91% 几何尺寸及 RTL 端点中心；原退场用例增加速度和最终绘制尺寸归零断言。

`20261125-0333` 开发版的最终构建返回 `BUILD SUCCESSFUL`。测试 XML 确认 6 个类共 33 项通过，0 失败、0 错误、0 跳过：底栏 16、完整退场 6、速度形变 5、操作条 2、滚动标题 1、导航宿主 3。首轮速度回归曾发现到达目标时仍保留最后一帧的微小速度（0.16117094 槽/秒）；生产控制器现在在位移结束时直接取消采样并清零，未通过延长测试等待来规避问题。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNavBarTest" --tests "*FloatingNavTransitionTest" --tests "*FloatingNavVelocityTest" --tests "*GlassToolbarInteractionTest" --tests "*ScrollingPageChromeTest" --tests "*MiuixNavDisplaySmokeTest"
```

Android 16 模拟器已覆盖安装最终 x86_64 APK。元素树和当前进程日志核对了点击切页、静止按住及跨栏拖动：静止按住记录 peakSlotsPerSecond=0、deformation=0，位移结束记录 velocity=0、durationMs=150；释放后光学参数回到 0，当前进程崩溃日志为空。未使用设备截图；局部强调色的像素边界、玻璃观感和真机动画手感仍待目视验收。

三种 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`，版本 `1.0.0-dev`，ABI 与输出文件名一致，均通过 apksigner v2 验签。产物位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `3d3c53a47aa75841954bb4546a2e70e937dce30843bbc56e08a393bda969ee9d` |
| armeabi-v7a | `b49a780572a0586519781d1643d043ffb649c2957471627ef6b0af0eb7d82fa0` |
| x86_64 | `c9900a8ddc0ad9d915b3d437b4c6fa1b4e556f7593b72ca3885c80bfefaf6570` |

#### 135% 放大、透明填充与拖动缓冲

选中框按住/拖动时的等比放大改为新空闲基准的 135%，保留 91% 空闲基准和原有的速度形变上限。交互玻璃移除强调色填充，改用与空闲框相同的黑色、0% 不透明度；可见填充由 19% 黑色随光学进度渐变到 0%。缩放与玻璃强度保持 150ms FastOutSlowIn，填充直接读取同一光学进度；取消和再次按下均从当前值继续过渡。

拖动坐标改为 32ms 时间常数的指数缓冲，手指事件只更新目标坐标，不直接写绘制位置。跟随协程按显示帧间隔逐步追近，剩余距离小于 0.002 标签槽时收敛；快速反向不超出当前点与目标点之间。选中框停止前的形变仍按实际绘制位移计算。松手、取消和点击切页会替换跟随任务，按原有 150ms 归位；释放选中标签仍按手指目标位置决定。

顶部渐进高斯模糊最大半径从 16dp 提高到 32dp，保留四分之一采样。着色器、透明度遮罩和兼容渐变共享屏幕坐标：最强点是屏幕 y=0，归零点是状态栏 inset 加顶部操作行实际高度（操作行默认 52dp，较高按钮会撑高），例如 24dp inset 时为 0–76dp。遮罩使用 17 个 smoothstep 采样点，端点斜率平滑；32dp 半径的采样边距为 96dp，采样与边距换算不移动可见起止点。renderer 按组合缓存。

新增受控动画回归检查缓冲滞后、抖动衰减、反向、松手/取消、大小/填充/光学的非线性同步与中途反转连续性。顶部新增几何测试与带 inset/较高操作行的真实触摸回归，覆盖端点换算、3σ 边距、半径翻倍及点击通路。

`20261125-0358` 开发版最终构建返回 `BUILD SUCCESSFUL`。先运行的 32 项底栏回归通过；最终测试 XML 确认 8 个类共 46 项通过、0 失败、0 错误、0 跳过：底栏 16、缓冲与填充过渡 5、完整退场 6、速度形变 5、顶部模糊几何 7、滚动/真实触摸 2、操作条 2、导航宿主 3。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNavBarTest" --tests "*FloatingNavTransitionTest" --tests "*FloatingNavVelocityTest" --tests "*FloatingNavBufferTest" --tests "*ProgressiveTopBlurGeometryTest" --tests "*ScrollingPageChromeTest" --tests "*GlassToolbarInteractionTest" --tests "*MiuixNavDisplaySmokeTest"
```

Android 16 模拟器覆盖安装最终 x86_64 APK 后，用元素树与当前进程日志验证静止按住、慢速拖动、快速反向、点击切页及顶部设置按钮/返回。日志确认 targetScale=1.35、responseMs=32；向右释放时可见逻辑位置为 2.7748，随后在 150ms 内归位到 3，向左释放时为 0.2109，随后归位到 0，速度与光学参数均归零。当前设备顶部模糊实测配置为 screenY=0..273px、radiusPx=84（32dp）、marginPx=252（3σ）、sampleScale=0.25、variableRadius=true；着色器坐标 63..131.25 还原到屏幕后恰为 0..273px。顶部按钮点击可进入设置并正常返回，当前进程崩溃日志为空。应用最后打开并停留在“我的”页供用户复核，未使用截图或录屏；视觉观感和真机手感保留人工复核。

三种 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`，版本 `1.0.0-dev`，ABI 与输出文件名一致，均通过 apksigner v2 验签。产物位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `26981e161aa6bec80ccf64697214e127e8f68e053189af24c3659083359699cd` |
| armeabi-v7a | `5c84f60fdd938b9477157fe16931b457eb1e819f072710f0918ede970169b231` |
| x86_64 | `83a1be58f2d575f67918987b94a1fa4efbbfc175ec6ef8bcf2b75f6660f58533` |

#### 固定同行标题、完整玻璃菜单与详情/关于页整理

选中框放大调到 150%，仍沿用 91% 空闲基准、32ms 指针缓冲、150ms FastOutSlowIn、0% 交互填充及实际速度驱动的 20% 横向拉伸/25% 纵向压缩。更新了放大后的比例、退场、反转、并行光学/填充和最终归零回归。

TopAppBar 统一固定在按钮同行，不再展开到下一行或跟随内容折叠。四个主页面的标题从 LazyColumn 移到 PageActionBar，与按钮垂直居中对齐；联系人总数保留在标题旁，页面内容仍可在完整视口内滚动。联系人索引按移除正文标题后的实际头部条目数计算。高度由 52dp 最小值和标题/按钮实际高度共同决定，较大字号不与按钮重叠。

顶部继续执行 32dp、四分之一采样、3σ 核边距的渐进高斯模糊；在该模糊之上另绘制同一屏幕端点的主题背景遮罩，屏幕 y=0 处不透明度 100%，操作区域下沿为 0%，使滚入顶部的内容渐隐。高斯计算仍使用 exp(-0.5*x*x) 权重的横纵双 pass，未用纯渐变替代；固定标题和按钮绘制在两层效果之后。

展开菜单沿用按钮的默认玻璃光学参数、霜冻和采样，填充为当前强调色 50%。查阅锁定的 Miuix 0.9.3 源码后，分别移除了 ListPopupContent 宿主和 DropdownImpl 菜单项的实色 surfaceContainer 底色；普通页面和对话框仍恢复原主题颜色，关闭玻璃时菜单保持原实色背景。菜单原有入退场、位置、选项点击和外部关闭行为保留。

普通联系人详情的创建时间移为标签之后的末尾项，保留可见性开关和原日期格式。“我的名片”继续使用 MY_CONTACT_ID=-1 的资料及 user_profile.cardImagePath，未把普通联系人的图片或字段接到我的名片页面。关于页改为品牌、版本与构建、贡献者、项目与支持四组，组间 24dp；仅使用 Badger 自有 logo，移除占位描述、贡献者头像和错误的数据库版本行，保留原外部链接、七连击与超时重置、开发者开关和日志入口。

`20261125-0431` 开发版最终构建返回 `BUILD SUCCESSFUL`。最终测试 XML 共 20 类、164 项，失败/错误/跳过均为 0；覆盖底栏与缓冲/过渡、菜单宿主及菜单项透明化、固定标题对齐与真实触摸、顶部模糊几何、关于页、联系人详情/创建时间顺序、平台添加、自我资料隔离、待办持久化与编辑、外观偏好及路由。菜单首轮检查通过后又查出 DropdownImpl 自身也绘制 surfaceContainer，已同步透明化并更新回归断言后重跑完整验证。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNav*Test" --tests "*GlassToolbarInteractionTest" --tests "*ScrollingPageChromeTest" --tests "*ProgressiveTopBlurGeometryTest" --tests "*PersonSearchInputTest" --tests "*MyPageTest" --tests "*AboutPageTest" --tests "*ContactDetailInteractionTest" --tests "*ContactDetailViewModelTest" --tests "*AddContactPlatformsTest" --tests "*SocialViewModelTest" --tests "*SocialCardInteractionTest" --tests "*MiuixNavDisplaySmokeTest" --tests "*Todo*" --tests "*AddPlatformScreenTest" --tests "*AppearancePreferencesTest"
```

Android 16 模拟器已覆盖安装并启动最终 x86_64 包。无障碍树确认“我的”标题 y=144、高 121px，设置图标 y=173、高 63px，中心均为 204.5px；关于页标题与返回按钮同中心，内容滚动后位置不变。关于页四组标签、Badger 自有 logo 和内部返回流程可用。普通联系人详情中创建时间显示在“添加标签”之后的末尾。展开菜单日志确认 fillAlpha=0.5019608（50% 的 8 位量化）、buttonOptics=true、opaqueHost=false、opaqueItems=false；高斯模糊仍为 variableRadius=true、radiusPx=84（32dp）、屏幕范围 0..273px，新增 contentVeil=100%..0%，按钮不在采样中。当前进程崩溃日志为空。未使用设备截图或录屏，最终观感留给用户目视验收。

三种 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`，版本 `1.0.0-dev`，ABI 与输出文件名一致，均通过 apksigner v2 验签。产物位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `a4d03098430b1ef2e4619e19a404495b280a90657c142b03d409fe8eeb6ee16c` |
| armeabi-v7a | `231868e0d250f54654759dcf2d6ba598b3b46f7b3c4cecbdf30863d48742ce74` |
| x86_64 | `28df4d14519b7729287a6d3fb1dd296d6ba8bc4b9c10e1fc499ac37e4c31a8fa` |

#### 任意标签按住放大、统一 73 设计 px 与 G2 轮廓

按住未选中标签现在与按住当前标签一样进入 Pressed，位置在 150ms 内靠近触点的同时等比放大到 150%；位置停止且仍按住时保持放大、速度形变归零。松手点击从当前状态平行缩小/退光学/恢复黑色 19% 填充，不再重新启动玻璃脉冲造成进度错位；无实际指针的读屏/程序点击仍保留原脉冲。既有 32ms 拖动缓冲、速度形变上限 20%/25%、空闲 91% 基准和端点中心限制保留。

普通内容容器使用 BadgerRadius.surface = 73 / 3.5 = 20.857143dp，避免将 73 直接当 dp。按钮和底栏保留独立半径；过小组件按短边一半限制实际半径。布局中的卡片、输入框、标签、图片容器、弹窗、菜单与提示条接入统一轮廓，真圆头像/色点保持圆，取景和裁剪的功能性矩形及二维码完整图像/quiet zone不改。

BadgerShapes 用每角两段三次 Bezier 定义 G2 轮廓：直线接点曲率严格为 0，中间接点共享位置、切线与曲率，正半径下切线非零、轮廓凸且连续。不是把普通 RoundedCornerShape 或库 squircle 名字直接当作 G2。玻璃裁切、边缘描线、外发光、选中框填充与强调色遮罩共用路径；AGSL 用同一控制参数求曲线距离/法线，并分别处理四个角，兼容二维码弹层按角几何。

原生 Card/Button/TextField 的背景圆角设为 0，由应用 G2 背景/裁切主导；输入、浮动标签、IME、原生点击与长按回调仍交给 Miuix。移动反馈卡片将实际背景与 G2 裁切放在原生缩放内部，避免缩放时被静态轮廓切断。被动卡片继续调用原生无点击重载，保留内部 Pager 的独立语义；阴影使用 clip=false，视觉裁切采用 continuousClip，避免 Generic shape 参与命中导致点击失效。非 G2 的圆和矩形仍委托原生 clip。

弹窗通过透明安全内缩让库固定的 32dp+ 裁切落在可见 G2 面板外，原有布局边距尽量补偿；透明边缘的点击仍转发关闭。提示条沿用原生队列、超时、动作结果和滑动关闭；开关保留原生圆形滑块、拖动、触觉和语义，仅轨道重绘。调色盘/调色器继续委托原生 HSV/颜色空间算法及指针映射，只替换预览、色格和轨道视口；0.5dp 绘制安全内缩防止原生圆胶囊二次裁切，控制点包络测试证明 G2 曲线完整位于原裁切内。扫码新增标签的选色状态修正为无符号 ARGB Long，未升级数据库或迁移既有数据。

底栏图标/文字间距从 4dp 收紧至 2dp，横向轨道向中心收拢，窄屏自适应保证至少 48dp 标签热区。放大与光照按实际高度预留上下空间，大字号下也不依赖固定留白。标签管理搜索与排序进入同一 GlassActionGroup 胶囊，各自保持独立点击。顶部高斯半径从 32dp 加至 48dp，横纵 pass 各 25 个 Gaussian 采样点（12×0.25σ=3σ 边距），保留四分之一采样和既有 100%→0% 遮罩，渐隐端点不变。

首轮 G2/交互检查发现 5 个工具栏点击回归，改为绘制裁切后复测通过。扩展 260 项时又发现被动卡片重载造成的 Pager 语义合并、蓝色名片阴影默认裁切及弹窗安全边缘关闭问题，均已修复；背景草稿测试对图片加载隔离，避免 Robolectric 缺少 AndroidKeyStore 影响与取图无关的取消/文件保留断言。最终 `20261125-0803` 构建返回 `BUILD SUCCESSFUL`，测试 XML 31 类共 260 项通过，失败、错误、跳过均为 0。重点包括 G2 几何 8 项、表单/开关/提示条/调色器交互 7 项、底栏 34 项、菜单/固定顶栏/高斯边界，以及联系人、自己的名片、平台添加、账号、待办与裁剪数据回归。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNav*Test" --tests "*G2CornerGeometryTest" --tests "*FormSurfaceInteractionTest" --tests "*GlassToolbarInteractionTest" --tests "*ScrollingPageChromeTest" --tests "*ProgressiveTopBlurGeometryTest" --tests "*TagManagerSettingsViewModelTest" --tests "*AboutPageTest" --tests "*MyPageTest" --tests "*PersonSearchInputTest" --tests "*ContactDetailInteractionTest" --tests "*ContactDetailViewModelTest" --tests "*AddContactPlatformsTest" --tests "*AddPlatformScreenTest" --tests "*SocialViewModelTest" --tests "*SocialCardInteractionTest" --tests "*MiuixNavDisplaySmokeTest" --tests "*Todo*" --tests "*AppearancePreferencesTest" --tests "*AccountPageTest" --tests "*AccountSwitchPageTest" --tests "*ServerAddressesPageTest" --tests "*CreateContactPersistenceTest" --tests "*CollectionBackgroundDraftTest" --tests "*SetupStepProfileCropTest" --tests "*AlphabetIndexBarTest" --tests "*ScanMarkerConfigTest"
```

Android 16 模拟器已安装最终 x86_64 包。无截图检查确认：自己的名片添加卡仍能打开添加平台页并取消返回；从首页按住“我的”且不拖动，日志为 selected=0、page=3、Scale=1.5×1.5、dragging=false，释放后恢复 1×1。G2 AGSL 已执行，普通玻璃保持 0.25 倍采样，交互选中框保持原分辨率且无霜冻。标签管理右侧两图标共用胶囊，排序菜单和搜索分别可用；搜索输入能够接收文本并筛选，收起后恢复界面。底栏图文向中心收拢，当前设备轨道内边距约 12.19dp、图文间距 2dp。顶部高斯实测 radiusPx=126（48dp）、marginPx=378（3σ）、variableRadius=true、screenY=0..273px，采样坐标 94.5..162.75 还原后端点不变。最终进程无崩溃日志。未截图或录屏，动画观感和真机帧率仍由人工复核。

三种 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`，版本 `1.0.0-dev`，ABI 与文件名一致，均通过 apksigner v2 验签；产物位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `4e7937aabcee31f8375c77cf1eb993884858fc65011bd857179783aa93f0a92c` |
| armeabi-v7a | `8b583800dfb595ee7d783aa9f14629b850ef04591debba249b7bc416c8b5398e` |
| x86_64 | `e8536a008cc9da56ca8ec192d7ff20dd763de6b95f8b362dbb15d54b4d7c636c` |

#### 渐进模糊加强、玻璃按钮统一与滚动页铺满

顶部渐进高斯模糊最大半径由 48dp 提高到 64dp，仍使用四分之一采样、25 个采样点与 3σ 核边距；渐隐遮罩继续沿用屏幕端点 100%→0%，只在加强模糊本身，没有改动起止点或滑动范围。`GlassScaffold` 的 `progressiveTopBlur` 默认为真，所有使用项目 Scaffold 的页面（含二级、三级页）都绘制同一层高斯模糊与内容渐隐；主页面和列表页把 Scaffold 内边距移入滚动视口（`mergePagePadding`），内容实际从模糊层下方经过。本轮完成联系人列表、名片夹、名片夹详情、我的名片、设置及全部设置子页、待办列表/编辑/类型管理、连接地址页的滚动内边距迁移。

玻璃按钮统一为 48dp 触控目标（`BadgerSize.minimumTouchTarget`）与 24dp 图标，圆形保存按钮同尺寸、半径为 24dp；悬浮新建按钮保持库默认 60dp 直径与 24dp 图标，避免工具栏与浮动按钮互相压缩。新建待办与编辑待办的保存入口改为圆形勾选图标：必填标题为空时以中性色 50% 不透明度填充（与其他玻璃按钮同一参数），标题有效后改用当前强调色 50%；删除按钮与保存按钮之间加入 12dp 间距，不再贴合。待办编辑页与待办类型管理页的顶栏按钮在扁平页面上单独启用玻璃；标签管理列表内的自定义颜色与删除按钮恢复原扁平样式。

新增共享按下反馈 `glassPressFeedback`：按下瞬间在触点绘制雷达状提亮光晕（加法混合、裁切在当前 G2 轮廓内），按钮整体先缩小到 81%，再按非线性弹簧过冲到 119%；松手后以带轻微回弹的弹簧恢复到 100%，布局尺寸和点击热区全程不变。普通玻璃按钮、圆形保存按钮、工具栏图标按钮、悬浮新建按钮与合并操作条内的图标都走同一套反馈。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:testDebugUnitTest --tests "top.mcxiafeng.badger.ui.glass.*" --tests "top.mcxiafeng.badger.ui.navigation.FloatingNav*" --tests "top.mcxiafeng.badger.ui.navigation.MiuixNavDisplaySmokeTest" --tests "top.mcxiafeng.badger.ui.components.FormSurfaceInteractionTest" --tests "top.mcxiafeng.badger.pages.settings.*" --tests "top.mcxiafeng.badger.pages.todo.*" --tests "top.mcxiafeng.badger.pages.person.contact.ContactDetail*" --tests "top.mcxiafeng.badger.pages.person.contact.AddPlatformScreenTest" --tests "top.mcxiafeng.badger.pages.server.ServerAddressesPageTest" --tests "top.mcxiafeng.badger.pages.account.AccountPageTest" --tests "top.mcxiafeng.badger.pages.social.SocialCardInteractionTest"
```

Gradle 返回 BUILD SUCCESSFUL，`:app:compileDebugKotlin` 无错误。测试 XML 共 21 类、158 项通过，失败、错误、跳过均为 0；覆盖底栏与缓冲/过渡、G2 几何、表单交互、合并操作条、滚动标题、顶部高斯几何、关于页、我的页面、待办存储与编辑、联系人详情与工具栏、平台添加、账号页、名片交互、连接地址页与导航宿主。全量测试套件里的数据库压力、Robolectric SDK 选择与 sqlite4java 相关用例在未改动的环境下仍受既有模拟器依赖影响，未作为本轮验收依据。

Android 16 模拟器已覆盖安装最终 x86_64 APK。通过元素树实际核对：待办列表顶部四个入口、新建待办页的返回与圆形保存（标题为空时点击保存停留在编辑页，不落库）、待办类型管理页的返回与新建入口（类型行与预设色正常渲染）、标签管理页返回/搜索展开收起/排序菜单（字母、最新创建）与行内改色/删除、连接地址页返回与添加入口及空态、设置页与我的页列表均正常渲染。新建待办输入标题后点保存，列表首行出现对应待办，再打开编辑页删除并确认后返回列表，测试数据已清理。编辑待办顶栏删除与保存的触控中心相距 52.7dp（当前模拟器 2.625 密度下 158px），即两个 48dp 目标之间保留 12dp 间距；图标节点为 24dp。设备工具在本次会话中不提供 logcat，故玻璃光晕、64dp 模糊观感与弹簧手感留待目视复核，验证未使用截图或录屏。

三个 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`，版本 `1.0.0-dev`，包内 ABI 与文件名一致，`:app:validateSigningDebug` 通过；产物位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `9ca8bbe6f9bdc2c8b1515d260b14b94fdc8c7c231a831aa0873d8f286ff63481` |
| armeabi-v7a | `8f31432eae3ea4d749b1e1e4f0272b41f5f3eb075f7d57231eb9e72e75fd86d6` |
| x86_64 | `7a5a97849c348edcf9ccc3fe70336ee1647be69c915d3eaae5d35f59cd0e71e1` |

#### 底栏端点对齐、触点光晕与菜单圆角阴影

`20261125-1648` 开发版将底栏绘制的胶囊两端各内收 8dp（`BadgerSpacing.sm`）：首末标签空闲选中框（轨道槽宽的 91%）在端部的水平留白约 7.8dp，接近其上下留白约 6.5dp；相比上一版的 12dp 内收，胶囊每端加长约 4dp。标签热区、轨道内边距和选中框几何保持不变，仍满足 48dp 触控目标与端点放大不出屏。玻璃模式把 `bounds` 指向同一内收胶囊，扁平模式也改用同一 G2 路径填充并绘制 0.14 轮廓线，不再使用整宽背景。底栏「名片夹」改为「联系人」，图标由 FolderOpen 换为 Person；首页顶栏标题「我的名片」改为「首页」。

触点光晕在按下与移动时以触点为圆心绘制 20% 白色径向渐变，加法混合，半径取底栏高度 1.25 倍；该层位于胶囊表面与图标之上、选中框之下，并裁切在胶囊路径内，可见区域限制在底栏范围，按压结束 240ms 淡出。选中框新增到达惯性：位移结束按峰值速度（4～14 槽/秒映射 0～1）触发，宽度收缩 9%、高度增长 14%，120ms 到达峰值后由 `spring(0.55, 300)` 回弹复位；低于阈值不触发，按下、拖动或再次切页会以 75ms 平滑取消。选中框色散从 50 调为 18，深度 60 与折射 130 不变。底栏布局预留已按 +14% 高度重新计算。

弹出菜单统一使用 `BadgerRadius.menu = 53dp`（139px ÷ 当前 2.625 密度）：深浅模式、强调色等选项框与确认框（`WindowDialog`）以及列表菜单（`OverlayListPopup`）都换用该半径。`WindowDialog` 在 G2 表面外增加 12dp `shadow(clip=false)`；Miuix 宿主为透明全屏 Dialog，阴影不被窗口边界裁切。`OverlayListPopup` 的内容会被库内 `popupClipReveal` 裁到自身边界，外部投影不可见，因此列表菜单只统一圆角，不叠加阴影。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:testDebugUnitTest --tests "*FloatingNav*" --tests "*GlassToolbarInteractionTest" --tests "*G2CornerGeometryTest" --tests "*ScrollingPageChromeTest" --tests "*MiuixNavDisplaySmokeTest" --tests "*ProgressiveTopBlurGeometryTest" :app:assembleDebug
```

Gradle 返回 `BUILD SUCCESSFUL`。6 个测试类共 60 项通过、0 失败、0 错误、0 跳过：底栏 36（含 2 项新增惯性回归）、操作条 4、G2 几何 8、滚动标题 2、导航宿主 3、顶部模糊几何 7。新增用例验证惯性强度随到达速度从 0 线性升到 1、低于 4 槽/秒不触发，以及到位后框体窄于、高于空闲尺寸并在 1.2s 内弹簧复位到 0.5px 内；原有端点中心、RTL、速度形变与完整退场测试继续通过。

Android 16 模拟器已覆盖安装 `20261125-1648` arm64 包。无障碍元素确认底栏标签为首页/联系人/待办/我的，首页标题为“首页”，联系人页标题为“联系人”并显示“共 4 位联系人”，四页切换正常；深浅模式选项框显示跟随系统/浅色/深色，强调色选项框显示五个选项与“已选择”标记，面板横向范围 95..985px（890px ≈ 339dp），点击外部可关闭。设备工具本次不提供 logcat，光晕强度、惯性手感、8dp 端部留白、53dp 圆角与 12dp 阴影的观感待目视验收；验证未使用截图或录屏。

三个 APK 的应用 ID 为 `top.mcxiafeng.badger.debug`，版本 `1.0.0-dev`，包内 ABI 与文件名一致，产物位于 `app/build/outputs/apk/debug/`。

| ABI | SHA-256 |
| --- | --- |
| ARM 64 位 | `44b8bcc5c088a6c5b43337073e0e3907582b8f62be5487bc04cd32b4392bc642` |
| armeabi-v7a | `083e4ff7a907e74a0af6bccb4306175a6a03382d4f21f8d2bc4dc98e48325a6a` |
| x86_64 | `89975d90df91951a311c78c301db59a666b381ef489728254e10d7aed8e0a29c` |
