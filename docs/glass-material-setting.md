# 独立玻璃材质开关

2026-11-26。UI 设置新增「玻璃材质」，默认开启，与「悬浮导航栏」分别持久化。

| 玻璃材质 | 悬浮导航栏 | 结果 |
| --- | --- | --- |
| 开 | 开 | 保留现有玻璃按钮、菜单与悬浮底栏 |
| 开 | 关 | 按钮和菜单仍为玻璃，仅底栏切回经典样式 |
| 关 | 开 | 按钮使用不透明强调色，面板使用不透明底色；悬浮底栏形状与交互动效保留，关闭光学与材质背景模糊 |
| 关 | 关 | 同样的无材质控件与面板，底栏使用经典样式 |

- 新偏好为已有 `badger_settings` 中的 `glass_material_enabled`，缺省 true；原 `nav_bar_floating` 及首次引导逻辑保持独立。
- UI 通过 UiSettingsViewModel 收集两条 StateFlow 并调用独立 setter；新增 ViewModel 只持有 ApplicationContext。
- App 不再用悬浮开关控制整个 GlassStyle 的启用。`enabled`/`glassButtons` 管理既有控件形态与反馈，`materialEnabled` 决定材质绘制，`floatingNavigation` 决定底栏分支。
- 无材质分支在创建 GlassOptics、RuntimeShader 和采样坐标之前返回，使用相同 G2 bounds 绘制纯色，不采样 backdrop，不画光学 rim。有效按钮直接使用当前强调色 alpha=1；无效内容保留不透明的主题中性色。
- 模式、强调色及其他选项弹窗原先传入透明 underlay，关闭材质时会合成为不透明主题面板；列表菜单、二维码、工具栏与底栏走同一纯色回退入口，保留阴影、位置、尺寸、圆角和内容。
- 缩放、按压光晕、内容透明度、5dp 水平跟随、松手/取消恢复、点击/长按及导航状态均独立于材质开关。悬浮选中框关闭材质后仍保持可见的普通选中填充及原有运动。
- 悬浮导航开启且玻璃材质关闭时，选中框额外绘制 1dp 白色描边，复用 `opticalProgress` 的 100ms 非线性淡入/淡出：触摸或位移时显示，静止且无触点时消失。描边与填充共用实时 G2 轮廓，随缩放、速度形变和到位惯性变化；不会开启玻璃采样或光学 rim。
- 页面顶部渐进模糊与渐隐保持原有独立设置；其专用 backdrop 不因材质关闭而停止录制。

## 验证

配置测试覆盖缺省、旧偏好兼容、两项独立保存及重新初始化；UI 测试覆盖两个可用开关、行/原生开关点击、四种组合与 ViewModel 重建。材质测试覆盖强调色填充 alpha=1、透明弹窗底色的纯色回退、无材质按钮点击/取消与形变、浮动底栏点击/滑页/取消，以及无材质模式/强调色/二维码弹层的操作和方向切换。

`:app:assembleDebug` 完成三个 ABI APK；最终相关测试返回 `BUILD SUCCESSFUL`，177 项、0 失败、0 跳过。首轮的导航测试为普通 ComponentActivity，已显式注入真实 UiSettingsViewModel；灰度合成断言按一个 8-bit 色阶容差校验。打包后仅调整了测试，正式源码未变，最终主编译任务为 UP-TO-DATE。未运行全仓库测试。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" :app:testDebugUnitTest --tests "*GlassMaterial*Test" --tests "*NavBarConfigTest" --tests "*UiSettingsPageTest" --tests "*MenuSurfaceGeometryTest" --tests "*PageScrollInsetsTest" --tests "*SettingsScrollEffectsTest" --tests "*ScrollTopFadeMaskTest" --tests "*ProgressiveTopBlurGeometryTest" --tests "*ScrollingPageChromeTest" --tests "*GlassToolbarInteractionTest" --tests "*GlassTouchFeedbackTest" --tests "*MyPageTest" --tests "*SocialCard*Test" --tests "*QrDisplay*Test" --tests "*Todo*Test" --tests "*SaveIconButtonTest" --tests "*ButtonTouchInteractionTest" --tests "*AddPlatformScreenTest" --tests "*ContactDetailInteractionTest" --tests "*BasicInfoCardInteractionTest" --tests "*AboutPageTest" --tests "*FloatingNav*Test"
```

API 36 模拟器已安装并启动 x86_64 最终包，经真实 UI 打开「我的 → 设置 → UI 设置」并切换两项。日志实际记录四种组合：material/floating 为 true/true、false/true、false/false、true/false；材质关闭时记录 runtimeShader=false、optics=false、backgroundBlur=false、fillAlpha=1.0。关闭悬浮后，元素树显示经典底栏的图标与标签位置，模式菜单仍可正常打开。检查结束恢复最初两项都开启，日志确认 true/true；未更改外观选项或业务内容。

遵守禁止设备截图的约束；这些状态、布局和日志检查不等同于人工视觉验收。

最终 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-0603.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认应用 ID 为 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与相应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `DFDDAE5E1263F6F3C8E153244AD0B119BE49B520ADBC4F0091C668259252F19E` |
| armeabi-v7a | `3236015556D1602F109DF2C4BFD2BFC0B9A259E2F1F316F463D46E4679962618` |
| x86_64 | `5324C71C35344DA4F9E7BC328E4F2DB4A4BA6DF9C67CF09FB541C06992C1BAF9` |

## 无材质选中框白色描边回归（2026-11-26）

选中框在现有绘制层上增加白色 Stroke，与普通填充共用 `indicator.bounds`；动画值仅在绘制期读取，没有新增动画状态或改变导航手势。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNav*Test" --tests "*GlassMaterialNavigationTest" --tests "*NavBarConfigTest"
```

构建返回 `BUILD SUCCESSFUL`；6 个测试类共 45 项，0 失败、0 错误、0 跳过。覆盖底栏导航、点击/拖动/取消、光影进退场、速度形变及独立设置，未运行全仓库测试。

API 36 模拟器安装本轮 x86_64 包，读取到初始 floating=true/material=false。按住当前标签并横向拖到「我的」后，日志确认 `white outline=true`、`widthDp=1.0`、`followsOpticalProgress=true`，共用进度到 1；松手到位后以既有 100ms 过渡回到 0。开启玻璃材质后日志确认 `white outline=false`，主标签点击正常。检查后恢复初始开关组合。使用元素树与日志，未截图，描边最终观感仍需人工目视验收。

本轮 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-1209.apk`。三个 ABI 均通过 apksigner 验证；aapt 确认 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `432D9824260FA48EC85E5896936416D9A740FAC5A741488317662650673B1EBA` |
| armeabi-v7a | `2A314058A717FEB5B0D996A8F8AE9A488E7C44B660D6E6C6829E5D2F6CB230A2` |
| x86_64 | `708C1DF9704AA23AFF5D7591541F1BBE26D81437A189ED139C060760AF9BFA73` |
