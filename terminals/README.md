# 统一终端包

本目录把此前分散在多个分支、仓库和 APK 中的终端方案归档到同一项目。Android App 不再要求用户安装不同终端版本，而是在「键盘与模型设置」中手动选择终端。

## 可选终端

| 终端 ID | 显示名称 | 数据链路 | 当前状态 |
|---|---|---|---|
| `phone_hid` | 手机蓝牙 HID | Android → 蓝牙 HID → 电脑 | 已验证 |
| `pico_w` | Pico W | Android → BLE 1234/1235/1236 → Pico W → USB/BLE HID | 已有 v10/v11 源码 |
| `esp32_s3` | ESP32-S3 Super Mini | Android → BLE → ESP32-S3 → USB HID | 固件已实机验证 |
| `yd_esp32_s3` | YD-ESP32-S3 N16R8 | Android → BLE → YD 板 → USB HID | 已加入 N16R8 配置和烧录脚本 |
| `esp32_legacy` | ESP32-S3 旧 BLE HID | Android → BLE 数据服务 + BLE HID | 兼容归档，Windows 稳定性较差 |

## 目录

- `sources/phone-bluetooth/`：手机蓝牙 HID 持续开发分支源码快照。
- `sources/plain-bluetooth/`：普通蓝牙存档版源码快照。
- `sources/pico-w-master/`：Pico W v10/v11 固件与 Android 客户端源码快照。
- `sources/esp32-s3-app/`：ESP32-S3 蓝牙桥接专用版源码快照。
- `sources/multi-target-6/`：包含 ESP32-S3、YD-ESP32-S3 N16R8 和新版 Android 客户端的多目标源码快照。
- `sources/pico-w-keyboard-android-client/`：独立仓库 `halala1989/pico-w-keyboard`。
- `sources/pico-w-firmware-independent/`：独立仓库 `halala1989/pico-w-firmware`。

所有快照只归档源码、配置和文档，不保存旧 APK/UF2 二进制；构建所需的预编译固件仍可从原分支或发布目录恢复。

## 当前统一协议

Pico W、ESP32-S3 和 YD-ESP32-S3 均使用：

```text
Service  00001234-0000-1000-8000-00805f9b34fb
Command  00001235-0000-1000-8000-00805f9b34fb
Status   00001236-0000-1000-8000-00805f9b34fb
```

命令包括 `TEXT`、`KEY`、`MOD`、`UNI`、`UMOD`、`SPEED` 和 `STOP`。Android 侧由 `OutputRouter` 根据当前选择把病例文本路由到手机 HID 或外接板 BLE 通道。

## v4.0-test8 速度曲线

- 手机 HID 各档延时系数约为旧版一半。
- 第 8 档继续使用稳定下限；第 9、10 档将下限降低到 7ms、5ms，最高速度约为旧版两倍。
- Pico 与 ESP32-S3 当前固件源码同步采用半速延时表；第 9、10 档把固件安全下限从 2ms 降到 1ms。
- 已烧录的 Pico/ESP32 必须重新烧录本版固件后才会获得新的板端速度曲线。

## 导入来源

来源提交记录、分支和仓库地址见 `catalog.json`。所有文件均来自用户本机已有仓库或用户自己的 GitHub 仓库。
