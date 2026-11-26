# 深浅模式设置图标

2026-11-26：「我的」页的「深浅模式」行通过 ArrowPreference.startAction 显示 Brightness6 半明半暗图标，复用 AppearanceIcon，与「强调色」保持相同颜色和间距。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*MyPageTest"
```

构建成功，MyPageTest 8 项通过，0 失败、错误或跳过。三个 ABI Debug APK 已生成。x86_64 APK 通过 apksigner verify，安装至 emulator-5554 并启动；无障碍树确认「深浅模式」和「强调色」文字同处 x=190，点击前者可打开三种模式选项，返回关闭正常。未截图，未进行图标外观的目视验收。

x86_64 APK：`app/build/outputs/apk/debug/Badger-1.0.0-dev-3-x86_64-20261126-0420.apk`，应用 ID `top.mcxiafeng.badger.debug`，SHA256：`0B1A457B090D3CF74DA5F2530E81211CE66D9ACC8FEF205DE667A6D963413AC9`。
