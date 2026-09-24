# YD-ESP32-S3 N16R8 桥接版 v35

日期：2026-09-24

## 新增

- 第四个硬件目标 `yds3` / `yds3Lite`。
- 板卡：丝印 `YD-ESP32-23-2022-V1.3 v2356`，模组 `ESP32-S3-WROOM-1 N16R8`。
- 16 MB Flash + 8 MB OPI PSRAM 专用固件配置。
- BLE 广播名：`YD-ESP32-S3 Bridge`。
- USB HID 产品名：`YD-ESP32-S3 Keyboard`。
- 双 Type-C 说明：COM 为 FT232RQ 烧录/日志口，USB 为 ESP32-S3 原生 HID 口。

## 产物

- `APK/YD-ESP32-S3-Keyboard-v35.apk`
- `APK/YD-ESP32-S3-Keyboard-Lite-v35.apk`
- `YD-ESP32-S3-bridge-merged.bin`
- `esp32_bridge_firmware.bin`
- `flasher_args.json` / `flash_args`

## 烧录与验证

- 一键脚本可自动识别 FTDI `VID_0403:PID_6001`（本机 COM11）和 CH343。
- 实测 COM11 可自动进入下载模式。
- 实测写入 645,568 B 合并固件，回读校验 `verify OK`。
- 原生 USB 运行模式实测枚举为 `VID_303A:PID_4004` / `HID Keyboard Device`。

详细说明见同目录 `FLASH_GUIDE.md`。
