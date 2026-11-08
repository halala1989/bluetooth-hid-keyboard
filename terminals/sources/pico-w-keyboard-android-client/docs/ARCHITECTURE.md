# Android 端架构

## 1. 总览

本 App 是 Pico W 键盘注入器的 **BLE GATT 客户端**，职责单一：把用户输入的任意 UTF-8 文本
按应用层帧协议分包，通过 BLE 可靠地写入 Pico W。**不进行任何 Unicode → HID 转换**，
所有字符转换由 Pico W 固件完成。

```
MainActivity (Compose 入口 + 运行时权限)
   │
   ▼
MainViewModel (状态汇总 / 自动重连 / Hex 预览 / 持久化)
   │
   ▼
BleManager (扫描 / 连接 / MTU 协商 / 串行写队列 / GATT 回调)
   │
   ├── BlePacketizer (START/DATA/END/CANCEL 分包)
   └── BleState (连接状态机 + 发送状态机)
```

## 2. 模块划分

| 模块 | 文件 | 职责 |
|---|---|---|
| 入口 | `MainActivity.kt` | Compose `setContent`、Android 12+ 运行时蓝牙权限、蓝牙开关引导 |
| 视图模型 | `MainViewModel.kt` | 汇总 BleManager 各 StateFlow、自动重连、Hex/字节数预览、记住上次设备 |
| BLE 管理 | `ble/BleManager.kt` | 扫描/连接/MTU 协商/严格串行写队列/回调分发/取消发送 |
| 分包器 | `ble/BlePacketizer.kt` | 纯逻辑：UTF-8 分片，生成 START/DATA/END/CANCEL 帧，无 Android 依赖 |
| 状态模型 | `ble/BleState.kt` | `BleDevice`、`BleConnectionState`、`SendState` 数据类 |
| UI | `ui/MainScreen.kt` | Compose 界面：扫描列表、连接按钮、文本输入、发送/取消、状态展示 |

## 3. 关键设计决策

- **串行写队列**：每帧只有在上一次 `onCharacteristicWrite()` 回调完成后才发送下一帧
  （`writeQueue` 队首即 in-flight 帧）。BLE 的 write 并发会导致乱序，串行牺牲吞吐换取
  顺序与可靠，协议完整消息（含 END）全部落盘才结束。
- **MTU 动态协商**：连接后 `requestMtu(247)`；`currentMaxPayload = negotiatedMtu - 3 - 5`
  （ATT 头 3 字节 + 帧头 5 字节），上限截断到 240 B 与固件 ACL 载荷匹配。
- **文本完整才注入**：固件只在收到 END 且累计字节数 == START 声明的总长度时才注入，
  中途断连不会留下半截文本；Android 端无需关心注入细节。
- **取消语义**：`cancelSend()` 清空写队列并追加一帧 CANCEL；in-flight 写完成后
  队列被 drain，界面置 `Cancelled`，不会误报成功。
- **记住上次设备**：SharedPreferences 存最近成功连接的设备地址，下次启动**只自动重连一次**，
  失败即停止，不做无限重试。

## 4. 数据流

### 4.1 扫描
`startScan(showAllDevices)` → 默认按设备名 `PICO-W-KEYBOARD` 过滤（`ScanFilter`），
`showAllDevices=true` 时不过滤（调试用）。扫描 8 秒后自动停止，结果按地址去重刷新。

### 4.2 发送
```
sendText(text)
 → messageId = (messageId + 1) & 0xFFFF
 → BlePacketizer.packetize(text, messageId, currentMaxPayload())
     = [START(total_len, total_pkts), DATA×N, END]
 → 全部入 writeQueue，_sendState = Sending
 → writeNext(): 取队首帧写 TX characteristic（WRITE_TYPE_DEFAULT）
 → onCharacteristicWrite(GATT_SUCCESS) → 出队 → 队列空则 Success，否则写下一帧
```

### 4.3 取消
```
cancelSend()
 → cancelRequested = true；清空队列；追加 CANCEL 帧
 → onCharacteristicWrite 回调：若队首是 CANCEL → 出队并复位 cancelRequested，不再发成功
```

## 5. 权限模型

| 权限 | 版本 | 说明 |
|---|---|---|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Android 8.0~11（`maxSdkVersion=30`） | 安装时授予的普通权限 |
| `BLUETOOTH_SCAN` | Android 12+ | 运行时权限，`neverForLocation` |
| `BLUETOOTH_CONNECT` | Android 12+ | 运行时权限 |

- 不声明位置权限：扫描使用 `neverForLocation`，App 不利用扫描结果推导位置。
- `uses-feature android.hardware.bluetooth_le required=true`：无 BLE 硬件的设备不可安装。

## 6. 技术栈与约束

| 项 | 值 |
|---|---|
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| Kotlin | 2.0.20 |
| UI | Jetpack Compose（BOM 2024.09.03）+ Material3 |
| Gradle / AGP | 8.9 / 8.5.2 |
| 包名 | `com.example.picowkeyboard` |

---
