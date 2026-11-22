<div align="center">

<img src="./docs/assets/badger-logo.svg" width="112" height="112" alt="Badger 小猫名片图标">

# Badger

Android 电子名片夹。把一个人的多个社交账号收在同一张名片里，见面时扫码保存，之后用标签和名片夹找回来。

[下载 APK](https://github.com/mcxiafeng/Badger-Android/releases) · [本地构建](#本地构建) · [问题反馈](https://github.com/mcxiafeng/Badger-Android/issues)

</div>

## 能做什么

- **收录联系人**。用相机扫码、从相册识别二维码，或手动录入。拍摄纸质名片后可提取文字，配置 AI 服务后可辅助识别字段。也支持从 QAuxv 导出的好友文件批量导入。
- **整理名片夹**。给联系人加备注和标签，按活动或用途分夹，随时搜索。名片夹支持 JSON 文件导入和导出。
- **出示我的名片**。管理自己的多个社交账号，选择要出示的平台和对应二维码。支持写入 NFC 标签，配置 short.io 后还可以更新标签中短链的跳转目标。

支持记录 QQ、QQ 群、微信、哔哩哔哩、抖音、微博、小红书、GitHub、Telegram、Telegram 群、Facebook、X 和个人网站。链接识别和资料补全能力因平台而异。

## 本地使用与多设备同步

打开 App 就可以在本地用户空间添加和整理名片。需要跨设备使用时，可在「用户与同步」中连接兼容的 Badger Go 服务并登录，在同一账号下同步联系人、我的名片、名片夹、标签及相关图片。

本地用户和各登录账号使用独立的资料空间。登录后可在「本地资料导入」中预览并选择要复制的内容。两端对同一字段的修改发生冲突时，可以查看差异并选择保留的内容。详见 [账号与同步说明](./docs/account-sync.md)。

## 安装

支持 Android 8.0（API 26）及以上版本。按设备架构选择一个 APK，常见的 64 位手机使用 `arm64-v8a`，32 位 ARM 设备使用 `armeabi-v7a`，x86_64 模拟器使用 `x86_64`。下载地址见 [GitHub Releases](https://github.com/mcxiafeng/Badger-Android/releases)。

相机用于扫码和拍摄名片。写入实体标签需要支持 NFC 的设备。账号同步、在线补全、联网 AI 和动态短链需要网络。

## 本地构建

Android 客户端使用 Kotlin、Jetpack Compose、Miuix、Hilt 和 Room。构建需要 JDK 21、Android SDK Platform 37 和 Build Tools 35.0.0，仓库包含 Gradle Wrapper。

```bash
./gradlew :app:assembleDebug
```

从仓库根目录执行。Windows 命令提示符使用 `gradlew.bat :app:assembleDebug`。如本机的 JDK 路径与 `gradle.properties` 中的 `org.gradle.java.home` 不同，按本机安装位置调整该属性。

| 变体 | Gradle 任务 | APK 目录 |
| --- | --- | --- |
| Debug | `:app:assembleDebug` | `app/build/outputs/apk/debug/` |
| Beta | `:app:assembleBeta` | `app/build/outputs/apk/beta/` |
| Release | `:app:assembleRelease` | `app/build/outputs/apk/release/` |

每个变体按 ABI 生成独立 APK。为 Beta 或 Release 签名时，配置 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS` 和 `KEY_PASSWORD`。

## 反馈与贡献

报告问题时请附上 App 版本、Android 版本、复现步骤和脱敏日志。新增平台适配时，欢迎附上可公开分享的样本链接。提交代码前请阅读 [开发指南](./AGENTS.md)。

- QQ 群 `1106424576`
- [Telegram 群](https://t.me/+TCvPsqPXQltjOWM1)
- [Matrix 房间](https://matrix.to/#/#Open-Badger-APP:matrix.org)

## 许可证

本项目采用 GNU GPL v3，完整条款见 [LICENSE](./LICENSE)。
