# 继续开发交接（2026-08-27）

> 本文件是给“下一台电脑上的 ChatGPT/Codex”读的交接说明：
> 克隆仓库后先读本文件 + `README.md` + `docs/HISTORY.md`，再按 `docs/ONBOARDING.md` 检查环境，即可接着开发。
> 家用电脑环境：ChatGPT + CC Switch + DeepSeek API（提供方配置见 `docs/MIGRATION.md`，密钥需从旧电脑拷贝）。

## 一、当前状态（重要）

- 分支：`phone-keyboard`（默认分支，当前产品线）
- 版本：**v1.4**（versionCode 5 / versionName "1.4"；v1.3、v1.4 均尚未走正式发布归档流程）
- 应用：包名 `com.hidble.phonekeyboard`，应用名“手机蓝牙键盘”，minSdk 28 / targetSdk 34
- 仓库根目录 `PhoneBluetoothKeyboard-debug.apk` 是 2026-08-27 v1.4（UI 美化版）最新编译产物
  （本机调试签名 SHA-256 开头 `042c0c23...`）

### 开发机构建环境（已自检通过）

- JDK 17：`C:\Program Files\Java\jdk-17`（`JAVA_HOME` 已设置）
- Android SDK：`C:\Android\Sdk`（`ANDROID_HOME` 已设置；build-tools 33.0.1 + 35.0.0）
- Pico SDK：`C:\pico\pico-sdk`（仅旧 `master` 产品线编译固件需要）
- 编译 APK：`cd android; .\gradlew.bat assembleDebug`

### 签名注意事项（踩过坑）

- 手机上之前装的是“旧电脑调试签名”的包；新电脑重新编译后签名不同，安装会提示“签名不同”。
- 已通过「卸载重装」解决；之后在同一台机器上继续编译安装不会再冲突。
- 换新机器后若手机里已装别的签名包，需要卸载重装（或把旧机器 `~/.android/debug.keystore` 拷过来）。

## 二、本次开发会话内容（2026-08-27 对话整理）

### 1. GBK / 中文输入提速

- 分析：GBK 每个汉字要打 4-5 位 Alt 码数字，原来每个数字都要等“按下+抬起”两次延迟，
  加上蓝牙链路与 Windows 逐位解析，是慢的主因。
- 实现（`TypingEngine.kt`）：
  - Alt 与第一位数字合并为同一份报告（GBK/十进制/十六进制每字省 1 个报告）；
  - 数字抬起后不再额外等待（下一键的按下延迟负责节奏）；
  - 速度曲线整体更激进：`SPEED_SCALES = [2000,1550,1200,900,650,450,300,180,100,60]`，最小间隔 1ms；
  - Alt 收尾延迟 40ms，字符间隔 0ms。
- 应用侧每字延迟：默认 5 档约 71ms，8 档约 17ms，10 档约 7ms（英文/数字 1-9ms）。
- 物理下限提醒：速度 8+ 后瓶颈是蓝牙链路传输 + Windows 解析 Alt 码（每字约 100ms），App 内无法再压。

### 2. 长文本自动分段（防输入中断）

- 背景：单次文本没有硬性字数上限，但高速长文本会撑爆蓝牙发送缓冲导致丢字/中断。
- 实现（`TypingEngine.kt` + `MainActivity.kt`）：
  - 每 80 字暂停 20ms，让蓝牙缓冲消化积压报告；
  - 报告发送失败自动重试（20ms 间隔，最多约 1 秒），自动贴合链路实际速度；
  - 连接中断时停止发送并提示“发送中断：蓝牙连接可能已断开（已发送 X 字）”。

### 3. 大模型接入（类 CC Switch：选提供方 + 填 Token）

- 新增 `LlmClient.kt`：OpenAI 兼容 Chat Completions 客户端（HttpURLConnection，无新增依赖）。
- 提供方预设（`LlmProviders`）只有三个：**DeepSeek**（`deepseek-v4-flash`）、
  **小米 MiMo**（`mimo-v2.5`）、**火山引擎豆包**（`doubao-seed-1-8-251228`，
  地址 `https://ark.cn-beijing.volces.com/api/v3`）。
- 模型设置二级页面（`LlmSettingsActivity`）：下拉选提供方 → 模型自动填预设 → 只填 API Token →
  测试连接 → 保存自动返回。主界面右上角新增常驻“模型设置”按钮。
- 大模型对话卡片：提问输入框、“发送给模型”、可编辑输出框（显示“我/AI”对话）、“发送到键盘”、“清空”。
- 配置存 `SharedPreferences`（`hidble_prefs`）；主界面 `onResume` 重新读取配置，
  已修复“设置页保存后主界面仍提示未填 Token”的问题。
- 注意：API Token 明文存在本地，个人自用可接受；需要更安全可换 EncryptedSharedPreferences。

### 4. 版本与文档

- 版本号递增到 v1.3（versionCode 4 / versionName "1.3"）。
- 修复 `tools/setup_check.ps1` 编码问题（加 UTF-8 BOM），Windows PowerShell 5.1 也能运行。
- `README.md` 当前版本描述更新为 v1.4。


### 5. 界面美化（v1.4，2026-08-27）

- 新应用图标（自适应图标矢量重绘）：手机 → 蓝牙 → 键盘，深色渐变背景 + 青色点缀，
  与 App 暗色主题一致（`ic_launcher_foreground.xml` + `ic_launcher_background.xml`）。
- **发送 HID 时阻止锁屏**：`sendText` / `sendOutputToKeyboard` / 按键页 HID 动作执行期间
  设置 `FLAG_KEEP_SCREEN_ON`，发送完自动释放（清单已加 `WAKE_LOCK` 权限）。
- 主界面大瘦身：
  - 光标控制、组合键、功能键 → 新二级页「更多按键」（`KeysActivity`，经 `MainActivity.performHid` 转发）；
  - 模拟蓝牙键盘开关 + 已配对设备 + 状态 → 新二级页「连接管理」（`ConnectionActivity`，状态实时同步）；
  - 命令日志 → 新二级页「命令日志」（`LogActivity` + 共享 `LogStore`，最多保留 200 条）；
  - 主界面只留：输入/发送、常用语、速度滑块、中文模式下拉、大模型对话、二级页入口。
- 中文输入模式改为**下拉菜单**（`Spinner`）：选中项左侧显示绿色 “✓”（自定义 adapter）。
- 输入速度改为**横向滑块**（`SeekBar` 1-10 独占一行）：拖动即生效，取消“应用”按钮，自动保存。
- 大模型对话：
  - 提问输入框放大到 4 行，右侧上下滑块可看长文本；
  - 输出框右侧滚动条常显（`fadeScrollbars=false`）；
  - “我：”发言默认绿色、“AI：”回复默认白色（`Spannable` 着色，编辑后自动重刷）；
  - “发送到键盘”默认**不发送“我：”的发言**，可勾选“包含我的发言”后全部发送；
  - **AI 思考状态**：请求发出后输出框上方显示“AI 正在思考…”动画（点号循环），回复/失败后隐藏；
  - **文本框优先内部滚动**：`llmInput` / `llmOutput` 改为 `ScrollableEditText`，内容溢出时滑动文本框优先滚动内容，不带动整个页面。
- **顶部状态条可点击**：点击主界面顶部的连接状态（含红色“未启动”文字，整条可点）直接进入「连接管理」二级页。
- **主输入框改为 4 行**：主界面文本输入框放大到 4 行多行输入（`ScrollableEditText`），右侧滚动条常显，可上下滚动查看长文本。
- **随机乱码进一步加固（重要）**：加了 `U+` 前缀后仍偶发随机乱码（如 `U+7E`、丢字 `53EF→ϯ`），
  原因是最后一位数字刚抬起就按 Alt+X（间隙仅 1ms，与速度无关），宿主应用还没把数字写进文档就触发转换，
  加上蓝牙链路偶尔丢报告。修复：新增 `ALT_PRE_MS=100` 的“按 Alt+X 前停顿”（随速度缩放），
  并对 HEX/GBK/十进制模式在松开 Alt 前同样加停顿；速度档位重排为
  `[4000,3300,2700,2100,1600,1150,780,500,260,80]`——最低档更慢更稳、档间差值拉开。
- **修复 Alt+X 中文乱码（重要）**：Word/记事本会把光标前**所有相邻十六进制字符**读成一个码点，
  前面有数字/字母时（如日期 `2025`+`5E74`）会被合并成超长无效码点导致不转换/乱码。
  修复：每个中文字先打 `U+` 前缀再打十六进制码再 Alt+X（微软官方消歧写法，转换时被应用吃掉）。
  已在 Win11 记事本实测：`2025年8月27日`、`可靠程度：可靠`、`伤后24小时内局部冷敷` 均正确。

## 三、已知限制 / 待办

- [ ] 正式发布 v1.3 / v1.4：按 `releases/README.md` 归档 `releases/v1.3/`、`releases/v1.4/`（APK + RELEASE_NOTES.md）、
      更新 `releases/LATEST`、打 git tag `v1.3`（根目录最新副本已更新）。
- [ ] 真机验证提速后的正确率：GBK 模式 8-10 档在个别手机/Windows 组合上可能丢位，需实测；
      优先推荐支持 Alt+X 的应用（记事本/Word）。
- [ ] 大模型：当前非流式（等完整回复）；对话历史仅本次运行期有效；各家用模型名可能更新，需在设置页改。
- [ ] 历史规划（`HISTORY.md` 待办）：微软拼音 `vuc` 方案、一键 Shift 切换中英文等，尚未实现。

## 四、给“家用电脑 ChatGPT”的提示

1. 先读：本文件、`README.md`、`docs/HISTORY.md`、`docs/MIGRATION.md`、`docs/ONBOARDING.md`。
2. 克隆后运行 `tools/setup_check.ps1` 检查环境（Windows PowerShell 5.1 / 7 均可）。
3. 编译 APK 需要 JDK 17 + Android SDK；CC Switch 配置从旧电脑拷贝（见 `MIGRATION.md`）。
4. 开始前先 `git pull`，确认在 `phone-keyboard` 分支。

