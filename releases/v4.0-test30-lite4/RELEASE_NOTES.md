# 全终端 Lite 键盘 v4.0-test30-lite4

- applicationId：`com.hidble.carekeyboard.lite`
- versionCode：`40029`
- 版本名称：`4.0-test30-lite4`
- APK：`全终端Lite键盘-v4.0-test30-lite4.apk`
- SHA-256：`8F67936BF9EF59E3DEF5AD7B914765D62ABE47B0516D8F49CBB5FBE5CD0D45BD`

## 本次更新

- 定位 GBK 汉字不能输出的原因：ESP32-S3 Bridge 固件只实现了 Alt+X，收到 `UMOD:2` 会返回 `ERR:UNI_MODE_NOT_READY`。
- 为 ESP32-S3 Bridge 源码加入 128 KB Unicode→GBK(CP936) 映射表。
- GBK 模式改为按住 Alt 输入小键盘 GBK 十进制机内码。
- 同时补全 Unicode 十进制和 Alt 十六进制小键盘模式。
- Lite App 会在旧固件返回 `UNI_MODE_NOT_READY` 时明确提示“开发板固件不支持此中文模式”，不再静默失败。
- 新增 ESP32-S3 Super Mini 固件构建脚本 `build_supermini_firmware.ps1`。

## 重要说明

本版本修复了固件源码和 Android 端的错误提示，但当前开发电脑没有安装 ESP-IDF，**没有生成新的固件二进制**。

仅安装新 APK 不能修复已烧录旧固件中的 GBK 缺失。必须在一台安装 ESP-IDF 的电脑上编译新的 Bridge 固件并烧录到开发板。

构建与验证步骤见 `docs/ESP32_GBK_FIX.md`。

## 已知边界

- GBK 小键盘 Alt 码要求目标 Windows 中文系统开启 NumLock。
- 目标软件需要接受 GBK/CP936 Alt 机内码输入。
- 固件源码已修复，但尚未在当前电脑编译和实机烧录验证。
