# 从 GitHub 迁移到另一台电脑

> 目标：在新电脑上输入仓库地址 `git clone` 即可拿到全部代码、文档、发布归档，并继续开发。
> 仓库：https://github.com/halala1989/bluetooth-hid-keyboard

## 第一步：克隆代码（新电脑）

```powershell
git clone https://github.com/halala1989/bluetooth-hid-keyboard.git
cd bluetooth-hid-keyboard
git branch -a          # 看到 master 和 phone-keyboard 两个分支
git checkout phone-keyboard   # 当前产品线（手机蓝牙键盘，v1.x）
```

- 仓库默认分支是 `phone-keyboard`，克隆后就在该分支。
- 要 Pico W 旧产品线：`git checkout master`（v10/v11，需要 Pico W 固件）。
- 所有发布归档（APK/固件/说明）都在 `releases/` 目录里。

## 第二步：准备开发/使用环境

### 2.1 ChatGPT / Codex 环境（用 CC Switch，无官方账号）

> ⚠️ 密钥不在本仓库里（安全考虑）。CC Switch 的供应商/API Key 配置需要从旧电脑拷贝。

1. 旧电脑上把整个文件夹拷到新电脑相同位置：
   ```
   旧电脑:  C:\Users\<旧用户名>\.cc-switch
   新电脑:  C:\Users\<新用户名>\.cc-switch
   ```
   里面 `cc-switch.db`（供应商/密钥）、`settings.json`、`model-pricing.json` 都要带。
2. 拷贝 Codex 配置（CC Switch 写出的）：
   ```
   旧电脑:  C:\Users\<旧用户名>\.codex\config.toml
            C:\Users\<旧用户名>\.codex\auth.json
            C:\Users\<旧用户名>\.codex\cc-switch-model-catalog.json
   新电脑:  C:\Users\<新用户名>\.codex\   （没有就新建）
   ```
3. 新电脑安装 CC Switch（安装包在旧电脑 `Downloads\CC-Switch-v3.20.0-Windows.msi`，或到官方发布页下载）。
4. 启动 CC Switch → 选供应商/账号 → 启动 ChatGPT/Codex，验证能对话。
5. 若 `config.toml` 里有写死的绝对路径（`D:\` 或 `C:\Users\<旧用户名>\`），按新电脑路径改。

### 2.2 对话历史 / 技能 / 插件（可选，想在新电脑看到之前的任务记录）

退出 Codex App 后，把旧电脑 `C:\Users\<旧用户名>\.codex\` 下的这些拷到新电脑同目录：
`thread_history_1.sqlite`、`logs_2.sqlite`、`state_5.sqlite`、`queue_1.sqlite`、`memories_1.sqlite`、
`session_index.jsonl`、`sessions\`、`skills\`、`plugins\`。

### 2.3 构建工具链（要继续编译 APK / 固件才需要）

| 工具 | 旧电脑位置（大小） | 新电脑处理 |
|---|---|---|
| JDK 17 | D:\jdk17（≈485MB） | 整个拷过去，或重新下载，设 `JAVA_HOME=D:\jdk17\jdk-17.0.20+8` |
| Android SDK | D:\Android（≈673MB） | 整个拷过去，或装 Android Studio 自动下载，设 `ANDROID_HOME=D:\Android` |
| Pico SDK（仅固件） | D:\pico（≈1.9GB） | 整个拷过去，或 `git clone -b master https://github.com/raspberrypi/pico-sdk.git`，设 `PICO_SDK_PATH` |

编译命令：

```powershell
# APK（phone-keyboard 分支）
cd android
.\gradlew.bat assembleDebug

# Pico 固件（仅 master 旧产品线需要）
.\build_firmware.ps1
```

## 安全提醒

- `.cc-switch\cc-switch.db` 与 `.codex\auth.json` 里是 API Key：拷贝走 U 盘/加密压缩包，别明文发网盘/聊天工具。
- 新电脑可用后，旧电脑上的密钥文件请妥善保管或删除。
