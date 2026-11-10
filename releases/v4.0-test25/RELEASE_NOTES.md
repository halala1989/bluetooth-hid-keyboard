# 医键住院管理 v4.0-test25

- applicationId：`com.hidble.carekeyboard`
- versionCode：`40024`
- 版本名称：`4.0-test25`
- APK：`医键住院管理-v4.0-test25.apk`
- SHA-256：`EF97DCD8C697A532BECB781C9C05240688C626836C648E7D09CFE937E7122319`

## 本次更新

- 书写规范升级为多 Skill 库，可同时保存多个 Markdown Skill。
- 每个 Skill 可独立启用或停用；勾选多个时，会按列表顺序组合应用于病历生成。
- 点击 Skill 可在当前规则区域单独预览。
- 支持一次导入多个 `.md`、`.markdown`、`.txt` 文件，也支持导入 Skill ZIP 包。
- 单个 Skill 可单独导出为 Markdown；多个已启用 Skill 可导出为 ZIP，并可直接重新导入。
- Skill 可逐项删除，也可以一次删除全部。
- AI 修改只作用于当前预览的 Skill，不会覆盖其他 Skill。
- 旧版本中的单个 Skill 会自动迁移到新 Skill 库。
- 多个已启用 Skill 的组合内容最多发送 48,000 字，超出时会提示并截断。

## 验证

- Android Debug APK 构建通过。
- 当前电脑未连接 Android 手机，尚未执行真机上的多选、导入和导出回归。
