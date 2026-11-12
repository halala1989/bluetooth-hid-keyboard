# ESP32-S3 Super Mini 烧录结果

- 日期：2026-11-12
- 开发板：ESP32-S3 Super Mini
- 芯片：ESP32-S3 QFN56 rev v0.2
- Flash / PSRAM：4 MB / 2 MB
- MAC：`28:84:85:6D:74:50`
- 下载端口：`COM3`
- 固件：`firmware/ESP32-S3-SuperMini-0001-bridge-merged.bin`
- 固件 SHA-256：`7EE804080A73F85CF4AE701CCCED4D156CA2D4CBE7B5213B675D792E60AD5B62`

## 烧录结果

- bootloader：写入成功，哈希校验通过
- application：写入成功，哈希校验通过
- partition table：写入成功，哈希校验通过
- 自动硬复位后：重新识别为 `VID_303A&PID_4004` USB HID 键盘

新固件已包含 GBK 汉字输出和自动 NumLock 检查。
