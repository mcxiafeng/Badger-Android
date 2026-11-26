# 关于页贡献者文案

2026-11-26：贡献者条目的显示名改为 `MarsDemon`，副标题为 `GitHub · MarsDemon0520`，与其他贡献者格式一致；移除原「UI 设计贡献」文案。链接仍指向 `https://github.com/MarsDemon0520`。

复用并更新 AboutPageTest 的显示断言及链接目标断言，没有新增测试类。

新增圆形贡献者头像：信息前使用 ArrowPreference.startAction；本地提供的头像缩放打包为 `contributor_mars_avatar.png`，其余头像使用 GitHub 图片并由 Coil 缓存。显示名、副标题和链接目标保持。最新验证与 APK 见 [模态弹层与点按衔接](modal-popup-and-tap-motion.md)；下方保留此前文案调整的验证记录。

验证命令：

```powershell
.\gradlew.bat --offline --console=plain -I "C:\Users\MarsDemon\.openclaw\workspace\tools\badger-build\local-maven.init.gradle" "-Pandroid.injected.testOnly=false" :app:assembleDebug :app:testDebugUnitTest --tests "*AboutPageTest"
```

构建返回 `BUILD SUCCESSFUL`，AboutPageTest 9 项通过，0 失败、错误或跳过。x86_64 包已安装到 API 36 模拟器；关于页元素树确认标题 `MarsDemon` 和副标题 `GitHub · MarsDemon0520`，未出现原角色文案。未截图，也未打开外部 GitHub 页面。

APK 位于 `app/build/outputs/apk/debug/Badger-1.0.0-dev-3-<ABI>-20261126-0801.apk`。三个 ABI 均通过 apksigner 验证，aapt 确认应用 ID `top.mcxiafeng.badger.debug`、versionCode=3、versionName=1.0.0-dev 与相应 ABI。

| ABI | SHA256 |
| --- | --- |
| arm64-v8a | `DAB74F32F6382341A8E1A58DF626F23ACCD5C97DF956C2819031B1A28395AD69` |
| armeabi-v7a | `09B3918475BFBB3A690F12BD19E84F89614D40A7503929FC28AF881CA04576A7` |
| x86_64 | `A67282962380C2CC8CC6E2258A35591C520DBEEB3E7FE32ACF544274C0AC09AD` |
