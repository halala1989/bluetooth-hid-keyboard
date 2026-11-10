# 医键住院管理 v4.0-test28

- applicationId：`com.hidble.carekeyboard`
- versionCode：`40027`
- 版本名称：`4.0-test28`
- APK：`医键住院管理-v4.0-test28.apk`
- SHA-256：`14732205D576ABD8D28A75514C7DACF72202D9BE7E6C0339C8BB4287E7247916`

## 本次更新

- ESP32-S3 的 BLE 广播设备名改为 `ESP32蓝牙键盘终端-0001`。
- ESP32-S3 的 USB HID 产品名同步改为 `ESP32蓝牙键盘终端-0001`。
- USB 设备序列号由固定占位值改为唯一序列号 `0001`。
- 中文名称放在 BLE 扫描响应中发布，避免超过传统广播包 31 字节限制。
- Android App 同时兼容新名称和旧 `ESP32-S3 Bridge` 名称。
- App 内置的两个板型固件同步更新为带 `0001` 序列号的版本。
- 后续新设备只需修改编译参数 `BRIDGE_SERIAL_NUMBER`，BLE 名称和 USB 序列号会自动同步为该编号。

## 固件

- `ESP32-S3-SuperMini-0001-bridge-merged.bin`
- `YD-ESP32-S3-N16R8-0001-bridge-merged.bin`

## 验证

- Super Mini 与 YD-N16R8 两套固件均构建通过。
- Android APK 构建通过，并确认包含两套新固件。
- 2026-11-10 已通过 COM10 烧录到 ESP32-S3 Super Mini，芯片 MAC 为 `28:84:85:6D:74:50`。
- 烧录后回读校验结果：`verify OK (digest matched)`。
- 重新插拔 USB 后，Windows 识别到 `VID_303A:PID_4004\0001`，确认 USB 序列号已生效。
- 手机侧 BLE 中文广播名还需连接手机 App 后最终确认。
