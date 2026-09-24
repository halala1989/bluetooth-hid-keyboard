# YD-ESP32-S3 N16R8 桥接板

适用板卡：丝印 **`YD-ESP32-23-2022-V1.3 v2356`**，模组屏蔽罩标识 **`S3-N16R8`**，
板上有两个 Type-C 接口。

## 硬件识别

| 项目 | 配置 |
|---|---|
| 主控模组 | ESP32-S3-WROOM-1-N16R8 |
| Flash | 16 MB，QIO，80 MHz |
| PSRAM | 8 MB，OPI（Octal），80 MHz |
| 无线 | Wi-Fi + Bluetooth LE 5.0 |
| 串口/烧录 Type-C | FT232RQ USB 转串口，用于烧录和查看 UART 日志 |
| 原生 USB Type-C | TinyUSB HID 键盘，连接目标电脑 |
| BOOT 键 | GPIO0，固件中兼作紧急停止 |
| RGB LED | GPIO48（当前桥接固件不依赖该灯） |

两个 Type-C 可以同时连接。推荐：

1. COM 口（FT232RQ）接开发电脑，用于 `idf.py flash` 和日志；
2. ESP32-S3 原生 USB 口接目标电脑，系统识别为 USB HID Keyboard；
3. 手机通过 BLE 连接广播名 **`YD-ESP32-S3 Bridge`**。

## 固件参数

`sdkconfig.yds3.defaults` 已设置：

- `CONFIG_ESPTOOLPY_FLASHSIZE_16MB=y`
- `CONFIG_SPIRAM_MODE_OCT=y`
- `CONFIG_SPIRAM_TYPE_ESPPSRAM64=y`
- `CONFIG_SPIRAM_SPEED_80M=y`
- `CONFIG_PARTITION_TABLE_SINGLE_APP_LARGE=y`

协议与现有 ESP32/Pico 桥接板一致：

- Service `00001234-0000-1000-8000-00805f9b34fb`
- 写特征 `00001235-...`
- 通知特征 `00001236-...`

## 构建

ESP-IDF 工程路径包含中文时，构建目录必须放在纯英文路径。仓库脚本已处理：

```powershell
cd "<repo>"
.\build_yds3_firmware.ps1
```

默认 IDF：`C:\Users\halal\esp\v5.2.2\esp-idf`；
默认构建目录：`C:\esp32_yds3_bridge_build`；
输出目录：`<repo>\releases\yds3-v1`。

## 烧录

### 一键烧录（推荐）

双击仓库根目录的 **`一键烧录_YD-ESP32-S3.cmd`**：

1. 自动检测 FT232RQ（`VID_0403`）或 CH343 串口；
2. 默认使用 `releases\yds3-v1` 里的现成固件；
3. 写入后自动回读校验并复位。

需要重新编译再烧录时：

```powershell
.\flash_yds3_firmware.ps1 -Rebuild
```

手动指定串口：

```powershell
.\flash_yds3_firmware.ps1 -Port COM11
```

### 手动烧录

1. 用数据线连接 **COM Type-C** 到电脑，设备管理器确认 USB Serial Port (COMx)；
2. 通常无需按 BOOT；若无法进入下载模式，按住 BOOT 再插线；
3. 执行：

```powershell
. "C:\Users\halal\esp\v5.2.2\esp-idf\export.ps1"
cd "<repo>\esp32_bridge_firmware"
idf.py -B C:\esp32_yds3_bridge_build -p COMx flash monitor
```

4. 烧录完成后按 RESET 或重新插拔；
5. 将 **ESP32-S3 原生 USB Type-C** 接到目标电脑；
6. 手机 App 使用 `YD-ESP32-S3 键盘`，连接管理页选择 `YD-ESP32-S3 Bridge`。

## 尺寸与分区

构建使用 1.5 MB 单应用分区，满足当前桥接固件。若后续固件显著增大，再改为自定义 4 MB 分区；
不要沿用 Super Mini 的 4 MB Flash / Quad PSRAM 配置。

## 故障排查

| 现象 | 检查 |
|---|---|
| 看不到 COM 口 | 换 COM 口；FT232RQ 通常由 Windows 自动识别；换可传数据的线 |
| 原生 USB 口没有键盘 | 确认接的是 ESP32-S3 原生口；检查目标电脑设备管理器 |
| 手机扫不到广播 | 确认板子已运行、未停在下载模式；授予“附近设备”权限 |
| PSRAM 分配失败 | 确认使用 `sdkconfig.yds3.defaults`，不是 Super Mini 的 Quad 配置 |
| 烧录后不运行 | 查看 COM 串口日志；必要时按 RESET |
