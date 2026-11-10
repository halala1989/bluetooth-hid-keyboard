# ESP32-S3 Super Mini Test30 烧录与验证记录

- 日期：2026-11-10
- 板型：ESP32-S3 Super Mini
- 芯片 MAC：`28:84:85:6D:74:50`
- 下载串口：COM10
- 固件：`ESP32-S3-SuperMini-0001-bridge-merged.bin`
- 固件 SHA-256：`948AD92A7771D5E8887643F0D3CC77CEA651881C148265B07BBC612628344609`
- 写入大小：642,416 字节
- 回读校验：`verify OK (digest matched)`
- USB 运行标识：`USB\VID_303A&PID_4004\0001`
- BLE 广播名：`ESP32蓝牙键盘终端-0001`

## 停止测试

- 测试文本：100 个 `a`
- 固件速度：1 档
- STOP 后状态通知：`OK:STOPPED`
- 实际输出：35 个字符后终止

## ASCII 键码测试

- 测试内容：`+-0123456789`
- Windows 捕获：`Shift + '='`、`-`、`0` 至 `9`
- 固件映射正确，未出现字符替换或乱码键码。
