# ESP32-S3 Super Mini · USB HID 键盘最小测试（无蓝牙）

用途：**完全不使用蓝牙**，验证「板子 → 电脑」的 HID 打字链路是否正常。

## 行为

- 板子通过 USB 插到电脑后，作为**有线 USB 键盘**枚举
- 开机 3 秒后打第一个数字 `1`，之后每隔 60 秒依次打 `2`、`3`…`9`、`0`、`1`… 循环
- 键码：'1'..'9' = 0x1E..0x26，'0' = 0x27（USB HID usage）

## 实测结果（2026-09-12）

- 电脑成功枚举：`HID\VID_303A&PID_4004`（Class=Keyboard，Status=OK）
- 自动化文本框测试**成功捕获到 `2`** → USB HID 打字链路完全正常（报告格式、按下/松开均正确）
- 说明之前"蓝牙 HID 打不出字"的问题**不在打字引擎/报告格式**，而在 BLE HID 与 Windows 的连接（Windows 反复 reason 531 断开）

## 构建与烧录

```powershell
. $env:USERPROFILE\esp\v5.2.2\esp-idf\export.ps1
cd <repo>\esp32_usb_hid_test
idf.py -B C:\esp32_usb_hid_build set-target esp32s3   # 首次
idf.py -B C:\esp32_usb_hid_build build
idf.py -B C:\esp32_usb_hid_build -p COM10 flash
```

注意：
- 烧录后 **USB 串口(COM10)会消失**（USB 口被 HID 占用）；控制台已改到 UART0（未接串口线则无日志）。
- 要恢复蓝牙固件：**按住 BOOT 键**再插 USB/按 RESET 进入下载模式，COM 口重新出现后烧 `esp32_firmware`。
- 依赖：`espressif/esp_tinyusb`（IDF 组件管理器自动下载，见 `main/idf_component.yml`）。