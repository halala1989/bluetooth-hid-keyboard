# 全终端 Lite 键盘 v4.0-test30-lite5

- applicationId：`com.hidble.carekeyboard.lite`
- versionCode：`40029`
- 版本名称：`4.0-test30-lite5`
- APK：`全终端Lite键盘-v4.0-test30-lite5.apk`
- SHA-256：`9C2B2D5CEE59A4DEA9A254C1A19EEB35D56AF53A7F2A86ED38EA72A942EAC020`

## 本次更新

- GBK 和 Alt 十六进制输入前自动检查 NumLock。
- ESP32-S3 Bridge 固件读取 USB HID 主机返回的 LED 状态。
- 如果 NumLock 关闭，固件自动模拟按一次 NumLock，并等待主机确认开启。
- 手机蓝牙 HID 的 GBK 模式也会自动切换 NumLock。
- 如果主机始终不返回 LED 状态，固件只在第一次输入时尝试切换，避免每个汉字反复切换。
- 用户不再需要每次手动按 NumLock。

## 重要说明

开发板 NumLock 自动开启属于固件行为。仅安装 APK 不会更新已烧录的 Bridge 固件，仍需使用 ESP-IDF 编译并烧录新版固件。

构建方式见 `docs/ESP32_GBK_FIX.md`。

## 已知边界

- 少数电脑或 HID 驱动不会返回 NumLock LED 状态，此时固件会采用“首次自动切换后视为开启”的兼容策略。
- 目标软件仍需接受 GBK/CP936 Alt 机内码输入。
- 当前电脑没有 ESP-IDF，未生成新的固件二进制。
