# 构建与 CI

## 1. 本地构建

前置要求：JDK 17、Android SDK（compileSdk 35）。

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

首次构建会自动下载 Gradle 8.9 与依赖，耗时较长；后续有缓存会快很多。

- Debug 包已开启 Compose，可直接安装调试。
- Release 未开混淆（`isMinifyEnabled = false`），`proguard-rules.pro` 保留为空骨架。

## 2. GitHub Actions 云端构建（推荐，免费）

工作流文件：`.github/workflows/android-build.yml`（名 "Build Debug APK"）。

触发条件：
- push 到 `main`
- PR 指向 `main`
- 手动 `workflow_dispatch`

流程：

| 步骤 | 动作 |
|---|---|
| Checkout | `actions/checkout@v4` |
| JDK | `actions/setup-java@v4`（Temurin 17） |
| Gradle | `gradle/actions/setup-gradle@v4`（8.9，启用依赖缓存） |
| 构建 | `./gradlew assembleDebug --no-daemon` |
| 上传 | `actions/upload-artifact@v4` → `pico-w-keyboard-debug-apk` |

构建产物在 Actions 运行页底部的 **Artifacts** 中下载，解压得到 `app-debug.apk`。

手动触发：仓库 **Actions** → Build Debug APK → **Run workflow**。

### CI 徽章

```markdown
[![Build Debug APK](https://github.com/halala1989/pico-w-keyboard/actions/workflows/android-build.yml/badge.svg)](https://github.com/halala1989/pico-w-keyboard/actions/workflows/android-build.yml)
```

## 3. 常见构建问题

| 现象 | 原因与处理 |
|---|---|
| 依赖下载超时/失败 | 首次拉取较慢，重跑一次即可；`setup-gradle` 缓存生效后基本稳定 |
| `SDK location not found` | 本地缺 SDK；CI 环境无需处理 |
| Java 版本报错 | 需 JDK 17；`gradle.properties` 已配置 jvmTarget 17 |
| Artifacts 为空 | 检查 `app-debug.apk` 路径是否变化；`if-no-files-found: error` 会直接标红 |

## 4. 版本信息速查

| 项 | 值 |
|---|---|
| Gradle | 8.9 |
| AGP | 8.5.2 |
| Kotlin | 2.0.20 |
| Compose BOM | 2024.09.03 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| versionName / versionCode | 1.0 / 1 |

---
