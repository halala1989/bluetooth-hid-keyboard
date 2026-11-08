# 烧录指南（FLASHING）

## 1. 准备工作

- 固件：`pico_w_keyboard_firmware.uf2`（从本地 `build/` 或 GitHub Actions 产物获取）
- 硬件：Raspberry Pi Pico W（带 CYW43439 无线）
- 数据线：支持数据（非仅充电）的 USB-C / Micro-USB 线

## 2. 烧录步骤（BOOTSEL 方式）

1. 按住 Pico W 板载 **BOOTSEL** 按钮不放；
2. 用数据线连接 Pico W 到电脑；
3. 松开 BOOTSEL——电脑出现一个名为 `RPI-RP2` 的 U 盘；
4. 把 `pico_w_keyboard_firmware.uf2` **拖入/复制**到该 U 盘；
5. 文件复制完成后 Pico W 自动重启，`RPI-RP2` 自动弹出，烧录完成。

> 注意：从 BOOTSEL 进入时，Pico W 处于"仅 USB 存储"模式，
> 此时**不会**执行固件（不会广播 BLE）。重启后才会运行。

## 3. 验证

烧录成功后：

- **蓝牙**：附近设备应可搜到广播名 `PICO-W-KEYBOARD`；
- **USB 枚举**：将 Pico W 通过 USB 插入 Windows 主机，系统应识别为
  HID 键盘设备（设备管理器中可见 "USB Input Device"）。

## 4. 常见问题

| 现象 | 可能原因 | 处理 |
|---|---|---|
| 看不到 `RPI-RP2` 盘 | BOOTSEL 未按住/线不支持数据 | 重新按住 BOOTSEL 再插线，换数据线 |
| 复制 UF2 后无反应 | UF2 文件损坏/目标错误 | 重新从 CI 或 build 下载 UF2 再试 |
| 无 BLE 广播 | 固件未运行/供电不足 | 确认 USB 已枚举，检查 PWR 灯 |
| Windows 不识别为键盘 | 主机未完成枚举 | 重新插拔 USB，查看设备管理器 |

## 5. 重新烧录 / 变砖恢复

- 任何时候都可按住 BOOTSEL 重新进入烧录模式，覆盖写入新的 UF2；
- 若固件写入错误固件导致无法启动，再次 BOOTSEL 烧录正确固件即可恢复，无变砖风险。
