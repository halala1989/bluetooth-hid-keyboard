# v1.0（2026-08-26）

> 独立产品线：**手机直接模拟蓝牙键盘**，不依赖 Pico W。
> 分支：`phone-keyboard`　git tag：`v1.0`

## 产物

| 文件 | SHA256 |
|---|---|
| PhoneBluetoothKeyboard-v1.0.apk | 5489DC37988B9F9062B50983C0007BE27A36C80846D07FA33E891B67AC2A66F6 |

## 应用标识（与旧版互不覆盖）

- 包名（applicationId）：`com.hidble.phonekeyboard`（旧 Pico W 版为 `com.hidble.keyboard`，可同时安装）
- 应用名：手机蓝牙键盘
- 版本：versionCode 1 / versionName "1.0"；最低 Android 9（API 28）

## 功能

- 手机注册为标准蓝牙键盘（BluetoothHidDevice），电脑蓝牙直接配对打字。
- 中文输入 4 种模式：Alt+X（默认）/ 十六进制 / 十进制 / GBK（逻辑 1:1 移植自旧固件）。
- 输入速度调速 1-10、常用语句保存、界面布局与旧版一致（输入置顶，连接区置底）。

## 使用方法

1. 手机安装 APK，开启蓝牙。
2. 点底部“启动键盘”，状态变为“键盘已启动”。
3. 电脑：设置 → 蓝牙 → 添加设备 → 选择“手机蓝牙键盘”并配对。
4. 回到 App，点底部“已配对设备”里的电脑连接（多数电脑配对后自动连接）。
5. 顶部输入文字点发送；速度/常用语/中文模式用法与旧版相同。

## 回滚

```powershell
git checkout v1.0    # 当前分支内任意版本
git checkout master  # 切回 Pico W 产品线（v10/v11）
```
