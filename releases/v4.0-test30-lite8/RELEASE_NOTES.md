# 全终端 Lite 键盘 v4.0-test30-lite8

- applicationId：`com.hidble.carekeyboard.lite`
- versionCode：`40030`
- 版本名称：`4.0-test30-lite8`
- APK：`全终端Lite键盘-v4.0-test30-lite8.apk`
- SHA-256：`D6472DB2F6D983FBC3645FF9037E881FCD35E9A86F7FB1147B7F35D93EBE33F4`

## 本次更新

- “发送到当前设备”按钮简化为“发送”。
- 输入框下方新增“暂停”“停止”“清空”。
- 暂停后再次点击“发送”从暂停位置继续。
- 停止会完全中断输出，下一次发送从头开始。
- 清空会先停止输出，再清空输入框。
- 手机 HID 与 ESP32-S3 外接板都支持新的发送控制。
- 外接板改为逐字符确认后再发送下一字符，避免缓冲区排队造成暂停/停止延迟。

## 验证

- Android `assembleLite` 构建通过。
- APK 已生成并完成版本校验。
- 当前未连接 Android 手机，尚未完成四按钮真机回归。
