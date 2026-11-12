# 全终端 Lite 键盘 v4.0-test30-lite1

- applicationId：`com.hidble.carekeyboard.lite`
- versionCode：`40029`
- 版本名称：`4.0-test30-lite1`
- APK：`全终端Lite键盘-v4.0-test30-lite1.apk`
- SHA-256：`277BAC6FCD27FC01C3602DC7D4559BBB90D008EB5F9A4FBBF4FBCBB9256A9D8A`

## 本次更新

- 新建独立 Lite 分支构建，不再为不同目标板分别安装应用。
- 将手机蓝牙 HID、Pico W、ESP32-S3 Super Mini、YD-ESP32-S3 N16R8 和旧 ESP32 BLE HID 聚合到同一个连接入口。
- Lite 首页直接显示当前终端、连接状态和发送区。
- 统一连接页可选择终端、主动连接、扫描板子、配对电脑、断开和设置输出参数。
- Lite 构建隐藏大模型、API Token 和住院病历入口。
- 保留 1–10 档速度、四种中文输入模式和常用语管理。
- 独立应用名称、图标和包名，可与完整医疗管理版同时安装。

## 安装与构建

```powershell
cd android
.\gradlew.bat assembleLite --no-daemon
```

APK：

```text
android/app/build/outputs/apk/lite/app-lite.apk
```

## 已知边界

- 当前完成构建与 Manifest 核验，尚未连接真实手机和全部目标板做最终回归。
- 外接板实际输出速度仍受对应固件版本影响。
- Lite 不包含大模型和医疗文书功能。
