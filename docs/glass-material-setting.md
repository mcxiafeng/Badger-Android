# 独立玻璃材质开关

2026-11-26。UI 设置新增「玻璃材质」，默认开启，与「悬浮导航栏」分别持久化。

| 玻璃材质 | 悬浮导航栏 | 结果 |
| --- | --- | --- |
| 开 | 开 | 保留现有玻璃按钮、菜单与悬浮底栏 |
| 开 | 关 | 按钮和菜单仍为玻璃，仅底栏切回经典样式 |
| 关 | 开 | 按钮使用不透明强调色，面板使用不透明底色；悬浮底栏与选中框保留位移/拖动跟随，取消缩放、回弹、光晕与描边，选中遮罩为 39% 强调色 |
| 关 | 关 | 同样的无材质控件与面板，底栏使用经典样式 |

- 模态弹层已采用新填充政策：材质开启时深浅均为 9% 白色；材质关闭时深浅色均与普通卡片同色、100% 不透明，且不启用光学。背景统一 15% 黑色遮罩，按钮展开菜单保持原有政策。详见 [模态弹层与点按衔接](modal-popup-and-tap-motion.md)。
- 新偏好为已有 `badger_settings` 中的 `glass_material_enabled`，缺省 true；原 `nav_bar_floating` 及首次引导逻辑保持独立。
- UI 通过 UiSettingsViewModel 收集两条 StateFlow 并调用独立 setter；新增 ViewModel 只持有 ApplicationContext。
- App 不再用悬浮开关控制整个 GlassStyle 的启用。`enabled`/`glassButtons` 管理既有控件形态与反馈，`materialEnabled` 决定材质绘制，`floatingNavigation` 决定底栏分支。
- 无材质分支在创建 GlassOptics、RuntimeShader 和采样坐标之前返回，使用相同 G2 bounds 绘制纯色，不采样 backdrop，不画光学 rim。有效按钮直接使用当前强调色 alpha=1；无效内容保留不透明的主题中性色。
- 模式、强调色及二维码等模态弹窗按上方填充规则处理；按钮展开列表、工具栏与底栏保留各自的纯色回退入口与交互，原有阴影、位置、尺寸及圆角保持。
- 普通按钮的缩放、按压光晕、内容透明度、5dp 水平跟随、松手/取消恢复、点击/长按均独立于材质开关。
- 悬浮导航开启且玻璃材质关闭时，底栏恢复与玻璃模式相同的选中框位移、拖动缓冲和底栏水平跟随，但不挂载光晕绘制，不执行缩放、速度形变、光学或到位回弹。G2 轮廓使用 `primary.copy(alpha = 0.39f)` 遮罩，无描边。选中图标和文字继续沿用原有强调色着色逻辑。再次开启材质时从当前标签建立动效状态，避免从旧位置补播。此规则替代此前完全静态与白色描边方案，其他按钮不变。
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

## 白色描边历史回归（2026-11-26，已由静态强调色方案替代）

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

## 无材质静态底栏历史方案（2026-11-26，位移规则已修订）

底栏在 material=false 时不挂载按压/跟手观察器和光晕绘制，不调用选中框位置、光学及形变动画。点击使用 `requestScrollToPage`，底栏拖动结束直接选择目标，取消保留当前页；遮罩与选中内容着色使用同一个当前页的静态轮廓。绘制代码独立为 `FloatingNavIndicatorDrawing.kt`，保留玻璃模式的光学参数和着色管线。普通按钮源码未改。

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*FloatingNav*Test" --tests "*GlassMaterialNavigationTest" --tests "*NavBarConfigTest" --tests "*ButtonTouchInteractionTest" --tests "*GlassTouchFeedbackTest"
```

三个 ABI APK 构建成功。首轮 57 项中两项新增选中状态断言失败：暂停时钟仅推进一帧时，requestScrollToPage 完成测量后，选中语义尚未重组。测试按测量帧和后续语义提交帧分别同步，未放宽断言或修改正式行为。重跑同一组 `:app:testDebugUnitTest` 返回 `BUILD SUCCESSFUL`；8 类共 57 项，0 失败、0 错误、0 跳过，正式主编译为 UP-TO-DATE。未运行全仓库测试。

API 36 模拟器安装本轮 x86_64 包，按住当前标签与横向拖到「我的」均正常；日志明确 `animations=false; halo=false; outline=false; solidAccentAlpha=0.39`，此时没有启动 FloatingNavIndicator 的按压/光学/运动任务。开启材质后记录 animations=true，按压从当前第 3 页开始，光影进度到 1，释放后回到 0；检查后恢复 floating=true/material=false。使用元素树与日志，无设备截图；最终遮罩与图标颜色观感待人工目视验收。

APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-1306.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认 `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 和各 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `3056E6B2636F2DC14C2AEDF66FFFED3236E531AF3D7895D544908D1AC9C83815` |
| armeabi-v7a | `DAEF6E5026FA4623CCB03FA1C5492C70DDE4F3028094F330C5CB8E7F0A46724D` |
| x86_64 | `546EA40EFEE4EABF534ADFF3E19BDF4D88BD8A35BF2F5F51027B7F5DEFA62F9D` |
