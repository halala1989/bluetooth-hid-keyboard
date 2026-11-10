# 医键住院管理 v4.0-test27

- applicationId：`com.hidble.carekeyboard`
- versionCode：`40026`
- 版本名称：`4.0-test27`
- APK：`医键住院管理-v4.0-test27.apk`
- SHA-256：`A6287666BF29D0916C66A201BFCCE3727BA77F871B135C52AE4F8CB6EA80E08A`

## 本次更新

- 修复 ESP32-S3 Bridge 固件启动时阻塞等待 USB HID 挂载的问题。
- 板子只要上电运行 Bridge 固件就会启动 BLE 广播，不再要求先连接目标电脑。
- 安卓端 BLE 扫描器改为每次扫描时重新获取，避免蓝牙后开启后扫描器为空。
- 扫描结果优先读取广播包中的设备名，改进 `ESP32-S3 Bridge` 和 `YD-ESP32-S3 Bridge` 的发现。
- 增加 15 秒 GATT 连接/服务发现超时，并显示具体状态码或缺失服务，不再无限显示“连接中”。
- 连接前关闭旧 GATT，修复重复点击造成的状态残留和 133 类连接失败。
- 只有服务和通知订阅成功后才会标记为已连接并保存板子地址。
- 已保存地址连接失败时自动清除旧地址并重新扫描。
- 扫描结束后恢复“连接”按钮，避免无设备时一直停在扫描状态。
- 设置页新增 ESP32-S3 Bridge 连接说明和固件导出。
- App 内置两套已编译固件：
  - ESP32-S3 Super Mini：`ESP32-S3-SuperMini-bridge-merged.bin`
  - YD-ESP32-S3 N16R8：`YD-ESP32-S3-N16R8-bridge-merged.bin`

## 固件验证

- ESP32-S3 Super Mini Bridge 固件构建通过。
- YD-ESP32-S3 N16R8 Bridge 固件构建通过。
- 两个合并烧录文件均已生成，可由 App 导出。
- 当前电脑未连接 ESP32-S3 实机，本次尚未执行手机-BLE-板子-USB HID 全链路实测。
