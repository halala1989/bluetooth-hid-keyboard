# Android 端集成指南（ANDROID_INTEGRATION）

本工程固件配套一个 Android 应用，负责把手机上的文本通过 BLE 发送到 Pico W。
本节说明 Android 端与固件对接的关键点，供集成/移植参考。

## 1. 角色与职责

| 端 | 角色 | 职责 |
|---|---|---|
| Android App | BLE GATT **Client** | 扫描 → 连接 → 协商 MTU → 分包 → 串行写帧 |
| Pico W | BLE GATT **Server**（Peripheral） | 广播 → 收帧 → 重组 → 注入 USB HID |

## 2. 连接流程

1. 扫描广播名 `PICO-W-KEYBOARD`（GAP Device Name）；
2. 连接后立即发起 MTU 协商：`requestMtu(DESIRED_MTU)`，`DESIRED_MTU = 247`；
3. 协商成功后可用的单帧 payload：`currentMaxPayload = negotiatedMtu - 3`（上限 240）；
   （`-3`：ATT 头 1 字节 OpCode + 2 字节 Handle）
4. 发现 Service `19B10000-...` 与 TX characteristic `19B10001-...`，
   设置 Write 特性（`setWriteType(WRITE_TYPE_DEFAULT)`）。

## 3. 发送流程

```
sendText(text):
  bytes  = text.toByteArray(UTF_8)          # UTF-8 编码
  frames = BlePacketizer.packetize(bytes)   # 分包为 START/DATA/END
  writeQueue.enqueue(frames)                # 串行发送
```

- 分包规则与帧格式见 `docs/PROTOCOL.md`；
- 写入回调 `onCharacteristicWrite` 成功后才发送下一帧（串行队列，防止乱序）。

## 4. 关键常量

| 常量 | 值 | 说明 |
|---|---|---|
| `DESIRED_MTU` | 247 | 越大单帧承载越多，需 ≤ MAX_ATT_MTU（固件 256） |
| `MAX_PAYLOAD` | 240 | 固件实际能接受的单帧 payload 上限 |
| 广播名 | `PICO-W-KEYBOARD` | 扫描过滤关键字 |
| Service UUID | `19B10000-E8F2-537E-4F6C-D104768A1214` | |
| TX Char UUID | `19B10001-E8F2-537E-4F6C-D104768A1214` | |

## 5. 权限与系统要求

- Android 6.0+：需要 `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`（12+ 运行时权限）；
- 建议在 `onResume` 请求蓝牙打开，`onDestroy` 断开连接；
- 目标设备：任意支持 BLE 4.2+ 的 Android 手机。

## 6. 常见问题

| 问题 | 原因/处理 |
|---|---|
| 扫描不到设备 | 确认 Pico W 已烧录并运行、广播名正确、蓝牙已开启 |
| 写入总是失败 | MTU 未协商成功导致单帧超 240 B？检查 `currentMaxPayload` |
| 中文/符号丢失 | 固件 `usb_hid.c` 仅支持 ASCII，多字节字符当前被跳过 |
| 连接不稳定 | 距离过远 / 干扰，或固件 NVM/连接参数；尝试靠近后重连 |
