# ESP32-S3 Bridge GBK 修复

## 问题原因

手机端“GBK”会向开发板发送：

```text
UMOD:2
TEXT:汉字
```

测试版 Test30 的 ESP32-S3 Bridge 固件只实现了 Alt+X，`UMOD:2` 会返回：

```text
ERR:UNI_MODE_NOT_READY
```

因此英文和数字可以通过 ASCII 键码输出，但汉字无法通过 GBK 输出。

## 修复内容

`esp32_bridge_firmware/main` 已加入：

- `gbk_table.c/.h`：Unicode→GBK(CP936) 映射表。
- GBK 模式：按住左 Alt，输入 GBK 十进制机内码，松开 Alt 触发转换。
- GBK/十六进制小键盘输入前，固件会自动读取主机 NumLock LED 状态；关闭时自动切换开启并等待主机确认。
- 手机蓝牙 HID 收到 NumLock 关闭状态时，也会自动切换并等待 LED 状态更新。
- Unicode 十进制模式：Alt + 前导 0 + Unicode 十进制码点。
- Alt 十六进制模式：Alt + 小键盘加号 + Unicode 十六进制码点。
- Alt+X 模式保持原有行为。

示例：

| 汉字 | GBK 十六进制 | 小键盘十进制输入 |
|---|---:|---:|
| 你 | `C4E3` | `Alt + 50403` |
| 好 | `BAC3` | `Alt + 47811` |
| 医 | `D2BD` | `Alt + 53949` |
| 键 | `BCFC` | `Alt + 48380` |

## 构建 Super Mini 固件

在装有 ESP-IDF 的电脑上执行：

```powershell
cd terminals\sources\multi-target-6
.\build_supermini_firmware.ps1 -IdfPath "C:\Espressif\frameworks\esp-idf-v5.2.2"
```

输出：

```text
releases\supermini-gbk\ESP32-S3-SuperMini-0001-bridge-merged.bin
```

YD-ESP32-S3 N16R8 使用：

```powershell
.\build_yds3_firmware.ps1 -IdfPath "C:\Espressif\frameworks\esp-idf-v5.2.2"
```

## 烧录后验证

1. 不需要手动开启 NumLock，固件会尝试自动确保开启。
2. 让 ESP32-S3 原生 USB 口连接电脑，电脑识别为 HID 键盘。
3. 在 App 选择 GBK。
4. 先测试 `你好好`，再测试纯英文和数字。
5. 如果输出乱码，检查目标电脑当前代码页是否为中文 GBK/CP936。

## 本次实机烧录

- 开发板：ESP32-S3 Super Mini
- 芯片：ESP32-S3 QFN56 rev v0.2，4 MB Flash，2 MB PSRAM
- MAC：`28:84:85:6D:74:50`
- 下载口：`COM3`
- 固件 SHA-256：`7EE804080A73F85CF4AE701CCCED4D156CA2D4CBE7B5213B675D792E60AD5B62`
- 烧录后三个分区均显示 `Hash of data verified`
- 重启后已重新识别为 `VID_303A&PID_4004` USB HID 键盘

烧录使用的固件已保存到：

```text
releases\v4.0-test30-lite6\firmware\ESP32-S3-SuperMini-0001-bridge-merged.bin
```

该固件也已放入 App 内置 `assets/firmware`，以后可以直接从 App 导出。
