# 继续开发交接（2026-08-28 更新）

> 本文件是给“下一台电脑上的 ChatGPT/Codex”读的交接说明：
> 克隆仓库后先读本文件 + `README.md` + `docs/HISTORY.md`，再按 `docs/ONBOARDING.md` 检查环境，即可接着开发。
> 家用电脑环境：ChatGPT + CC Switch + DeepSeek API（提供方配置见 `docs/MIGRATION.md`，密钥需从旧电脑拷贝）。

## 一、当前状态（重要）

- 分支：`phone-keyboard`（默认分支，当前产品线）
- 版本：**v2.0 Beta（开发版）**（versionCode 10 / versionName "2.0-beta"；自 v1.5 起定名 2.0 Beta，标志功能基本完善）
  v1.3、v1.4 已正式发布归档：`releases/v1.3/`、`releases/v1.4/` + git tag `v1.3` `v1.4`，`releases/LATEST` = v1.4
- 应用：包名 `com.hidble.phonekeyboard`，应用名“手机蓝牙键盘”，minSdk 28 / targetSdk 34
- 2026-08-28 已合入真机 Bug 修复轮（见下文“2.6 真机 Bug 修复”），版本号当时仍为 1.4
- 2026-08-28 晚新增 **v1.5**：大模型对话「提示词预设下拉」（详见下文“最新一轮”）
- 2026-09-02 新增：**大模型流式输出 + 对话历史持久化**（详见下文“最新一轮”）
- 2026-09-02 第二轮：**蓝牙连接保活（前台服务）+ 各档提速 10% + 提示词长按编辑**（详见下文“最新一轮”）
- 2026-09-02 第四轮：**真机验证完成，未发现新问题**（详见下文“最新一轮”）
- 仓库根目录 `PhoneBluetoothKeyboard-debug.apk` 是 2026-09-02 最新编译产物
  （本机调试签名 SHA-256 开头 `042c0c23...`）
- **输入模式（重要，两台电脑不同）**：本机（家用电脑）测试时目标机用 **Alt+X 模式**；
  另一台电脑（今早开发的那台）的目标机用 **GBK 模式**（必须 NumLock 开启）。两边代码相同，仅目标机模式/速度档位不同。

### 开发机构建环境（2026-08-27 实测路径，注意在 D 盘）

- JDK 17：`D:\jdk17\jdk-17.0.20+8`（每次编译前设 `JAVA_HOME=D:\jdk17\jdk-17.0.20+8`）
- Android SDK：`D:\Android`（设 `ANDROID_HOME=D:\Android`、`ANDROID_SDK_ROOT=D:\Android`；platforms=android-34，build-tools 33.0.1 + 34.0.0）
- Pico SDK：`C:\pico\pico-sdk`（仅旧 `master` 产品线编译固件需要，本机未验证）
- 编译 APK：
  ```powershell
  $env:JAVA_HOME="D:\jdk17\jdk-17.0.20+8"; $env:ANDROID_HOME="D:\Android"; $env:ANDROID_SDK_ROOT="D:\Android"
  cd android; .\gradlew.bat assembleDebug
  ```
- 产物：`android/app/build/outputs/apk/debug/app-debug.apk`，复制到仓库根 `PhoneBluetoothKeyboard-debug.apk`

### 签名注意事项（踩过坑）

- 手机上之前装的是“旧电脑调试签名”的包；新电脑重新编译后签名不同，安装会提示“签名不同”。
- 已通过「卸载重装」解决；之后在同一台机器上继续编译安装不会再冲突。
- 换新机器后若手机里已装别的签名包，需要卸载重装（或把旧机器 `~/.android/debug.keystore` 拷过来）。

## 最新一轮（2026-08-28 晚 · v1.5：提示词预设下拉）

需求：大模型对话卡片「发送给模型」按钮缩短，同一行右侧新增**提示词下拉菜单**；可新建/删除预设提示词，
选中后发送前自动把提示词拼到输入前面（例如「书面化整理」：把与患者的交谈转成正式书面语言，去口语/语气词/多余符号制表符）。

- 实现文件：`MainActivity.kt`（逻辑）、`activity_main.xml`（布局）、新增 `item_llm_prompt.xml` / `spinner_bg.xml` / `ic_arrow_down.xml`
- 下拉项：`无提示词（直接发送）` / 已存预设（按名称显示） / `＋ 新建提示词…` / `✎ 删除提示词…`
- 新建 = 弹窗填名称+内容；删除 = 点选删除；数据存 SharedPreferences（`llm_prompts`，JSON 数组），
  当前选中存 `llm_selected_prompt`，重启保留
- 发送逻辑：`text = 提示词内容 + "\n\n" + 用户输入`；对话框“我：”仍只显示用户原话；命令日志记录“已应用提示词「xxx」”
- 内置示例预设「书面化整理」，默认不选中（保持“无提示词”）
- 下拉带 ▼ 箭头指示（`spinner_bg`），避免看起来像普通输入框
- 版本号升到 v1.5（versionCode 6）；根目录 APK 已更新并推送（commit `612253a`）
- 内置 6 个医疗场景预设（门诊病历草稿/入院记录草稿/SOAP病历/病历规范书面化/患者沟通解释/随访记录），
  符合中国《病历书写基本规范》；按 `llm_prompt_preset_version` 增量补齐，不覆盖用户自定义

> **给另一台电脑的 AI：本机（家用电脑）测试用 Alt+X 模式；另一台电脑（今早那台）目标机用 GBK 模式（NumLock 开启）。**

## 最新一轮（2026-09-02 · 大模型流式输出 + 对话历史持久化）

- **流式输出**（`LlmClient.chatStream` + `MainActivity.sendToLlm`）：
  - 请求体加 `"stream": true`，`Accept: text/event-stream`，逐行读 SSE `data:` 块，
    解析 `choices[0].delta.content` 增量回调；`data: [DONE]` 结束；
  - UI：第一段到达前仍显示“AI 正在思考…”动画，首个增量到达后切到 `AI：` 行并逐字/逐段追加；
  - 流式期间跳过逐字着色与保存（`llmStreaming` 标志），结束后统一 `applyLlmColors()` + 保存，避免卡顿；
  - 设置页“测试连接”仍走原非流式 `chat()`。
- **对话历史持久化**（`LlmPrefs.KEY_HISTORY`，JSON 数组）：
  - 每轮 user/assistant 消息存入 SharedPreferences，重启 App 后 `loadLlmHistory()` 恢复多轮上下文；
  - 最多保留最近 40 条，防止请求体无限增长；「清空」同时删除历史。
- 版本号 versionCode 7 → 8（versionName 仍为 "2.0-beta"），根目录 APK 已更新并推送。
- 实现文件：`LlmClient.kt`、`MainActivity.kt`。

## 最新一轮（2026-09-02 第二轮 · 连接保活 + 提速 + 提示词管理）

用户真机反馈三项，均已修复：

1. **切子页面/切后台蓝牙立刻断线（重要）**
   - 根因：Android 官方文档明确规定——BluetoothHidDevice 注册的 App **如果不是前台状态，
     系统会自动注销注册并断开连接**。之前只做了“掉线后自动重连”，连接本身仍会被系统断开。
   - 修复：新增前台服务 `HidKeyboardService`（`foregroundServiceType="connectedDevice"`，
     清单加 `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` 权限）。
     键盘注册成功即 `startForegroundService`，保持进程/UID 前台，系统不再注销 HID；
     关闭键盘或主界面销毁（未注册时）才停止服务。通知常驻“手机蓝牙键盘运行中”。
   - 保留自动重连作为兜底。
2. **各档位输入速度提高 10%**
   - `SPEED_SCALES` 延迟系数整体 ×0.9：
     `[4000,3300,2700,2100,1600,1150,780,500,260,80]` →
     `[3600,2970,2430,1890,1440,1035,702,450,234,72]`。
   - 注意：`MIN_DELAY_MS=10`（蓝牙物理下限）不动，约 7 档以上会触底，实际速度以链路为准。
3. **提示词管理：只能添加/删除 + 长按编辑**
   - 下拉项“✎ 删除提示词…”改为“✎ 管理提示词…”；
   - 管理弹窗 = 列表：**点按=删除（二次确认），长按=编辑**（新建/编辑共用一个表单，编辑原位更新）；
   - 选择仍只在主下拉里进行，不可编辑。

- 版本号 versionCode 8 → 9（versionName 仍为 "2.0-beta"），根目录 APK 已更新并推送。
- 实现文件：`HidKeyboardService.kt`（新增）、`AndroidManifest.xml`、`MainActivity.kt`、`TypingEngine.kt`。

## 最新一轮（2026-09-02 第三轮 · 修复流式回复串入大量 "null"）

- 现象：发送给大模型后，回复正文前后出现一长串 `null`。
- 根因：`JSONObject.optString("content")` 对 JSON `null`（角色切换/思考阶段/结束标记等无正文增量块）
  会返回字面量字符串 `"null"`，被当成正文逐块拼进输出。
- 修复（`LlmClient.kt`）：流式与非流式都改用 `opt("content")` + 类型判断，
  只接受真正的非空字符串内容，空/null 增量一律跳过；思考阶段只显示“AI 正在思考…”动画，不产生 null。
- 版本号 versionCode 9 → 10（versionName 仍为 "2.0-beta"），根目录 APK 已更新并推送。

## 最新一轮（2026-09-02 第四轮 · 真机验证结论）

- 用户在本机（家用电脑，目标机 Alt+X 模式）完成真机验证：流式回复无 null、连接保活（切页面/切后台不断连）、
  各档提速、提示词管理（点按删除/长按编辑）均正常。
- **未发现新问题**。当前 v2.0 Beta（versionCode 10）为稳定可用状态。
- 待办保持不变：正式发布归档 v1.5/v2.0-beta（若需要）、Token 加密、流式“停止”按钮、
  微软拼音 `vuc` 方案、一键 Shift 切换中英文、长文本/高档位压测。

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
- **发送可中止**：主输入区的「发送到键盘」按钮与「大模型对话」的「发送到键盘」按钮，点击发送后按钮变成「停止」，再点一次即可中止本次发送（输入框内容保留，不自动清空）。
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

### 6. 真机 Bug 修复（2026-08-28，重要）

真机测试反馈三个 bug，均已修复（代码在 phone-keyboard 分支最新提交）：

1. **长文本输入时目标机“删除前面所有内容”并继续输入（GBK 模式复现）**
   - 根因链：GBK 模式用 Alt+小键盘数字码，**目标机 NumLock 未开启时小键盘数字会变成方向键**
     （7=Home、1=End、9=PageUp…）；若再叠加一个“卡住没松开”的 Shift（高速发送丢报告导致），
     就变成 Shift+Home/End/PageUp 把整篇文本选中，后续打字全部覆盖删除。
   - 使能条件：之前速度 8-10 档把报告间隔压到 1-2ms，低于蓝牙链路 ~10ms/份的物理速率，
     报告在手机端堆积溢出后**静默丢失**，丢的正是 Shift/Alt 的“抬起”报告 → 键卡住。
   - 修复（`TypingEngine.kt`）：
     - **`MIN_DELAY_MS` 从 1ms 改为 10ms**：所有报告按蓝牙物理速率发送，不再堆积、不再丢报告。
       速度 8-10 档现在就是真实物理上限（之前“快”只是把报告堆在缓冲里最后丢掉，目标机并没更快）；
     - **NumLock 检测**：通过键盘 LED 输出报告读取目标机 NumLock（bit0）/CapsLock（bit1）；
       GBK 模式下 NumLock 未开启时回调 `onGbkNumLockWarning`（每次发送最多提示一次），
       MainActivity 会写日志提示“请先按一次 NumLock”；
     - **`releaseAll()`**：每次发送前/发送后/中止/中断时发一份全释放报告，清掉目标端残留按键；
     - 分段保持 40 字/40ms。
2. **切换页面或程序后蓝牙频繁掉线**
   - 修复（`HidDeviceManager.kt` + `AndroidManifest.xml`）：
     - 掉线自动重连：`lastHost` 记录最近主机，`STATE_DISCONNECTED` 后最多自动重连 3 次（间隔 2s）；
     - 回到前台（`MainActivity.onResume`）主动 `reconnectIfNeeded()`；
     - HID 系统服务重启（`onServiceDisconnected`）后自动重新 init + 注册；
     - 所有 Activity 加 `configChanges`，旋转屏幕不再销毁重建（避免注册被 cleanup 清掉）；
     - 重连成功连接建立后先发一份空报告清键盘状态。
3. **已配对设备点击“连接”没反应**
   - 修复：`connect()` 未注册时自动先 `register()`，服务未就绪自动重试（最多约 3 秒）；
     `ConnectionActivity` 点击后立即 Toast“正在连接…”，主界面未运行也明确提示。
   - 现在连接流程：开启键盘开关 →（自动注册）→ 连接电脑；配对后多数电脑会自动连接，掉线会自动恢复。

## 三、已知限制 / 待办

- [x] 正式发布 v1.3 / v1.4（2026-08-28 已完成：`releases/v1.3/`、`releases/v1.4/` + `releases/LATEST`=v1.4 + git tag `v1.3` `v1.4`）。
- [ ] 正式发布 v1.5（若需要）：按 `releases/README.md` 归档 `releases/v1.5/`（APK + RELEASE_NOTES.md）、更新 `releases/LATEST`=v1.5、打 git tag `v1.5`。
- [x] 真机复测（2026-09-02）：连接保活 / 各档提速 / 提示词管理 / 流式 null 修复均完成真机验证，未发现新问题。
      **不要为了“提速”再把 MIN_DELAY_MS 调回 10ms 以下**——那是丢报告/卡键/误全选删除的根源。
- [ ] 大模型：Token 换 EncryptedSharedPreferences（个人自用可暂缓）；流式可加“停止”按钮；
      各家用模型名可能更新，需在设置页改。
- [ ] 历史规划（`HISTORY.md` 待办）：微软拼音 `vuc` 方案、一键 Shift 切换中英文等，尚未实现。

## 四、给“家用电脑 ChatGPT”的提示

1. 先读：本文件、`README.md`、`docs/HISTORY.md`、`docs/MIGRATION.md`、`docs/ONBOARDING.md`。
2. 克隆后运行 `tools/setup_check.ps1` 检查环境（Windows PowerShell 5.1 / 7 均可）。
3. 编译 APK 需要 JDK 17 + Android SDK；CC Switch 配置从旧电脑拷贝（见 `MIGRATION.md`）。
4. 开始前先 `git pull`，确认在 `phone-keyboard` 分支。

## 五、给下一个 AI 的提示（2026-08-27 收尾时整理，重要）

### 1. 代码架构速览（改代码前先看）

- `MainActivity.kt` 是唯一持有 `HidDeviceManager` + `HidProtocol`（打字引擎）的地方；
  三个二级页（`KeysActivity` 更多按键 / `ConnectionActivity` 连接管理 / `LogActivity` 命令日志）
  都通过 `MainActivity.instance`（companion 单例，onCreate 赋值/onDestroy 清空）转发操作：
  - `performHid { it.xxx() }`：按键页发按键（发送期间自动 `FLAG_KEEP_SCREEN_ON`）
  - `setKeyboardEnabled(true/false)`、`connectToDevice(device)`、`getBondedDevices()`：连接页用
  - `connectionActivity` 字段：连接页 onResume 挂接、onPause 摘除，主界面状态变化时 `refreshAllState()` 会同步刷新它
- `LogStore.kt`：跨页面共享命令日志（最多 200 条），`LogActivity` 订阅 `listener` 实时刷新
- `ScrollableEditText.kt`：内容溢出时优先内部滚动、不让外层 ScrollView 抢手势（用于主输入框和 LLM 两个文本框）
- `TypingEngine.kt`：中文输入的四种模式都在这里；`Mutex` 串行发送，`send()` 返回 false 会自动重试 50 次

### 2. Alt+X 中文输入机制（花了大半天踩坑，务必理解）

- Word/记事本的 Alt+X 会把光标前**所有相邻的 0-9A-F 字符**连起来读成一个码点（不是固定 4 位），
  前面有数字/字母时会合并成超长无效码点 → 不转换/乱码（这就是“日期后面中文必乱”的原因）。
- 官方消歧办法：**`U+` 前缀**（如打 `U+5E74` 再 Alt+X → 年，`U+` 会被应用吃掉）；
  本机 Win11 记事本已实测有效。**`x` 前缀是 ASCII 专用**（`x20`→空格），不要用来输中文。
- 另一种官方办法是**先选中十六进制**再 Alt+X（实现复杂、慢，未采用）。
- 随机乱码（`U+` 残留、丢数字）原因：最后一位数字抬起后仅 1ms 就按 Alt+X，宿主来不及写入文档；
  蓝牙链路偶尔丢报告。已修复：`ALT_PRE_MS=100` 在按 Alt+X/松开 Alt 前停顿（随速度缩放）。
- 速度档位 `SPEED_SCALES=[4000,3300,2700,2100,1600,1150,780,500,260,80]`（1 最慢最稳、10 最快）。
- **目标电脑输入法最好处于英文模式**（中文输入法会拦截按键导致类似乱码）。

### 3. 本机验证 Alt+X 的技巧（如果还要测）

- 本机 Win11 有记事本。用 PowerShell `SendKeys` 模拟按键时，**必须先把微软拼音切到英文**：
  ```powershell
  Add-Type -AssemblyName System.Windows.Forms
  # 用 keybd_event 发右侧 Shift（VK=0xA1）切换输入法
  $sig='[DllImport("user32.dll")] public static extern void keybd_event(byte,byte,uint,UIntPtr);'
  Add-Type -MemberDefinition $sig -Name W -Namespace N -PassThru
  ```
  然后开记事本 → 发按键 → `^a` `^c` 读剪贴板验证。
- 真机验证建议：手机连电脑实际打字，1 档起逐步升档，确认各速度下中文无乱码。

### 4. GBK 模式与“长文本删光内容”的结论（2026-08-28，重要，改代码前必读）

- **目标电脑中文输入用的是 GBK 模式**（Alt+小键盘十进制 GBK 机内码）。
- GBK 输入**必须目标机 NumLock 开启**：NumLock 关时小键盘数字键变成方向键
  （7=Home、1=End、9=PageUp、4=Left、6=Right…），叠加残留 Shift 就会全选文本 → 打字覆盖删除。
- **报告发送速率不能超过蓝牙链路物理速率**（约 10ms/份）。低于此值，报告会在手机端缓冲里堆积，
  溢出后静默丢失；丢失“抬起”报告 = 修饰键/按键卡住，是各种玄学问题的总根源
  （乱码、丢字、误全选删除、Alt 卡住跨字累积数字等）。
- 已落地约束：`TypingEngine.MIN_DELAY_MS = 10`（全报告最小间隔）、`releaseAll()` 前后清理、
  NumLock LED 检测 + GBK 警告回调、掉线自动重连、Activity 防重建。
- 若再遇到“输入变乱/被删”，先检查：①目标机 NumLock；②目标机输入法是否英文模式；
  ③速度是否 ≤7 档；④日志里有没有“发送中断”或“NumLock 未开启”提示。

### 4. 待办（下次继续做的方向）

- ~~正式发布 v1.3 + v1.4~~（已完成，2026-08-28）。
- ~~大模型流式输出~~（已完成，2026-09-02）；~~对话历史持久化~~（已完成，2026-09-02）。
- 正式发布 v1.5 / v2.0-beta（若需要）：归档 `releases/v1.5/`（或按 2.0 定名）+ 更新 `releases/LATEST` + 打 git tag。
- 大模型：Token 换 EncryptedSharedPreferences（个人自用可暂缓）；流式可加“停止”按钮；家用模型名可能更新。
- 历史规划：微软拼音 `vuc` 方案、一键 Shift 切换中英文（见 `HISTORY.md`）。
- 真机压测：GBK/十六进制模式 8-10 档正确率；长文本发送时中止按钮是否即时。

### 5. Git 习惯

- 直接在本分支 `phone-keyboard` 上提交并 `git push origin phone-keyboard`（与历史一致）。
- 提交信息用中文、带 `feat(v1.x)` / `fix(v1.x)` 前缀；每次改完都重新编译 + 更新根目录 APK。
- 版本号规则见 `releases/README.md`（当前开发版定名 **v2.0 Beta**，versionName "2.0-beta"）。
