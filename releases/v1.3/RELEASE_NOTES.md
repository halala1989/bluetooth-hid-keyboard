# v1.3（2026-08-27）

> 大模型接入（DeepSeek / 小米 MiMo / 火山引擎豆包）+ 中文输入提速 + 长文本自动分段。
> 分支：`phone-keyboard`　git tag：`v1.3`

## 产物

| 文件 | SHA256 |
|---|---|
| PhoneBluetoothKeyboard-v1.3.apk | 83E91B0DB9018EABB3E0BB3582A7AF1D8FCF7705DD480D741DF2641CCD4751C2 |

## 新增内容

- **大模型接入**：新增 `LlmClient.kt`（OpenAI 兼容 Chat Completions，零依赖，HttpURLConnection）；
  模型设置二级页（下拉选提供方 → 自动填模型 → 只填 API Token → 测试连接 → 保存自动返回）；
  主界面大模型对话卡片（提问、发送给模型、可编辑输出、发送到键盘、清空）。
  提供方预设：DeepSeek（deepseek-v4-flash）、小米 MiMo（mimo-v2.5）、火山引擎豆包（doubao-seed-1-8-251228）。
- **中文输入提速**：Alt 与第一位数字合并为同一份报告、数字抬起后不再额外等待；
  速度曲线更激进（默认 5 档每字约 71ms，8 档约 17ms，10 档约 7ms）。
- **长文本自动分段**：每 80 字暂停 20ms 让蓝牙缓冲消化积压报告；报告发送失败自动重试；
  连接中断时停止发送并提示。

## 使用提示

- v1.3 之后（2026-08-28）有一轮真机 Bug 修复（GBK 长文本误删 / 掉线自动重连 / 配对点击无反应），
  已合入 **v1.4**，正式使用建议直接用 v1.4。

## 回滚

```powershell
git checkout v1.2
```
