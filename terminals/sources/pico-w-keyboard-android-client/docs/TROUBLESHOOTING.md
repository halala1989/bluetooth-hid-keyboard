# 常见问题排障（Troubleshooting）

## 1. 扫描不到 PICO-W-KEYBOARD

| 可能原因 | 排查 |
|---|---|
| Pico W 未上电/未在广播 | 确认固件已烧录并运行（蓝灯/串口日志） |
| 广播名不匹配 | 打开"显示全部设备"开关，核对实际广播名 |
| 系统要求定位权限 | 部分厂商强制位置开关影响 BLE 扫描，需开启 |
| 蓝牙权限未授予 | 检查"附近的设备"权限；Android 12+ 需 `BLUETOOTH_SCAN` |

## 2. 能扫描但连接失败

| 可能原因 | 排查 |
|---|---|
| 设备已离开范围 | 靠近后重试 |
| 固件侧连接数满/状态异常 | 复位 Pico W 后重连 |
| 上一次连接未正常关闭 | App 内先断开，再重连 |

## 3. 连接成功但发送失败

| 错误提示 | 原因 |
|---|---|
| "未找到目标 Service" | 连接的设备不是本项目的 Pico W 固件，或 GATT 表不一致 |
| "未找到 TX Characteristic" | 同上，UUID 需与固件 `.gatt` 严格一致 |
| "写入失败 status=xxx" | 链路断开或写被拒；看 `adb logcat -s BleManager` 的 status 码 |
| "上一消息仍在发送" | 等上一消息完成或先点"取消" |
| "文本为空" | 输入非空内容 |

## 4. 发送成功但 Windows 没打字

1. 确认 Pico W USB 已插入 Windows 并被枚举为键盘（设备管理器"键盘"类）。
2. 确认固件收到完整消息才注入：发送短文本（<15B 单帧）验证链路。
3. 当前固件仅支持 ASCII 可打印字符；中文等多字节字符会被跳过，属预期行为。
4. 若文本超长（>2048B）被固件拒绝，缩短后分段发送。

## 5. 中文/Emoji 无法注入

这是固件侧限制（`usb_hid.c` 仅做 ASCII → HID 映射，多字节 UTF-8 跳过），
App 已按 UTF-8 完整传输。需在固件侧实现 Unicode → HID 键盘映射（Alt Code 或输入法）。

## 6. App 崩溃 / ANR

- 编译期 BLE 权限在 Manifest 已按版本拆分；运行时权限被拒会 Toast 提示而非崩溃。
- 网络/Gradle 问题见 docs/BUILD.md。
- 若在 `onCharacteristicWrite` 后出现异常，抓 `adb logcat` 堆栈定位。

## 7. CI 构建失败

| 现象 | 处理 |
|---|---|
| 依赖下载超时 | 重跑一次，`setup-gradle` 缓存生效后稳定 |
| `if-no-files-found: error` | `assembleDebug` 失败或路径变化，看日志定位编译错误 |
| JDK 版本 | 工作流固定 Temurin 17，本地需 JDK 17 |

---
