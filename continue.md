# 医键住院管理：跨电脑继续开发指南

本文档用于在新的电脑上继续开发 **医键住院管理 v4.0-test18**。开始前请先确认仓库分支为 `phone-keyboard`，不要使用旧版的 `master` 分支替代当前产品。

- GitHub：<https://github.com/halala1989/bluetooth-hid-keyboard>
- 当前分支：`phone-keyboard`
- App 名称：`医键住院管理`
- applicationId：`com.hidble.carekeyboard`
- 当前版本：`4.0-test18`
- versionCode：`40017`

## 1. 新电脑首次准备

需要安装：

1. Git。
2. JDK 17。
3. Android SDK Platform 34、Build Tools 和 Platform Tools（含 `adb`）。
4. Android Studio（推荐）或只使用命令行 Gradle。

Windows 下建议安装 Android Studio，并由它安装 Android SDK。默认 SDK 路径通常是：

```text
C:\Users\<用户名>\AppData\Local\Android\Sdk
```

本文示例仍沿用原开发电脑的路径 `C:\Android\Sdk`；如果你的路径不同，构建前请把 `ANDROID_HOME` 改成自己的实际路径。

## 2. 获取代码

```powershell
git clone --branch phone-keyboard https://github.com/halala1989/bluetooth-hid-keyboard.git
cd bluetooth-hid-keyboard
git pull --ff-only
git status --short --branch
```

正常状态应显示在 `phone-keyboard` 分支，并且没有未解决的冲突。

如果以后继续在另一台电脑开发，开始工作前先执行：

```powershell
git pull
```

完成修改后再执行：

```powershell
git add -A
git commit -m "描述本次修改"
git push origin phone-keyboard
```

## 3. 构建 APK

在仓库根目录打开 PowerShell：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="C:\Android\Sdk"
cd android
.\gradlew.bat assembleDebug --no-daemon
```

构建成功后生成：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

当前正式测试包也放在：

```text
医键住院管理-v4.0-test18.apk
releases/v4.0-test18/医键住院管理-v4.0-test18.apk
```

如果你修改了功能，应重新构建并替换测试版 APK，避免源码与安装包不一致。

## 4. 安装到手机

手机开启“开发者选项”和“USB 调试”，连接电脑并允许调试，然后执行：

```powershell
adb devices
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

也可以把 APK 发送到手机后直接点击安装。新版包名与旧版手机键盘不同，可以和旧版同时安装。

## 5. 当前功能范围

### 患者与主界面

- 首页是患者列表，右下角“+”新增姓名、性别、年龄。
- 患者详情只保留姓名、性别、年龄。
- “书写病历”是大尺寸入口；已生成并保存的文书显示在详情页下方。
- 首页“下午好，医生”右侧的齿轮是“书写规范”，用于导入、启用和删除 Markdown Skill。

### 病历工作台

- 手机竖屏使用上下布局：上方是自然语言资料输入，下方是文书输出。
- 每条左侧资料有时间戳，生成时按时间顺序理解病情变化。
- 点击历史资料可修改内容，同时保留原记录时间。
- 每次生成都可以追加一段自然语言资料；模型会结合完整时间线和本次补充资料判断哪些内容相关。
- 所有开放文书都必须调用大模型生成，没有内置示范病例或模板兜底。
- 文书生成后会持久化到当前患者，退出并重新进入仍可读取。
- 已实现的文书入口包括入院病程记录、首次病程记录、普通病程记录、手术同意书、术前小结、术前讨论、出院小结和疑难病例讨论；“其他”暂未展开。
- 手术同意书从自然语言中提取任意数量的推荐方案和备选方案，不固定为三个。
- 生成时可调整该文书的补充提示词和 Markdown Skill。

### 医疗文本规则

- 不输出“待补充”“未提供”“需补充”“不详”等占位语句。
- 未提供的常规既往史按阴性整理；医生明确提供的舌脉、中医病名和证型具有最高优先级。
- 入院记录与首次病程应结构化、可执行，并符合我国住院病历书写规范和中医病历要求。
- 中医诊断依据、中医鉴别诊断必须使用中医辨证语言；西医诊断依据和鉴别诊断单独书写。
- 专科查体只写西医查体内容，不重复生成舌象、脉象。
- 首次病程必须包含诊疗计划。
- 入院记录和首次病程默认允许联网检索公开资料，用于完善鉴别诊断和诊疗计划。
- 这些规则已经写入提示词与 Skill 应用逻辑，但不是医疗器械级临床验证。正式用于真实患者前仍需医院制度、账号权限、审计、数据安全与临床验收。

### 模型设置

- 当前只开放 DeepSeek 和小米 MiMo。
- 在统一设置页填写 API Token 和模型名称。
- 保存非空 Token 前会先测试连通性；测试失败时不应覆盖原有有效 Token。
- 不要把真实 API Token 写入源码、提交记录、截图或本文档。

### 终端与输出

- 设置页选择手机蓝牙 HID、Pico W、ESP32-S3 或兼容外接板。
- 选择终端后，状态灯旁的按钮由用户主动发起连接。
- 手机蓝牙模式记住已配对电脑，并由手机主动连接电脑。
- 外接板模式通过 BLE 数据通道连接板子，再由板子通过 USB HID 或蓝牙 HID 向目标电脑输出。
- 输出过程中显示进度，可暂停、继续或停止；停止后再次输出会从头开始。
- 输出速度共 10 档。第 8 档是保守稳定上限，第 9、10 档刻意突破延时保护，可能因目标电脑或终端处理不过来而丢字。
- 当前会话没有连接真实 Android 手机、Pico W、ESP32 或目标电脑，因此只能确认代码和构建通过，不能声称硬件链路已经再次实测。

## 6. 关键源码位置

```text
android/app/src/main/java/com/hidble/phonekeyboard/
├─ PatientListActivity.kt          # 患者列表与新增患者
├─ PatientDetailActivity.kt        # 患者详情与文书回显
├─ MedicalRecordWorkspaceActivity.kt
│                                  # 竖屏病历工作台、生成、保存和输出控制
├─ PatientRepository.kt            # 患者和文书数据
├─ GeneratedDocumentStore.kt       # 生成文书的持久化同步
├─ WritingSkillStore.kt            # Markdown Skill 存储
├─ WritingRulesActivity.kt         # 书写规范管理
├─ LlmClient.kt                    # DeepSeek / MiMo 接口与连通性测试
├─ TerminalSettingsActivity.kt     # 终端、配对、连接和模型设置
├─ TerminalRegistry.kt             # 可选终端定义
├─ OutputRouter.kt                 # 按当前终端路由真实输出
├─ BoardBleManager.kt              # Pico/ESP32 外接板 BLE 客户端
├─ HidDeviceManager.kt             # 手机向电脑的蓝牙 HID 注册
├─ HidKeyboardService.kt           # 共享 HID 会话
└─ TypingEngine.kt                 # 按键速率、暂停和字符编码
```

相关目录：

```text
android/app/src/main/assets/medical-record-workspace.html
                                     # 工作台页面与前端交互
design-prototype/                    # 历史 UI 原型和截图
docs/INPATIENT_APP.md                # 产品实现与安全边界
docs/MIGRATION.md                    # 通用迁移说明
terminals/catalog.json               # 全部终端版本清单
terminals/sources/                   # 已归档终端和固件源码
releases/v4.0-test18/                # 当前测试版 APK 与发布说明
```

## 7. 继续开发时的优先检查

修改后至少完成以下检查：

1. 搜索并删除冲突标记：

```powershell
rg -n "^(<<<<<<<|=======|>>>>>>>)" .
```

2. 构建 APK：

```powershell
cd android
.\gradlew.bat assembleDebug --no-daemon
```

3. 核对 APK 身份：

```powershell
& "$env:ANDROID_HOME\build-tools\34.0.0\aapt.exe" dump badging `
  "app\build\outputs\apk\debug\app-debug.apk" |
  Select-String "package:|application-label:"
```

应看到：

```text
package: name='com.hidble.carekeyboard' versionCode='40017' versionName='4.0-test18'
application-label:'医键住院管理'
```

4. 真机测试：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat
```

重点回归以下路径：

- 新增患者后返回再进入，数据仍然存在。
- 生成入院病程记录和首次病程记录后退出工作台，再进入仍能看到已保存内容。
- 未配置 Token 或网络失败时明确报错，不展示示范病例。
- 手机蓝牙已配对电脑后，可由手机主动连接并真实输入文字。
- Pico/ESP32 连接后，暂停、继续、停止和重新开始均作用于真实输出任务。
- 第 8 档稳定；第 9、10 档的速度与丢字情况在目标电脑上实测后记录。

## 8. 已知限制

- 当前是本地工作流测试版，不是生产级医疗系统。
- 患者数据只保存在 Android App 私有存储中，尚未提供多设备同步、医院账号体系、角色权限和集中审计。
- 不同电脑、Windows 输入法、记事本/Word/浏览器对 Unicode 按键模式的支持不同；真实输出前应先用短句验证。
- 蓝牙 HID、外接板重连和固件协议需要实机回归；构建通过不等于全部硬件已验证。
- 大模型输出必须由医生审核。软件不能替代诊断、处方或知情同意过程。

## 9. 推荐的下一步

1. 在真实 Android 手机上完整走通患者新增、资料录入、模型生成、保存回显和再次进入恢复。
2. 用 DeepSeek 和 MiMo 各测试一次 Token 连通性及长文书生成。
3. 分别实测手机蓝牙 HID、Pico W 和 ESP32-S3 的真实文字输出。
4. 针对第 9、10 档输出速度记录不同终端的丢字率，再决定是否保留“高速不稳定”提示。
5. 正式部署前补齐登录权限、审计日志、患者数据备份与删除、传输加密、医院信息安全审批和临床验收。
