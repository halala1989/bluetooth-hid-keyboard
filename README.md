# 手机蓝牙键盘（Android → 蓝牙 HID → 电脑）

> **v3.0 Beta：住院全流程管理**
> App 首页现已改为患者列表，支持基本信息、病例文书、草稿保存和一键蓝牙 HID 输出。原键盘功能保留在「键盘工具」中。
> 实现与安全边界见 [`docs/INPATIENT_APP.md`](docs/INPATIENT_APP.md)。

> **v3.2 Beta：统一终端与蓝牙配对**
> 手机蓝牙、Pico W、ESP32-S3 Super Mini、YD-ESP32-S3 N16R8 和旧 ESP32 BLE HID 已汇总到一个 App，通过设置页手动切换。全部源码与来源版本见 [`terminals/README.md`](terminals/README.md)。

> **v4.0 Test：病历工作台测试版**
> 患者详情中的“书写病历”会打开双栏病历工作台，包含资料输入、全部文书类型、生成前提示词设置、手术同意书、术前小结、术前讨论及暂停/继续/停止输出界面。

> **v4.0 Test2：竖屏修复**
> 病历工作台改为竖屏显示，输入资料与病例输出上下排列并同屏可见；顶部返回按钮会真正回到病人详情。

> **v4.0 Test3：主动连接终端**
> 选择输出终端后，状态灯右侧显示主动连接按钮；手机蓝牙进入电脑配对菜单，外接板优先连接已保存地址或开始扫描。

> **v4.0 Test4：主动连接已配对电脑**
> 手机成功配对并连接过电脑后会记住该电脑；再次点击“主动连接”由手机发起蓝牙 HID 连接，不再要求电脑重新连接手机。

> **v4.0 Test5：真实模型生成进度**
> 病历生成改为调用已配置的 DeepSeek/MiMo，显示生成进度，成功后返回绿色“生成完成，可以开始输出”；缺少 Token 或接口失败时明确报错，不再回退示范病例。

> **v4.0 Test6：模型连接检查**
> API Token 输入后可手动测试；保存非空 Token 时会先验证当前 DeepSeek/MiMo 模型是否可连接，验证成功后才写入设置。

> **v4.0 Test7：病历持久化与真实输出**
> 生成结果按患者保存在本机，重新进入工作台会自动恢复；输出按钮已接入原生手机 HID 或外接板 BLE 输出，暂停/继续/停止控制真实输出任务。

> **v4.0 Test8：双倍输出速度**
> 各档延时约为旧版一半，第 8 档仍是稳定上限；第 9、10 档突破保护下限，最高速度约为旧版两倍，可能因链路丢字。

> **v4.0 Test9：医疗文本纠错与中医分栏**
> 生成前自动纠正常见语音同音字、错别字和断句；中医鉴别诊断/辨证论治与西医依据严格分栏，首次病程支持联网检索公开指南和病例作为鉴别参考。

> **v4.0 Test10：Markdown Skill 导入**
> 每种病历生成前均可导入 Markdown Skill 作为本次书写规范；Skill 会随文书配置一同保存并发送给模型，指导栏目结构和写作方式。

> **v4.0 Test11：仅保留 Skill 导入**
> 移除误加的 Excel 导入入口，每个病历模块只提供 Markdown Skill 导入。

> **v4.0 Test12：主界面全局书写规范**
> “下午好，医生”右侧新增齿轮按钮“书写规范”；Markdown Skill 改为在主界面统一导入和管理，并自动应用到全部病历模块。

> **v4.0 Test13：患者页精简与文书回显**
> 患者基本信息只保留姓名、性别、年龄；“书写病历”按钮放大；生成文书同步到原生加密摘要存储并显示在患者详情下方。手术同意书只要求医生录入三个方案名称。

> **v4.0 Test14：自然语言补充与动态手术方案**
> 所有文书生成先合并左侧资料和本次补充自然语言；手术同意书不再手动填写方案，模型从自然语言中提取任意数量的推荐及备选方案，再逐项生成优缺点和风险。

> **v4.0 Test15：病程时间线与历史资料回编**
> 左侧每条自然语言资料带时间戳，生成时按时间顺序分析病情变化；点击历史资料可以修改正文并保留原记录时间。

> **v4.0 Test16：模型筛选相关病程**
> 全部文书必须调用大模型。模型读取完整时间线资料池，按当前文书目的选择相关记录，结合本次补充自然语言资料生成；未配置模型时不使用模板兜底。

> **v4.0 Test17：病历内容纠偏**
> 未提及内容直接省略，不输出任何缺失占位句；既往史无阳性信息时写阴性表述；专科查体只写西医内容；首次病程必须生成诊疗计划。

> **v4.0 Test18：中医辨证与诊疗计划增强**
> 入院记录和首次病程按阴性补全常规背景项目，并默认启用联网检索；医生给出的舌脉、中医诊断和证型作为权威依据，用于完善中医鉴别、诊断依据和可执行诊疗计划。

> **应用身份：医键住院管理**
> 新版 applicationId 为 `com.hidble.carekeyboard`，可与旧版手机蓝牙键盘同时安装，不共享旧版应用数据。

> 📦 **GitHub 仓库**：https://github.com/halala1989/bluetooth-hid-keyboard
> 🌿 **两条产品线**：`phone-keyboard`（默认分支，手机蓝牙键盘 v1.x）｜ `master`（Pico W 方案 v10/v11）
> 🔁 **换电脑迁移**：`git clone https://github.com/halala1989/bluetooth-hid-keyboard.git` 后见 `docs/MIGRATION.md`；
> 📖 开发历史/对话记录整理见 `docs/HISTORY.md`

## 🚀 零代码迁移（懒人专用：交给新电脑的 ChatGPT）

不想自己敲命令？直接把仓库交给新电脑上的 ChatGPT，让它读指南、自己克隆：

1. 复制 `docs/ONBOARDING.md` 里那段现成指令，粘贴给新电脑的 ChatGPT；
2. ChatGPT 会自动克隆本仓库、读 `docs/MIGRATION.md`、跑 `tools/setup_check.ps1` 检查环境；
3. 涉及密钥的部分（CC Switch 配置）它只负责提示你从旧电脑拷贝。

> 完整执行细节见 `docs/MIGRATION.md`；给 ChatGPT 的现成话术见 `docs/ONBOARDING.md`。

**2.0 Beta（开发版）**（分支 `phone-keyboard`）：手机直接把自己注册成标准蓝牙键盘
（Android 9+ 系统的 `BluetoothHidDevice`），电脑蓝牙里添加“手机蓝牙键盘”配对后即可打字，**不依赖 Pico W**。
App 里的中文输入、速度、常用语等体验沿袭旧版。

```
Android App  --Bluetooth HID Keyboard-->  Windows PC
```

目标电脑端**不需要安装任何程序、驱动或服务**，也不读取/监控屏幕。

> 本应用与旧版互不覆盖：包名 `com.hidble.phonekeyboard`、应用名“手机蓝牙键盘”，可和旧 Pico W 版（`com.hidble.keyboard`）同时安装。
> 旧 Pico W 产品线在 `master` 分支（v10/v11），固件源码保留在 `pico_firmware/`，仅作参考。

## 中文/Unicode 输入方式（重要）

蓝牙键盘只能发送标准按键，不能直接发送 Unicode/汉字，因此 App 在手机端模拟 Windows 内置的几种 Alt 码输入方式
（逻辑原样移植自 v11 固件）。支持 4 种模式，App 里可一键切换：

| 模式 | 原理 | 适用 |
|---|---|---|
| **Alt+X（默认）** | 输入十六进制码点后按 `Alt+X` 转换 | **Win11 记事本**、写字板、Word、OneNote、Outlook 等 RichEdit 应用；**无需注册表、无需 NumLock** |
| 十六进制 | `Alt + 小键盘+ + 十六进制码` | 浏览器/聊天等大多数应用；需 `EnableHexNumpad` 注册表 + NumLock；**Win11 记事本无效**（小键盘 + 会被记事本拦截） |
| 十进制 | `Alt + 0 + 十进制码点` | RichEdit 应用；无需注册表 |
| GBK | `Alt + 小键盘十进制 GBK 机内码` | 仅中文版 Windows |

> **验证结果（Pico W 方案时验证）**：Win11 自带记事本 + Alt+X 模式可正常输入中文；ChatGPT 等浏览器对话框不支持 Alt+X（非 RichEdit），需在浏览器里切换“十六进制”模式（需注册表+NumLock）。

开启十六进制模式所需注册表（可选，只影响十六进制模式）：

```powershell
reg add "HKCU\Control Panel\Input Method" /v EnableHexNumpad /t REG_SZ /d 1 /f
```

这不是安装程序，只是打开 Windows 自带的 Unicode 十六进制输入功能。修改后需注销/重启一次。

## 目录结构

```
android/                # Android Kotlin 客户端（本版本主体）
  ├─ MainActivity.kt    # 界面与逻辑（输入/速度/常用语/连接）
  ├─ HidDeviceManager.kt# 手机注册为蓝牙键盘（BluetoothHidDevice）
  ├─ TypingEngine.kt    # 文本/按键/中文 Alt 码输入引擎（1:1 移植自 v11 固件）
  └─ HidProtocol.kt     # 上层封装
pico_firmware/          # 历史 Pico W 固件（v10/v11 方案，已不再需要）
firmware/               # 历史预编译固件 UF2（已不再需要）
releases/               # 版本化发布归档（本产品线 v1.0, v1.1, ...）
docs/                   # 协议、设置、工作进度（LLM_PROVIDERS.md = 大模型提供方手册；CONTINUE_DEV.md = 开发交接）
tools/                  # 构建/校验脚本
```

## 快速使用

### 1. 安装 Android App

用 Android Studio 打开 `android/` 连接手机编译安装；或直接用 `releases/v1.4/PhoneBluetoothKeyboard-v1.4.apk`（要求 Android 9+）：

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 输出路径：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### 2. 连接电脑（一键开关）

1. 打开本 App，点底部「**模拟蓝牙键盘**」开关。
2. 系统弹出“开启蓝牙”授权，点允许；随后弹出“**对附近设备可见**”，也点允许（否则电脑搜不到）。
3. 电脑：设置 → 蓝牙 → 添加设备，搜索“**手机蓝牙键盘**”并配对（手机上确认配对码）。
4. 首次配对后，若电脑未自动连接，可点底部“已配对设备”里的电脑手动连接。关闭开关即可停止。

### 3. 输入

1. 顶部文本框输入汉字/英文/数字，点发送；中文输入模式默认 Alt+X（记事本/Word 等用），浏览器等切“十六进制”或“GBK”。
2. **输入速度**：文本框下方“速度”填 `1-10`（数字越大越快，默认 5），点“应用”立即生效并自动保存。
   - 新版每档延时系数约为旧版一半；第 8 档保持稳定限速，第 9、10 档突破保护下限，最高约为旧版两倍，可能因蓝牙链路丢字。
3. **常用语句**：点“常用语”可添加/编辑/删除常用语句（自动保存），点选一条插入输入框，再点发送。

## 从源码构建

- APK：见上方 `cd android; .\gradlew.bat assembleDebug`。
- Pico W 固件（仅 v10/v11 历史方案需要）：`build_firmware.ps1`，详见 `releases/v10|v11` 发布说明。

## 版本与回滚

本产品线从 **v1.0** 开始递增归档（当前 **v2.0 Beta 开发版**）：

- 产物：`releases/vNN/`（APK + 发布说明），最新版另存于仓库根目录 `PhoneBluetoothKeyboard-debug.apk`。
- 代码：每次发布打同名 git tag（`git tag v1.0`、`git tag v1.1`、`git tag v1.2` …）。
- 回滚：`git checkout v1.0` 回到本产品线旧版；切 `git checkout master` 回到 Pico W 产品线（v10/v11）。
- 详细约定见 `releases/README.md`。

## License

MIT
