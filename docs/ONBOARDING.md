# 给新电脑上 ChatGPT 的指令（零代码迁移）

> 使用者全程不敲命令，把下面这段话复制给新电脑上的 ChatGPT，它会自己执行。
> 也可以直接说：“读取本仓库的 docs/ONBOARDING.md，按里面的步骤把项目拉到本地。”

## 直接复制这段话给新电脑的 ChatGPT：

---
你好，请帮我把这个 GitHub 仓库完整克隆到本电脑（我全程不敲命令，由你执行）：

仓库：https://github.com/halala1989/bluetooth-hid-keyboard

请按以下步骤做，每一步做完简单确认：

1. 先用 `git clone https://github.com/halala1989/bluetooth-hid-keyboard.git` 把仓库克隆到本地，
   目录建议放在 `C:\Users\<当前用户名>\Documents\Projects\bluetooth-hid-keyboard`（没有就创建）。
2. 克隆完成后，进入仓库目录，读取这几个文件并理解项目：
   - `README.md`（项目说明）
   - `docs/MIGRATION.md`（迁移指南）
   - `docs/HISTORY.md`（开发历史）
   - `docs/CONTINUE_DEV.md`（当前开发状态与交接说明，最新）
   - `docs/LLM_PROVIDERS.md`（大模型提供方手册：四个提供方、Agent Plan 接入要点、新增提供方方法）
3. 确认当前在默认分支 `phone-keyboard`；运行 `git branch -a` 和 `git tag`，
   确认 `master` 分支和 `v1.0/v1.1/v1.2/v10/v11` 这些标签都在。
4. 运行仓库里的 `tools/setup_check.ps1` 检查环境（git / JDK / Android SDK 等），
   把输出结果整理成简要报告告诉我；缺什么告诉我怎么补（或直接帮我安装）。
5. 提示我：接下来需要把旧电脑上的 CC Switch 配置（`~/.cc-switch`）和 Codex 配置
   （`~/.codex\config.toml`、`auth.json`）用 U 盘拷到本电脑（这些是密钥，不在仓库里，
   你不要、也不能替我从网上获取）。
6. 全部完成后，用一两句话汇报：仓库克隆在哪个目录、当前在哪个分支、下一步我该做什么。
---

## 说明

- 这份指令对应的执行细节（CC Switch 拷贝、对话历史、构建工具链）都在 `docs/MIGRATION.md`。
- 开发历史 / 来龙去脉见 `docs/HISTORY.md`。
- `tools/setup_check.ps1` 是环境自检脚本，ChatGPT 可以直接运行。
