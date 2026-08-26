# v10（2026-08-25）

> 昨日版本：Alt+X 中文输入模式（默认）+ 完整文档与构建产物。
> git tag: `v10`（commit `fc592cf`）

## 产物

| 文件 | SHA256 |
|---|---|
| PicoBleHidKeyboard-v10.apk | B6E9A74E289D68FD31BB2190670BFBC77E7A8B4452643D6761020EEA91E6E16B |
| pico_ble_hid_keyboard-v10.uf2 | 1F0A29CF088F714B6195C50518AB0F32DF10EC1F1CEBBFE8A61A361724CA5051 |

## 功能

- BLE GATT 透传 + USB HID 键盘（Pico W）
- 中文/Unicode 输入 4 种模式（UMOD）：Alt+X（默认）/ 十六进制 / 十进制 / GBK
- Android 客户端：深色 UI、扫描/连接/输入、中文输入模式切换

## 回滚方法

```powershell
git checkout v10          # 代码回滚到 v10
# 或用本目录下的 APK / UF2 直接装回去
```
