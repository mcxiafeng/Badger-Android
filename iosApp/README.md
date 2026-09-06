# Badger iOS App

Badger 电子名片夹的 iOS 壳工程，基于 Compose Multiplatform 共享 UI。

## 目录结构

```
iosApp/
├── project.yml              # XcodeGen 配置
├── BadgerApp/
│   ├── BadgerApp.swift      # SwiftUI @main 入口
│   ├── Info.plist           # 权限文案 + URL scheme + BGTask 标识
│   ├── BadgerApp.entitlements  # NFC entitlement
│   ├── PrivacyInfo.xcprivacy   # 隐私清单（App Store 提审必需）
│   └── Assets.xcassets/     # App 图标 + 主题色
```

## 构建步骤

### 前置条件

1. **macOS + Xcode 26**（CMP 1.11 需要 iOS 26 SDK 符号）
2. **Apple Developer Program 账号**（$99/年，NFC entitlement 需分配）
3. **XcodeGen**：`brew install xcodegen`
4. **Java 17**：`gradle.properties` 已锁定路径

### 构建

```bash
# 1. 生成 Xcode 工程
cd iosApp
xcodegen generate

# 2. 用 Xcode 打开
open Badger.xcodeproj

# 3. 在 Xcode 中选择签名团队（Signing & Capabilities）

# 4. 运行到模拟器或真机
```

### CI 构建（GitHub Actions）

`.github/workflows/ios-build.yml` 在 `macos-26` runner 上：
1. 编译 shared 模块 iOS framework
2. 安装 XcodeGen
3. 生成 Xcode 工程
4. `xcodebuild build -sdk iphonesimulator` 编译验证

## 权限说明

| 权限 | Info.plist Key | 用途 |
|------|---------------|------|
| 相机 | `NSCameraUsageDescription` | 扫码 / OCR 名片识别 |
| 相册读 | `NSPhotoLibraryUsageDescription` | 选择头像 / 名片背景 |
| 相册写 | `NSPhotoLibraryAddUsageDescription` | 保存名片图片到相册 |
| NFC | `NFCReaderUsageDescription` | 将名片 HTTPS URI 写入 NFC 标签 |

## 后台同步

- `BGTaskSchedulerPermittedIdentifiers`: `top.mcxiafeng.badger.sync.refresh`
- `UIBackgroundModes`: `fetch`, `processing`
- 时序：前台回前台 → kick() 重放；后台 → submit BGAppRefreshTaskRequest

## 真机验证清单（K17）

- [ ] TestFlight 包可安装启动
- [ ] 扫码添加联系人流程走通
- [ ] OCR 名片识别率对照 Android
- [ ] NFC 写入成功率（iPhone 7+ A12 限制确认）
- [ ] 同步时序语义记录（BGTask vs WorkManager 差异）
- [ ] App Store 提审清单就绪（隐私问卷 / 截图 / 描述）
