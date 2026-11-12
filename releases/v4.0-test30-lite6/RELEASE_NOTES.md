# 全终端 Lite 键盘 v4.0-test30-lite6

- applicationId：`com.hidble.carekeyboard.lite`
- versionCode：`40029`
- 版本名称：`4.0-test30-lite6`
- APK：`全终端Lite键盘-v4.0-test30-lite6.apk`
- APK SHA-256：`DBE04F20BD3DF1F6CFD21F31358ABAEFE354E22EDE45591E0F227F705983D554`
- 固件 SHA-256：`7EE804080A73F85CF4AE701CCCED4D156CA2D4CBE7B5213B675D792E60AD5B62`

## 本次更新

- ESP32-S3 Bridge 固件加入完整 Unicode→GBK 码表。
- GBK 汉字使用 Alt + 小键盘 GBK 十进制机内码输出。
- 自动检查电脑回报的 NumLock LED 状态。
- NumLock 关闭时自动模拟 NumLock 按键并等待主机确认。
- 已成功编译、烧录并校验 Super Mini 新固件。
- 烧录后的开发板已重新识别为 ESP32-S3 USB HID 键盘。
- App 内置固件已替换为新版，后续可直接从 App 导出或重复烧录。

## 实机烧录信息

- 板型：ESP32-S3 Super Mini
- MAC：`28:84:85:6D:74:50`
- 固件 SHA-256：`7EE804080A73F85CF4AE701CCCED4D156CA2D4CBE7B5213B675D792E60AD5B62`
- APK 中同时包含该固件文件和源码。

## 已知边界

- 仍需在 GBK 模式的真实汉字输入场景中验证目标软件是否接受 Alt 机内码。
- YD-ESP32-S3 N16R8 的源码已同步，但本次只编译并烧录了 Super Mini。
