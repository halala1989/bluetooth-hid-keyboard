# 分支与两个 Android App 说明（2026-09-11 拆分）

同一个仓库现在维护两条 Android 产品线，**两个 App 可以同时安装**（包名不同）：

## 1. 普通蓝牙版（手机自己当蓝牙键盘）

- 分支：`plain-bluetooth`（对应 2026-09-06 的 v16 状态，未包含 ESP32 外接板功能）
- 包名：`com.hidble.phonekeyboard`
- 应用名：手机蓝牙键盘
- APK：`PhoneBluetoothKeyboard-debug.apk`
- 说明：手机作为蓝牙 HID 键盘直接连电脑打字；大模型/提示词/文献检索等功能保留。
  （`phone-keyboard` 分支是它的持续开发分支，后续小修可在那里继续。）

## 2. ESP32-S3 专用版（板子当蓝牙键盘，手机只负责发数据）

- 分支：`esp32-s3-app`（本分支）
- 包名：**`com.hidble.esp32keyboard`**
- 应用名：**ESP32 蓝牙键盘**
- APK：`Esp32BluetoothKeyboard-v<版本号>.apk`（如 `Esp32BluetoothKeyboard-v25.apk`；同时保留 `Esp32BluetoothKeyboard-debug.apk` 作为最新版便捷名）`n  - **约定：每次更新都要递增 versionCode，并把 APK 文件名带上新版本号**
- 说明：
  - 连接管理页第一张卡片就是「外接键盘板（ESP32-S3）」，扫描/连接 `ESP32-S3 Keyboard`
  - 连上后「发送到键盘」把文本通过 BLE 发给板子（服务 1234/1235/1236），
    板子用 512KB PSRAM 缓冲 + 按速度档控速输出 HID 键盘报文到电脑
  - 板子固件在 `esp32_firmware/`（ESP-IDF + NimBLE），构建/烧录与踩坑见其中 README

## 构建

```powershell
$env:JAVA_HOME="D:\jdk17\jdk-17.0.20+8"; $env:ANDROID_HOME="D:\Android"; $env:ANDROID_SDK_ROOT="D:\Android"
cd android; .\gradlew.bat assembleDebug
# 产物: android/app/build/outputs/apk/debug/app-debug.apk
```

## 注意

- 两个 App 使用不同的 applicationId，但代码同源（namespace 仍是 `com.hidble.phonekeyboard`），
  所以 ESP32 版是从现有功能直接派生的独立安装包。
- 普通蓝牙版分支停在 v16；如果之后普通版也要加新功能，在 `phone-keyboard` 上改，
  需要时再同步到 `plain-bluetooth`。