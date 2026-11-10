# 医键住院管理 v4.0-test24

- applicationId：`com.hidble.carekeyboard`
- versionCode：`40023`
- 版本名称：`4.0-test24`
- APK：`医键住院管理-v4.0-test24.apk`
- SHA-256：`184B5B7D641410D27F8B0F74ADF67477E19FB9B2D97167A310E686C369E91E37`

## 本次更新

- 修复首页“今日病区概览 → 书写规范”按钮点击后闪退。
- 书写规范页面现在会安全读取旧版本留下的模型与 Skill 配置，不再因配置类型不一致而中断。
- 页面初始化或 Skill 渲染遇到异常时会显示恢复页面，并尽量继续展示当前 Markdown Skill。
- 保持原有功能：导入、清空、预览和调用 AI 修改书写 Skill。

## 验证

- Android Debug APK 构建通过。
- 当前电脑未连接 Android 手机，尚未执行真机点击回归；安装本包后需重新验证首页入口。
