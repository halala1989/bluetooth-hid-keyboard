# v11（2026-08-26）

> 今日版本：输入速度调速 + 常用语句 + 界面重排。
> git tag: `v11`

## 产物

| 文件 | SHA256 |
|---|---|
| PicoBleHidKeyboard-v11.apk | 69307354C4229BFDA3B126FB6D2F52C4307D8AAEAB7263EE5C0D7A7836041DD3 |
| pico_ble_hid_keyboard-v11.uf2 | D62411A43DA300302725F74045888ED3D7BFC458B88E10823A10B8FC8D0743DC |

## 相比 v10 的变化

1. **输入速度调速**：固件新增 `SPEED` 命令（1=最慢 … 10=最快，默认 5）；Android 输入区新增“速度”文本框 + “应用”按钮，自动保存，连接后自动下发。
2. **常用语句**：Android 输入区新增“常用语”按钮，支持添加/编辑/删除并自动保存，点选插入输入框。
3. **界面重排**：文本输入（含发送）移到最顶部；扫描设备/断开连接/发现设备移到最底部。
4. 应用版本号对齐：versionCode 11 / versionName "11.0"。

> ⚠️ 调速功能依赖新固件：需先刷 `pico_ble_hid_keyboard-v11.uf2`，否则旧固件不认识 SPEED 命令（会回 ERR:INVALID_CMD，不影响使用但调速不生效）。

## 回滚方法

```powershell
git checkout v10          # 代码整体回滚到 v10
# 或只回滚产物：装回 releases/v10/ 里的 APK，刷回 releases/v10/ 里的 UF2
```
