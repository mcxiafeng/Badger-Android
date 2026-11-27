# 菜单阴影、布局比例和滚动顶部留白

> 2026-11-28 拆分说明：液态界面（玻璃按钮/菜单/弹窗/顶栏/顶部特效）已移至 `liquid-glass-interface` 分支，master 改用 Miuix 原生组件。本文保留的历史配方在该分支仍然有效；与本分支现状冲突处以 [ui-spec](ui-spec.md) 与 [glass-material-setting](glass-material-setting.md) 为准。

2026-11-26。保留此前二维码可读性、昵称、触点光晕和保存修复，不更改数据库或持久化契约。

## 菜单与二维码

- `BadgerRadius.menu` 从原参考 139px（约 53dp）调整为同一密度 2.625 下的 123px（约 46.86dp），不将物理像素直接当 dp。
- 展开列表、选项对话框和二维码共用 6dp 阴影，ambient/spot 黑色透明度分别为 12%/18%。阴影在可见表面裁切外绘制；列表额外预留 12dp 透明空间，避免被 Miuix 原生 reveal clip 直接裁去。
- 菜单定位计算扣除透明阴影空间，以可见表面为准保留 8dp 按钮间距；零间距 ContextMenu 使用同样的 dropdown 参数，TopEnd 等别名按正确左右方向定位并保留 RTL。底部空间不足时可向上展开。
- 二维码贴屏幕顶部时上方两个角为直角，下方两个角有圆角；贴底部时相反。玻璃 bounds、内容裁切和阴影使用相同方向，内部内容单独旋转，不旋转背景取样坐标。
- `ListPopupColumn` 封装仍复用锁定版本 Miuix 的测量和滚动，在其内部增加一个 3dp 顶部 Spacer；页面与下拉控件使用同一个入口。

## 页面与顶部效果

- 首页名片可见表面固定宽高比 5:3，外边距位于比例约束之外，图片仍按原有裁切方式显示。
- 已识别的滚动列表、网格与滚动内容统一增加 `BadgerSpacing.scrollContentTopGap`（3dp），共用 helper 的调用点不重复增加；导航 Pager 和空态不因此增加滚动。
- 设置页列表占满视口，使用与待办页相同的顶部渐隐、20dp 高斯半径、51dp 尾部范围及弹性效果；内容在共用 3dp 基础上额外下移 5dp。
- 渐隐的 DST_IN 只绘制到局部效果下缘，底部不参加蒙版运算；高斯合成的 saveLayer/clip 严格限制到屏幕顶部效果区与本层范围的交集。
- 已确认的设置布局差异是短列表原先没有占满屏幕。现有渐隐 shader 在效果区以下的 alpha 为 1，没有发现黑色 RGB 遮罩的源码证据；范围加固不能单独证明用户所见下半屏变暗的 GPU 根因。

## 验证

测试覆盖菜单间距及上下/RTL 定位、123px 参考圆角、二维码两种停靠方向、滚动 padding 的 LTR/RTL 合并、设置全屏视口与空白区域拖动/回弹、顶栏固定、设置专属 5dp、超大效果层的严格裁切、真实原生画布的渐隐 alpha、实际名片比例，以及相关点击、保存、导航和二维码回归。

`:app:assembleDebug` 已完成三个 ABI APK；修正浮点负零与正零的严格相等断言后，最终 `:app:testDebugUnitTest` 返回 `BUILD SUCCESSFUL`：154 项、0 失败、0 跳过。打包后仅修改了测试断言，正式源码未变，最终测试中的正式编译任务为 UP-TO-DATE。未运行全仓库测试。

使用项目 Wrapper、离线缓存和已有本机 init script：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" :app:testDebugUnitTest --tests "*MenuSurfaceGeometryTest" --tests "*PageScrollInsetsTest" --tests "*SettingsScrollEffectsTest" --tests "*ScrollTopFadeMaskTest" --tests "*ProgressiveTopBlurGeometryTest" --tests "*ScrollingPageChromeTest" --tests "*GlassToolbarInteractionTest" --tests "*MyPageTest" --tests "*SocialCard*Test" --tests "*QrDisplay*Test" --tests "*Todo*Test" --tests "*SaveIconButtonTest" --tests "*ButtonTouchInteractionTest" --tests "*AddPlatformScreenTest" --tests "*ContactDetailInteractionTest" --tests "*BasicInfoCardInteractionTest" --tests "*AboutPageTest" --tests "*FloatingNav*Test"
```

API 36 模拟器已安装并启动最终 x86_64 包。元素树确认二维码翻转及关闭保持正常，日志确认其 squareScreenEdge 在 top/bottom 切换，glass/backdrop 均为 true；菜单圆角为 46.857143dp、阴影6dp，选项窗口 underlayAlpha=0.0、backdropAvailable=true；名片测量为 996×598px（5:3 取整）；高斯效果实际 clip 为屏幕 y=0..439px，半径52.5px（20dp @2.625）。设备检查遵守禁止截图的约束，使用元素树、布局坐标和日志；不将这些检查当作人工目视验收，未证实下半屏变暗的 GPU 根因。

最终 APK 为 `app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-0534.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认应用 ID `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与对应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `7D69C185FFF9463F7A17641208DAFF5CB809FB140701D2E0E3C83B4780AC4DC6` |
| armeabi-v7a | `0860198F8F8AD540F083059E64F9C9402844C00D81C740A6B445BCE87F27BB35` |
| x86_64 | `3F38A078C5D1D166E67A9C3AD1688B41AE7420861CA68B5AFC897EE5C7880D36` |
