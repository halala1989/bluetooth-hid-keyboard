# Android 端开发指南

## 1. 目录结构

```
app/src/main/java/com/example/picowkeyboard/
├── MainActivity.kt          # Compose 入口 + 运行时 BLE 权限 + 蓝牙开关引导
├── MainViewModel.kt         # 状态汇总、自动重连、Hex 预览、记住上次设备
├── ble/
│   ├── BleManager.kt        # 扫描/连接/MTU/串行写队列/回调/取消
│   ├── BlePacketizer.kt     # 纯 Kotlin 分包器（无 Android 依赖，可单测）
│   └── BleState.kt          # BleDevice / BleConnectionState / SendState
└── ui/
    ├── MainScreen.kt        # Compose UI
    └── theme/Theme.kt       # Material3 主题
```

## 2. 状态机速查

### 连接状态 `BleConnectionState`

```
Disconnected → Connecting → Connected ⇄ Disconnecting → Disconnected
                   └──────────────┘（失败/断连回落）
```

### 发送状态 `SendState`

```
Idle → Sending → Success
              → Failed(reason)   # 写失败/未连接/服务未就绪/发送中/空文本
              → Cancelled        # cancelSend() 后
```

UI 依据这两个 sealed interface 渲染按钮与提示，改动状态机时保持两者一一对应。

## 3. 常见扩展点

### 3.1 修改单包大小 / MTU

- `BleManager.DESIRED_MTU`（默认 247）→ 协商目标。
- `currentMaxPayload()`：`(negotiatedMtu - 3) - 5`，`coerceAtMost(240)` 与固件 ACL 上限一致。
- 若固件侧调大重组缓冲区/ACL，可同步放宽 240 上限（需改 `BlePacketizer.MAX_DATA_PAYLOAD`）。

### 3.2 增加帧类型

1. `BlePacketizer`：新增 `TYPE_*` 常量与构造方法（参考 `cancel()`）。
2. `Packet.describe()`：补充日志描述。
3. 固件侧 `packet_parser.c` 同步处理新 TYPE，两端协议号需对齐。

### 3.3 扫描过滤 / 广播名

- 默认只显示 `PICO-W-KEYBOARD`（`DEVICE_NAME_FILTER`），UI 有"显示全部设备"开关
  （`showAllDevices`）。如需改广播名，两端 `DEVICE_NAME_FILTER` 与固件广播名要一起改。

### 3.4 记住设备的存储

`MainViewModel` 用 SharedPreferences（`pico_w_prefs`）存 `last_device_address/name`。
改为多设备记忆时需引入列表结构并兼容旧 key。

## 4. 调试技巧

- `adb logcat -s BleManager`：扫描/连接/MTU/写回调全程有 Log.i/e。
- 测试模式：UI 有 UTF-8 Hex 预览（`MainViewModel.utf8Hex()`），Pico W 未到货时可核对
  编码与分包是否正确。
- 收不到扫描结果：先开 `showAllDevices` 确认广播名，再确认手机蓝牙/定位（若系统要求）。
- 中途想看分包序列：在 `sendText()` 中对 `packets` 打日志，或临时调用
  `Packet.describe()`。

## 5. 已知限制

- 无链路层重传/ACK：写失败即中止本次消息。
- 单条文本受固件 2048 B 重组缓冲区限制，超长需分段发送。
- 仅单设备连接，未做设备多连/切换后台策略。

---
