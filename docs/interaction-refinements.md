# 交互与对齐调整（2026-11-26）

- 待办行按住/取消只保留整行白色遮罩反馈，停止行缩放动画；编辑、完成与排序操作维持原契约。
- 所有底栏形态与待办行共用左右 24dp 边距。悬浮胶囊不再额外内收 8dp 或受 400dp 最大宽度约束，选中框按实际轨道宽度均分四栏，轨道两侧留白同步调整为 4dp，并在两种材质下使用同一轮廓为图标/文字着色。
- 无玻璃悬浮底栏恢复与玻璃模式相同的位置跟踪、拖动缓冲和底栏水平跟随。取消缩放、速度形变、到位回弹、光晕和描边，保持 39% 强调色选中遮罩。位置状态和视觉形变开关分别处理。
- 新建联系人草稿页移除「联系方式」展示分组。已有联系人详情与草稿保存的数据契约保持。
- 文字操作按钮去掉底色并使用强调色文字；显式传入的次级颜色及禁用颜色遵从调用者与主题，普通图标/确认/保存按钮不因此变化。
- 渐隐和渐进高斯共用固定屏幕下缘：400px @2.625 参考密度，换算为约 152.38dp。两者继续从屏幕顶部开始，顶栏高度、状态栏和内容视口不重启渐变。
- 从实际内容滚动到达边界的弹簧缓冲按到达速度调整；已在顶部/底部继续拉动、短列表拉动保持既有阻尼。

## 验证命令

```powershell
.\gradlew.bat --offline --no-daemon --console=plain --max-workers=1 "-Dorg.gradle.jvmargs=-Xms64M -Xmx1536M -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Dfile.encoding=UTF-8 -Djdk.attach.allowAttachSelf=true" "-Pkotlin.compiler.execution.strategy=in-process" -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\serial-tests.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNav*Test" --tests "*GlassMaterialNavigationTest" --tests "*NavBarConfigTest" --tests "*TodoRowInteractionTest" --tests "*SpringOverscrollStateTest" --tests "*ButtonTouchInteractionTest" --tests "*GlassTouchFeedbackTest" --tests "*PersonSearchInputTest" --tests "*CreateContactPersistenceTest" --tests "*ProgressiveTopBlurGeometryTest" --tests "*ScrollTopFadeMaskTest" --tests "*ScrollingPageChromeTest" --tests "*SettingsScrollEffectsTest" --tests "*PopupTransitionTest" --tests "*AnimatedListPopupTransitionTest"
```

本机系统提交内存不足曾导致 Gradle 和测试 JVM 的 G1 分配失败。最终命令使用单次 Gradle、Kotlin 进程内编译与 Serial GC；本地测试 init 脚本将测试初始堆设为 64m、最大 512m、单 fork。未更改仓库 Gradle 配置或系统服务。

相关回归发现通用弹窗背景的 clickable 父节点会合并带 PaneTitle 的分享内容，5 项分享测试报 unmergeable property PaneTitle。背景关闭改为独立触摸处理，不合并面板语义；保留弹窗内操作和点击外部关闭，并追加弹窗转场回归。

模拟器已确认：新建名片无「联系方式」分组，待办整行 feedback motion=false，底栏 material=false 时位置跟踪和拖动仍工作；渐隐与高斯的日志均为 screen=0..400.0。边界到达日志记录不同速度和 gain，快速滑动最高 1.4，上下方向均有释放回位。未捕获设备图像，观感仍需人工目视验收。

扩展回归返回 `BUILD SUCCESSFUL`：18 个测试类共 126 项，0 失败、0 错误、0 跳过，包括 5 项原先因 PaneTitle 合并失败的二维码分享测试。未运行全仓库测试。最后将轨道两侧留白同步改为 4dp 后，另构建最终 APK 并复验底栏：`*FloatingNav*Test`、`*GlassMaterialNavigationTest`、`*NavBarConfigTest` 共 6 类 47 项，0 失败、0 错误、0 跳过，构建再次返回 `BUILD SUCCESSFUL`。

最终 APK 已安装 API 36 模拟器。元素树确认两种材质下悬浮标签位置相同，经典底栏也使用新的左右边距；日志记录轨道留白 4.19dp（11px 取整）、端部内收 0dp，并实际切换到 floating/material 的四种组合。深浅模式窗口可正常打开，点击外部后关闭，记录 mergePanelSemantics=false。检查后恢复初始 floating=true/material=false，外观模式与业务资料未修改。

最终 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-1356.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认应用 ID `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 和对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `C426214CB2937D5C07FA5572D1EC6AED517EB2DCC3AF106B88AFF13A9BEFDD5F` |
| armeabi-v7a | `586E7FE4E27EFC92446807EE2B53CA8071D3F7C5BAA640EB6D00EF77B1B2D52F` |
| x86_64 | `29D7C27FC217F1A5C77B093CFF534F32878887EC5C267E7EA44BADF69436BA44` |
