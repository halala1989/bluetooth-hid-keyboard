# 大模型提供方手册（手机蓝牙键盘 · 内置 LLM）

> 供“下一台电脑上的 ChatGPT/Codex”开发时查阅：要改模型设置、新增/排查提供方，先读本文件。
> 主流程说明与历史见 `README.md`、`docs/CONTINUE_DEV.md`、`docs/HISTORY.md`。

## 1. 功能入口与工作方式

- App 主界面“大模型对话”卡片：提问输入框 → “发送给模型”（流式 SSE 回复，逐字显示）→ 可编辑输出框 →
  “发送到键盘”（把对话内容打到电脑）；另有“新对话”按钮与历史对话下拉。
- 主界面右上角“模型设置”二级页：下拉选提供方 → 自动填预设模型名与 API 地址 → 只填 API Token →
  “测试连接”（非流式）→ 保存自动返回。
- 配置存 `SharedPreferences`（`hidble_prefs`，键见 `LlmPrefs`）；Token 目前明文本地保存（个人自用）。
- 请求格式：OpenAI 兼容 Chat Completions；纯文本 content 用字符串，带图片/音频附件时用 content 数组
  （图片 `image_url` data-URI；音频 `input_audio`，小米 MiMo 原生支持）。

## 2. 四个提供方速查表（截至 2026-09-05，versionCode 14）

| 显示名（下拉） | id | Base URL（接口自动补 `/chat/completions`） | 默认模型 | Key 格式 | 说明 |
|---|---|---|---|---|---|
| DeepSeek | `deepseek` | `https://api.deepseek.com` | `deepseek-v4-flash` | `sk-…` | 最省心通用 |
| 小米 MiMo | `mimo` | `https://api.xiaomimimo.com/v1` | `mimo-v2.5` | `sk-…` | 原生支持本地音频 `input_audio` |
| 火山引擎（豆包） | `volcano` | `https://ark.cn-beijing.volces.com/api/v3` | `doubao-seed-1-8-251228` | `ark-…`（普通方舟 Key） | 标准方舟 OpenAI 兼容端点 |
| 火山 AI Hub（Agent Plan） | `volcano-agent-plan` | `https://ark.cn-beijing.volces.com/api/plan/v3` | `ark-code-latest` | `ark-…`（Agent Plan 专用 Key） | Agent Plan 套餐专属端点；默认官方聚合模型 `ark-code-latest`（2026-09-05 更新） |

代码位置：`android/app/src/main/java/com/hidble/phonekeyboard/LlmClient.kt` 的 `LlmProviders.list`。

## 3. 火山 AI Hub / Agent Plan（重点，容易踩坑）

### 3.1 结论（多来源交叉核实 + 2026-09-05 官方文档复核）

- **“Agent-Plan-Small”是套餐档位（Small / Medium / Large / Max），不是模型名。**
  把 `Agent-Plan-Small` 填进 `model` 字段会报模型不存在/400。
- Agent Plan 使用**专属 OpenAI 兼容端点**：
  - Base URL：`https://ark.cn-beijing.volces.com/api/plan/v3`
  - 实际接口：`https://ark.cn-beijing.volces.com/api/plan/v3/chat/completions`
  - 鉴权：`Authorization: Bearer <Agent Plan 专用 API Key>`
- **推荐模型名：`ark-code-latest`**（2026-09-05 官方文档复核确认）：多模型聚合入口，
  填它会自动选择套餐内最合适的子模型，最省心；作为本 App 该提供方的默认模型。
- 也可填**套餐内具体模型名**强制指定，例如：
  `doubao-seed-2.0-mini` / `doubao-seed-2.0-lite` / `doubao-seed-2.0-pro` /
  `glm-5.2` / `kimi-k2.6` / `deepseek-v4-pro` 等（以火山方舟控制台/官方文档为准）。
- **普通火山方舟 Key（`/api/v3`）与 Agent Plan Key 不通用**；Token 填控制台 Agent Plan 专用 Key。
- Agent Plan **不提供 `/models` 列表接口**（实测 404），所以设置页无法自动拉取模型列表，
  只能预设/手动填模型名（这正是 CC Switch 也不能自动拉 Agent Plan 模型的原因）。
- 套餐档位限制：不同档位可用的模型/功能不同（例如部分模型要求 Medium 及以上、Small 不含图像生成等），
  报“模型不可用/无权限”时先确认是否为本套餐支持。

### 3.2 本 App 的预设

- 提供方显示名：“火山 AI Hub（Agent Plan）”，默认模型 **`ark-code-latest`**
  （官方推荐多模型聚合入口，自动选套餐内最合适的子模型；2026-09-05 起默认）。
- 也可手动填套餐内具体模型强制指定（`doubao-seed-2.0-lite` / `doubao-seed-2.0-pro` /
  `glm-5.2` / `kimi-k2.6` / `deepseek-v4-pro` 等）。
- `LlmProvider` 有 `hint` 字段：设置页在模型框下方显示“Agent-Plan-Small 是套餐档位不是模型名，
  请填具体模型名”并列出常用模型；新增其它“套餐型”提供方时可复用该机制。

### 3.3 参考链接

- 官方《Agent Plan 个人版 · 快速开始》（2026-09-05 复核）：
  <https://console.volcengine.com/ark/region:cn-beijing/docs/82379/2373738?lang=zh>
  （模型名可填 `ark-code-latest`；开通管理页切换子模型 3–5 分钟生效）
- 官方《Agent Plan · 其他工具 · OpenAI 兼容接入》（2026-09-05 复核，本 App 属此类）：
  <https://console.volcengine.com/ark/region:cn-beijing/docs/82379/2373746?lang=zh>
  （OpenAI 兼容 Base URL = `https://ark.cn-beijing.volces.com/api/plan/v3`；
  Model 支持 `ark-code-latest` 或具体 Model Name 两种方式）

- 火山方舟官方 Agent/Coding Plan API 参考（入口）：<https://docs.volcengine.com/docs/82379/2407058?lang=zh>
- CC Switch 实测 issue（确认 Base URL、`model = <Model_Name>`、无 `/models` 接口）：
  <https://github.com/farion1231/cc-switch/issues/6566>
- pi-provider-volcengine-agent-plan（Agent Plan 模型清单与档位可用性、协议差异）：
  <https://pi.dev/packages/pi-provider-volcengine-agent-plan>
- CodePick 火山 Coding Plan / Agent Plan 指南：<https://codepick.dev/zh/guides/ark-coding-plan-guide/>

## 4. 多模态（图片/音频）注意事项

- 发送格式：content 数组。图片 `{"type":"image_url","image_url":{"url":"data:<mime>;base64,…"}}`；
  音频 `{"type":"input_audio","input_audio":{"data":"data:<mime>;base64,…"}}`。
- 小米 MiMo：原生支持本地音频（`mimo-v2.5`）。
- 火山 Agent Plan 的 `doubao-seed-2.0-*` 系列中部分模型支持图片输入（控制台/文档标注为准）。
- 普通文本模型不支持图片/音频时会返回 4xx，属**模型能力限制**，不是 App bug；
  错误会写进“命令日志”，真机排查时先看日志。
- 附件原文件只保留在本次运行内存；重启后历史里只剩文件名称文字说明，不再重传原文件。

## 5. 如何新增/修改一个提供方（给后续 AI）

1. 在 `LlmClient.kt` 的 `LlmProviders.list` 增加一项 `LlmProvider(...)`：
   - `id`：英文小写唯一值（已保存的用户配置按 id 匹配，**不要改已有 id**）；
   - `displayName`：下拉显示名；
   - `baseUrl`：**不含** `/chat/completions`（`endpoint()` 会自动拼接）；
   - `defaultModel`：自动预填的模型名，必须真实有效；
   - `hint`：可选，设置页展示的注意事项（套餐型/特殊模型名格式等场景很有用）。
2. 若新提供方支持不同请求格式（如 Responses API、特殊鉴权头），在 `LlmClient.chat()` /
   `chatStream()` 按 `provider.id` 分支处理，不要破坏现有三家的 OpenAI 兼容路径。
3. 涉及界面文字/交互改动：`LlmSettingsActivity.kt`（提供方选择、测试连接、保存）与
   `res/layout/activity_llm_settings.xml`。
4. 改完：`versionCode +1`（`android/app/build.gradle`）→ 编译 → 复制 APK 到仓库根目录 →
   更新 `docs/CONTINUE_DEV.md`、`docs/HISTORY.md` → 中文提交并推送 `phone-keyboard`。

## 6. 排查清单（真机测试报错时）

- 提示“请先填写 API Token / 模型名”：确认在“模型设置”里保存过，主界面 `onResume` 会重新读取。
- 401/403：Token 与提供方不匹配（Agent Plan 必须用 Agent Plan 专用 Key，不是普通方舟 Key）。
- 400 “model not found / not supported”：模型名写错，或该模型不在当前套餐档位内；换套餐内模型名。
- 无回复/流式中断：看“命令日志”里的“模型调用失败”原因；先试“测试连接”（非流式）定位是协议还是模型问题。
- 回复里出现过“null”：已修复（2026-09-02），流式只收真正字符串增量；若再现说明新提供方返回结构不同，需检查 `chatStream` 解析。
- 个别模型对 Chat Completions/SSE 不兼容：社区反馈 Agent Plan 部分模型走 Responses API 更稳；
  先换套餐内其它模型，确有必要再按提供方分支支持。
