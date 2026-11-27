# 玻璃控件触摸反馈与菜单修正

2026-11-26。保留此前顶部渐隐/高斯效果及未提交的业务改动。

## 触摸时序（2026-11-27 动效重做后的现行规则）

- 按钮和可交互整项共用 `TouchScaleFeedback`：按住缩至 **97%**；松手或取消**单段**恢复 100%。每段 **150ms** `FastOutSlowInEasing`；快速点击和恢复中重按均从当前比例继续。按压内容透明度降至 **70%**，同一 150ms 过渡。
- 底栏整条按压缩至 **98%**；选中框按压同样缩至 97%；选中框位移 239ms 单调曲线 + 32ms 指数缓冲跟手保留；**无速度拉宽/压扁、无到位惯性、无触点光晕**。
- 按钮不再水平跟手（`HorizontalTouchFollow` 仅底栏 barFollow 使用）；按钮触点光晕删除；整项高亮（10% 白）保留。
- 状态变色一律 `animateColorAsState(tween(BadgerMotion.DURATION_FAST))`（选中/就绪等语义色），不允许颜色突变。
- 历史时序（115%/90%/100% 序列、105%/95%/100%、239ms 分段、触点光晕、速度形变、到位惯性）见下方各轮记录，**均已废止**。

### 已废止的旧规则（2026-11-26）

- 按钮和可交互整项共用 `TouchScaleFeedback`：按住 115%；松手或取消先收至 90%，再恢复 100%。放大、收缩、恢复每一段均为 239ms；松手后两段合计 478ms。快速点击和恢复中重按均从当前比例继续。
- 底栏胶囊、图标、光晕及选中框整体使用独立的 105% → 95% → 100%，每一段也各为 239ms。原有选中框自身按压 150%、速度拉宽/压扁及到位惯性宽 -9% / 高 +14% 幅度保留。
- 整条待办及同类复合可交互项目以外层为反馈边界，均匀加亮，去除内部文字点击区域的局部矩形 indication；完成、编辑、排序和子操作的回调与无障碍语义保留。
- 选中框从开始位移或接触起以 100ms 切入玻璃；位移或接触期间保持，静止且无接触后以 100ms 退回。位移、按压形变和到位惯性等其他过渡统一为 239ms。
- 新增 tween 和普通 keyframe 段显式使用 `FastOutSlowInEasing`；选中框惯性恢复保留原阻尼 0.55 / 刚度 300 的弹簧轨迹，经时间映射放入 239ms 总过渡；原有指针缓冲仍使用指数响应。
- 浅色模式仅浮动底栏背景改用 9% 黑色遮罩，深色底栏与选中框的原有色值参数保留。
- 底栏原有光晕强度、半径、层级及出入时长保留。按钮光晕使用相同 20% 白色加法混合和高度半径比例，位于内容之上并裁切在控件轮廓内，按钮光晕过渡为 239ms。
- 组合工具栏整体缩放一次，子项继续保留独立点击与无障碍语义。复合可交互项目用 `ItemPressFeedbackScope` 标明外层拥有反馈，内部玻璃子按钮不再额外缩放，但颜色和点击不变。变换层位于背景和裁切之外，不用原轮廓把放大部分截断；静态布局尺寸不变。

## 入口覆盖

共享 Button、TextButton、IconButton、FloatingActionButton、MaterialIconButton、GlassActionText、SaveIconButton 和 GlassActionGroup 使用统一反馈；另补齐 16 处直接使用玻璃背景的实际点击控件。背景轨道、头像相机角标等非独立点击区域不重复添加。

在 23 个页面/组件文件的 26 处主滚动视口补上现有 `springOverscroll`，包括关于、设置子页、账号、认证、联系人编辑、服务器和待办编辑。既有 spring 不重复安装，fade 保持在前；空导入态不增加 spring，名片夹详情和引导平台页按内容非空启用。

深浅模式与强调色共用透明玻璃菜单底色，关闭玻璃时保留实心回退。关于页新增 `MarsDemon0520`、角色“UI 设计贡献”及公开 GitHub 入口；名称来源为仓库当前 Git 身份和本次明确的 UI 设计贡献，不使用私人记忆或推定真实姓名。

诊断日志使用 `GlassButtonPressTester`、`FloatingNavIndicatorTester`、`FloatingNavBarTester` 及页面 Tester 标签。记录按压/释放、玻璃切换及完成值，不添加每帧日志。

## 上轮验证（本轮分段时序调整前）

- `GlassTouchFeedbackTest` 覆盖 115% → 90% → 100%、非线性中间值、快速点击与连续重按。
- FloatingNav 系列覆盖左右布局、真实触摸与无障碍点击、指针缓冲、拖动取消、选中框原形变范围，以及独立的 100ms 玻璃 / 239ms 形变时序。
- 底栏整体放大后，手势测试通过当前渲染的标签边界计算屏幕触点，不继续套用放大前的固定坐标。
- 最终 `:app:assembleDebug :app:testDebugUnitTest` 成功：24 类、179 项选定测试，0 失败、0 错误、0 跳过。其中 AboutPageTest 9 项、MyPageTest 8 项；包含菜单开启/关闭、选中项与回调次数、贡献者入口、关于页越界移动与固定返回键等回归。没有运行仓库全部测试。
- 页面交回后的 `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` 再检查返回 BUILD SUCCESSFUL，主源码和测试编译任务均为 UP-TO-DATE，确认交付 APK 覆盖最终源码。
- API 36 模拟器安装并启动 x86_64 包。长按当前底栏标签，日志实际完成值为 1.15，释放后为 1.0；100ms 玻璃切入/退出均到达端点。90% 中间值及 239ms 曲线由帧时钟测试核验。
- 分别打开深浅模式与强调色菜单，再点击外部关闭；两者均记录 `underlayAlpha=0.0`、`backdropAvailable=true`，并走 GPU 玻璃渲染路径。全程没有改变外观选择。
- 关于页元素树确认 `MarsDemon0520` / `UI 设计贡献 · GitHub`；关于页、设置页分别下拉越界，日志确认从约 145px / 141px 回位，返回键保持固定且返回流程正常。未打开外部链接。
- 当前应用进程未记录 AndroidRuntime 错误；全程未截图、录屏或捕获真实 UI 像素，观感和低端设备性能仍需目视/对应设备验收。

## 上轮 APK

位于 `app/build/outputs/apk/debug/`，文件为 `Badger-1.0.0-dev-3-<ABI>-20261126-0129.apk`。三个 ABI 均通过 apksigner v2 签名验证，aapt 确认包名 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `AE0D17FCDA4681AB485AD9116E492EE783B1D9D62886DA85392C3D3255730A35` |
| armeabi-v7a | `BDC4074BF3E4FCFB883959128D9154D6F309C76322E43FF741156B183D88894A` |
| x86_64 | `0A82293EB9B8DDEEFFF865EE55CEB170B672AC9E2D3EF9B60D19D9F587BF122F` |

构建使用项目 Gradle Wrapper 8.14.3、daemon criteria 指定的 Oracle Java 21，保持 `--offline` 并通过工程外已有的 local-maven init script 解析本机依赖。未提交或推送。

## 本轮整项高亮与分段时序验证

- 用户提供的截图显示待办内部文字点击区域的原生矩形 indication；本轮没有调用任何设备截图/录屏工具。
- 按钮与可交互整项为 115% / 90% / 100%；底栏整体为 105% / 95% / 100%。放大、收缩、恢复每段各 239ms，松手后两段共 478ms，均非线性；快速松手/取消/重按从当前比例衔接。
- 五处项目边界：TodoRow 整条卡片、ContactDraftEditableRow、TodoAssociatedContact、AddressRow 地址卡、BasicInfoSmallCard 单格。只抑制它们的局部文字 indication；固定回调、完成操作、排序无障碍动作和禁用状态保留。草稿字段行和地址卡的内部按钮使用 ItemPressFeedbackScope，避免叠加第二次缩放。
- `ItemPressHighlightTest` 用原生画布绘制人工生成的纯色卡片，检查左/右/上/下/中心亮度一致、圆角外不亮、释放后没有残留；不采集真实 UI 像素。
- `GlassTouchFeedbackTest` 验证每段 239ms 的中间值与端点、短按、取消、重按，以及复合项目和子按钮只缩放一次。TodoRowInteractionTest 4 项和 BasicInfoCardInteractionTest 2 项验证真实点击、取消、禁用和排序动作。
- 浅色底栏背景为 9% 黑色，深色和选中框原色值保留，已有底栏光晕与选中框形变参数保留。浅/深分支由 FloatingNavVelocityTest 检查；设备当前主题保持不变。
- 渐隐和高斯共用尾段为 51dp，在当前 density=2.625、TopBar 高305px设备上稳定下缘为 y=439px。两者仍共用测量后的区域，不硬编码所有设备的物理像素。
- 本轮最终 `:app:assembleDebug :app:testDebugUnitTest` 返回 BUILD SUCCESSFUL，29 类、204 项选定回归全部通过，0 失败、0 错误、0 跳过；主源码在最终构建中为 UP-TO-DATE，包含最后两处子按钮归属修正。
- API 36 模拟器已安装最终 x86_64 包并启动。长按底栏实际记录 1.05、释放低点0.95、恢复1.0；长按截图对应待办记录 wholeItem=true、1.15、释放低点0.9、恢复1.0，segmentMs=239。待办编辑打开和返回正常，未保存任何数据更改。当前进程无 AndroidRuntime 错误。
- 未进行截图或真实照片/画面的目视验收。补丁格式检查通过，未提交或推送。

### 本轮安装包

`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-0311.apk`。三个 ABI 均通过 apksigner v2 签名验证，aapt 确认包名 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `E7C4B6A9CB16D094FD87E5C05A442AC8BB5FA768A83BD6117D7F8D2422238B80` |
| armeabi-v7a | `ABB2EB13A49A8B88DE983952325EA99B5D02BC6A9CCAE4071572B0297D680880` |
| x86_64 | `ECFE43C8D9D8EFBF80D8485688888C7FA27810C5B1A82070DEEFC317E3052716` |

## 保存点击、触点跟随与遮罩修正（2026-11-26）

- 待办编辑保存条件改为内容有效且未在保存中。原有 `changed` 只用于退出时的丢弃确认，避免已有合法待办的高亮勾号实际禁用。SaveIconButton 的点击处理在绘制变换前，保持 48dp 目标；保存回调及关键状态日志不记录待办内容。
- 按钮和底栏的水平跟随共用 HorizontalTouchFollow：拖动距离的 15%，上限左右各 5dp，松手/取消以 239ms 非线性过渡复位。底栏在水平拖动越过触摸阈值后开始跟随，不消费选中框拖动事件。
- 按压时按钮内容透明度以 239ms 过渡到 50%，释放/取消恢复；玻璃底色不随内容一起变淡。触点光晕使用原来的强度和半径，移除叠加的原生按压 indication。组合工具栏只由外层负责形变和光晕，子按钮独立变淡及点击。
- 共享 Button、TextButton、IconButton、FloatingActionButton、MaterialIconButton、GlassActionText、SaveIconButton 和 12 个页面文件的 16 处自定义按钮接入相同规则；原有整项列表高亮保持独立。
- 核查其他保存条件和调用点，未发现其他页面复用 `changed` 禁用合法未修改草稿的情况。新增 TodoEditorSaveTest 使用真实整页触摸、真实 LocalTodoRepository 和文件重读，覆盖新建、编辑、未修改完成与空标题禁止；SaveIconButtonTest 检查稳定目标、最新回调和取消。
- 深浅模式与强调色本已共用同一个 WindowDialog、透明底色和相同玻璃参数。锁定的 Miuix BasicComponent 不绘制行底色。新增真实 MyPage 宿主的浅/深主题回归检查玻璃开关、透明 underlay 和页面 backdrop；这些检查不能证明 GPU 最终观感。没有找到模式独有的不透明覆盖层，未盲目调整视觉参数。

最终 `:app:assembleDebug :app:testDebugUnitTest` 返回 `BUILD SUCCESSFUL`。19 个选定测试类共 116 项，0 失败、错误或跳过；没有运行全仓库测试。覆盖真实保存落盘与关闭顺序、保存目标和最新回调、光晕/内容透明度/取消、5dp 跟随与复位、底栏导航、联系人操作、外观窗口契约，以及二维码回归。Robolectric 的真实文件 IO 回调等待中显式推进暂停的 Android main looper，避免把未调度的成功回调误判为点击失败。

API 36 模拟器安装最终 x86_64 包并启动；打开已有合法待办、不改内容，实际触摸右上角勾号后返回列表。日志顺序为 `Save action received` → `Saving task draft` → `Task action completed`，同时记录 halo only、contentAlpha=0.5、maxHorizontalDp=5.0。未截图；按钮透明度和位移数值由受控帧时钟测试验证，玻璃观感未做目视验收。

构建命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*Todo*Test" --tests "*SaveIconButtonTest" --tests "*ButtonTouchInteractionTest" --tests "*GlassTouchFeedbackTest" --tests "*GlassToolbarInteractionTest" --tests "*FloatingNav*Test" --tests "*MyPageTest" --tests "*ContactDetailInteractionTest" --tests "*BasicInfoCardInteractionTest" --tests "*AddPlatformScreenTest" --tests "*QrDisplay*Test"
```

最终 APK 为 `app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-0509.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认包名 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `1C44948D86937621B5F55B1B639DA0089AD88D4B3C77314DDC4E2EF57C042635` |
| armeabi-v7a | `A2D2D55C1AFFE8C47B5A812D21EC1F2E137542B6E739203A51DA68D68A05CFC4` |
| x86_64 | `9AF90355E06E84C12B9EF1D0428A2F8F4B8AF01D91F6FE577E5176B030065BFC` |
