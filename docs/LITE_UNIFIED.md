# 全终端 Lite 键盘

本分支 `codex/lite-unified` 将历史 Lite 版本聚合为一个独立、简明的 Android 应用。

## 应用身份

- 应用名称：`全终端 Lite 键盘`
- applicationId：`com.hidble.carekeyboard.lite`
- 版本：`4.0-test30-lite5`
- 可与医疗住院管理正式测试版同时安装。

## 聚合的终端

一个 App 内可手动切换：

1. 手机蓝牙 HID：手机直接作为标准蓝牙键盘连接电脑。
2. Pico W：手机通过 BLE 数据通道发送，Pico W 输出 USB HID。
3. ESP32-S3 Super Mini：ESP32蓝牙键盘终端-0001，BLE 接收后输出 USB HID。
4. YD-ESP32-S3 N16R8：N16R8 桥接板，使用同一 BLE 数据协议。
5. ESP32-S3 旧 BLE HID：兼容早期归档固件。

## 精简后的功能

- 主界面直接显示当前连接设备名称和 MAC，不显示具体开发板类型。
- “扫描、配对或主动连接”进入统一设备页，电脑和所有开发板使用同一个扫描列表。
- 点击设备后，程序根据真实蓝牙类型自动选择电脑 HID 或开发板 BLE 连接方式。
- 首次连接成功后永久保存设备名称、MAC 和连接类别。
- 下次进入连接页会先主动重连上次设备；失败时再扫描并选择新设备。
- 保留文字输入、发送到当前设备、1–10 档速度、四种中文输入模式和常用语。
- 不显示大模型、API Token、住院患者和病历文书入口。
- Lite 构建使用独立包名、独立图标和独立应用名称，不和完整医疗版互相覆盖。

## 构建

在仓库根目录执行：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="C:\Android\Sdk"
cd android
.\gradlew.bat assembleLite --no-daemon
```

输出：

```text
android/app/build/outputs/apk/lite/app-lite.apk
```

完整医疗应用仍使用普通构建：

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

## 关键源码

```text
android/app/src/main/java/com/hidble/phonekeyboard/LiteKeyboardActivity.kt
android/app/src/main/java/com/hidble/phonekeyboard/LiteConnectionActivity.kt
android/app/src/main/java/com/hidble/phonekeyboard/LiteConnectionMemory.kt
android/app/src/main/res/layout/activity_lite_keyboard.xml
android/app/src/lite/AndroidManifest.xml
android/app/src/lite/res/drawable/ic_lite_launcher.xml
android/app/src/main/java/com/hidble/phonekeyboard/TerminalRegistry.kt
android/app/src/main/java/com/hidble/phonekeyboard/OutputRouter.kt
android/app/src/main/java/com/hidble/phonekeyboard/TerminalSettingsActivity.kt
```

Lite 使用资源布尔值 `lite_edition` 切换：

- Lite：隐藏大模型设置卡片，修改连接页标题。
- 普通完整版：保持全部医疗功能和大模型设置。

## 真机回归

正式发布前应分别验证：

- 手机蓝牙 HID 的首次配对和主动重连。
- Pico W、ESP32-S3 Super Mini、YD-ESP32-S3 的扫描、保存地址和重连。
- 四种中文输入模式与第 8–10 档速度。
- 外接板暂停、继续和完全停止。
- 常用语新增、修改、删除与插入。
