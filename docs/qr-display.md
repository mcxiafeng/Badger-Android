# 出示二维码

2026-11-26。

- 显示颜色读取应用实际主题 `LocalAppDarkTheme`，支持系统深色而应用浅色的组合。默认浅色为黑码白底，深色为浅码深底；不再使用带透明度的默认码点。
- 继续复用 `Methods.generateQRCode`；留白为 4 个模块，长按换色保留。所有颜色同时满足文字对比度和扫码亮度间隔校验，避免绿色等组合被 ZXing 的局部阈值误判。
- 头像下依次选用已保存的平台昵称、抓取到的用户名、全局个人资料名字；跳过空白和抓取层合成的 QQ 默认用户名。没有名字时省略昵称，不用平台名或账号代替。平台标签和账号单独显示，账号行不再拼接昵称。
- 弹层外壳使用与「深浅模式」相同的 `BadgerRadius.menu`（53dp）和 G2 连续圆角。只旋转内部内容，玻璃背景在屏幕坐标中保持正向取样；捕获调用处主题、玻璃样式与背景源后显式传入根弹层。
- 显示中的 Bitmap 交由 GC 管理，不手动 recycle；纯测试里的临时 Bitmap 在 finally 中回收。日志只包含主题、方向和状态，不记录二维码内容或昵称。

## 验证

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*QrDisplay*Test" --tests "*SocialCard*Test" --tests "*MyPageTest"
```

最终构建返回 `BUILD SUCCESSFUL`：6 个测试类、27 项，0 失败、错误或跳过。二维码测试覆盖两种主题 × 6 个颜色的实际 Bitmap 像素和 ZXing 解码；深色码点反色后解码。另覆盖昵称优先级、空白/合成名字处理、抓取结果更新、根弹层保留调用处主题，以及玻璃开关两种情况下连续四次翻转。没有运行全仓库测试。

生成三个 ABI Debug APK（`20261126-0444`）。x86_64 和 arm64-v8a 均通过 `apksigner verify`，x86_64 最终包已安装并启动。应用 ID 为 `top.mcxiafeng.badger.debug`。

| ABI | APK | SHA256 |
| --- | --- | --- |
| x86_64 | `app/build/outputs/apk/debug/Badger-1.0.0-dev-3-x86_64-20261126-0444.apk` | `3A0E27752E1BBB2923FC71AE349D6C4656967746369997FD6E12058F75AFF21A` |
| arm64-v8a | `app/build/outputs/apk/debug/Badger-1.0.0-dev-3-arm64-v8a-20261126-0444.apk` | `9EF72C6894BDA851630EBA0F6DE5A8DBE23AA0CAA7DCDF1FA5488250AB240F37` |

已有设备检查：在 emulator-5554 上，系统保持深色，应用切至浅色；日志确认二维码按 appDark=false 生成。无障碍树确认昵称、QQ 标签和账号分别显示；点击头像可切换方向，翻转前后日志保持 radiusDp=53.0、glass=true、backdrop=true。遵守禁止截图的约束；图标、玻璃和圆角的实际观感未做目视验收。
