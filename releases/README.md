# 版本发布约定

从 **v10** 开始，每个版本号递增（v10 → v11 → v12 → …），方便回滚。

## 规则（每次发布新版本时遵守）

1. **版本号递增**：当前最新号 +1。今天最新是 `v12`，下次就是 `v13`。
2. **产物归档**：把当次构建的产物复制到 `releases/vNN/` 下，命名带版本号：
   - `releases/vNN/PicoBleHidKeyboard-vNN.apk`（必有）
   - `releases/vNN/pico_ble_hid_keyboard-vNN.uf2`（仅 Pico W 固件方案需要；v12 起手机方案不再需要固件）
3. **发布说明**：每个版本目录写 `RELEASE_NOTES.md`，记录日期、功能变化、产物 SHA256、回滚方法。
4. **代码打 tag**：发布完成后打同名 git tag：
   ```powershell
   git tag vNN
   git push origin vNN   # 如推送到远程
   ```
5. **更新 LATEST 指针**：`releases/LATEST` 文件内容改为最新版本号（如 `v11`）。
6. **最新副本**：仓库根目录 `PicoBleHidKeyboard-debug.apk` 与 `firmware/pico_ble_hid_keyboard.uf2` 始终是“最新版”，方便直接取用。

## 回滚方法

- **代码回滚**（整个项目回到某版本）：
  ```powershell
  git checkout v10
  ```
  改完后想回到最新：`git checkout master`
- **只回滚产物**：直接用对应 `releases/vNN/` 里的 APK 装手机、UF2 刷 Pico W。

## 版本历史

| 版本 | 日期 | 说明 |
|---|---|---|
| v10 | 2026-08-25 | Alt+X 中文输入模式（默认）+ 完整文档与构建产物（Pico W） |
| v11 | 2026-08-26 | 输入速度调速 + 常用语句 + 界面重排（Pico W） |
| v12 | 2026-08-26 | 手机直接模拟蓝牙键盘，不再需要 Pico W 固件 |
