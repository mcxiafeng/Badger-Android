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
