# AGP 9 / Gradle 9.4 / Miuix 0.9.5-rc01 完整升级记录

2026-11-27。目标：miuix ui/preference 从 0.9.3 完整跟进 0.9.5-rc01，与 nav 统一。前置约束（compose 1.13 家族 AAR 元数据要求 AGP 9.1+）由本轮 AGP 升级解锁，`app/build.gradle.kts` 的版本固定策略已整体移除。

## 版本组合

| 组件 | 旧 | 新 |
| --- | --- | --- |
| AGP | 8.12.0 | 9.1.1 |
| Gradle wrapper | 8.14.3 | 9.4.1 |
| Kotlin（KGP） | 2.3.0（kotlin-android 插件） | 2.3.0（AGP 内置 Kotlin，KGP 经根 buildscript classpath 声明） |
| miuix ui/preference/nav | 0.9.3 / 0.9.3 / 0.9.5-rc01 | 0.9.5-rc01 统一 |
| compose ui/foundation/animation/runtime | resolutionStrategy 固定（1.11.2 / 1.13.0-rc01） | 原生解析 1.13.0-rc01 |
| androidx.lifecycle | 2.9.4（固定策略混排 2.10/2.11） | 2.11.0 |
| KSP | 2.3.6 | 2.3.6（不变） |

## 迁移要点

- **AGP 内置 Kotlin**：`org.jetbrains.kotlin.android` 插件在 AGP 9 下禁止应用，已从根/app 构建脚本移除；AGP 默认 KGP 2.2.10，KGP 2.3.0 通过根 `buildscript { dependencies { classpath(kotlin-gradle-plugin:2.3.0) } }` 显式声明（官方迁移路径）。compose compiler / serialization 插件照常应用。
- **Gradle 9.3 有 settings `plugins {}` 编译回归**（`Unresolved reference 'plugins'`），9.4.1 起修复——AGP 9.1.1 要求 Gradle ≥ 9.3.1，直接用 9.4.1。`maven { url = uri(...) }` 在 Kotlin DSL 严格化后需写 `maven { url = java.net.URI("...") }`。
- **`android.applicationVariants` 已从新 DSL 移除**：APK 改名迁移到 `androidComponents.onVariants`；公开 Variant API（含 `VariantOutput.versionName/versionCode`）没有 `outputFileName`（官方缺口 issuetracker.google.com/issues/480062612），暂以 `com.android.build.api.variant.impl.VariantOutputImpl` 内部类过渡，AGP 升级时需回归此块。
- **lifecycle 编译/运行解析不一致是二进制地雷**：miuix-nav 的 lifecycle 依赖在 runtime scope，删掉固定策略后单测编译期只有 2.10、运行期 2.11；`ViewModelStore.put` 的 key 签名在 2.11 从 `String` 改为 `Object`，编译期旧签名的测试字节码在运行期抛 `NoSuchMethodError`。解法是把直接依赖 `androidx-lifecycle` 对齐 2.11.0，保证两个配置一致。
- **miuix 0.9.3 → 0.9.5-rc01 源码级变更仅一处命中**：`InputField` 在 `textStyle` 后新增 `color: Color` 参数；`ui/components/InputField.kt` 顺势改传 `color = Color.Transparent` 并删除原来包一层的 `MiuixTheme(surfaceContainerHigh = Transparent)` hack。
- **AGP 9.1 R8 默认加 `-repackageclasses`**（可用 `-dontrepackage` 退出）；`-keep class A` 不再隐式保留构造器（`strictFullModeForKeepRules`）。混淆构建（beta/release）如遇规则缺失需按此排查。
- Kotlin stdlib：miuix 0.9.5-rc01 声明 stdlib 2.4.21，KGP 2.3.0 按编译器版本对齐降级 stdlib；当前 nav 0.9.5-rc01 已在此组合下长期运行，ui/preference 由 Robolectric 全量测试覆盖（全部通过），未发现 2.4 专属 API 缺失。

## 验证

- `:app:assembleDebug` 通过，三个 ABI APK 输出文件名正常（`Badger-1.0.0-dev-3-<abi>-20261127-<hhmm>.apk`）。
- 全量 `:app:testDebugUnitTest`（911 项）与升级前遗留失败清单一致：`ContactNetworkResolverTest`/`PlatformIdExtractorTest`/`PlatformFieldsTest`（targetSdk 37 > Robolectric 4.14 maxSdk 35）、`CollectionExporterTest.importFromJson_v2WithTags_restoresTags`（mockk 验证）——均为升级前已存在，本轮零新增失败。
- `:app:minifyReleaseWithR8` 通过：现有 ProGuard 与 baseline-profile 通配规则在 AGP 9.1 R8（默认 `-repackageclasses`、strictFullMode）下可用；带签名的完整 release 打包未在本机验证（无签名环境变量），发布前按原流程跑 CI。
