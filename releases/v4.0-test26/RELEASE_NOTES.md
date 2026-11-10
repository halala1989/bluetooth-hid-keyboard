# 医键住院管理 v4.0-test26

- applicationId：`com.hidble.carekeyboard`
- versionCode：`40025`
- 版本名称：`4.0-test26`
- APK：`医键住院管理-v4.0-test26.apk`
- SHA-256：`602F87150E8C6644879409412F59DC2B8789B409465E475315E68DBF0754175B`

## 本次更新

- 修复书写规范页面出现“Binary XML file line #265”布局恢复提示的问题。
- 将页面内外两层嵌套滚动控件替换为系统原生 `ScrollView` 和可滚动 `TextView`。
- 保留长 Skill 文本的独立上下滚动预览。
- 恢复页面增加“复制错误详情”，后续若仍有设备兼容问题可直接复制完整异常。
- 保留 Test25 的多 Skill 导入、导出、单选和多选组合功能。

## 验证

- Android Debug APK 构建通过。
- 当前电脑未连接 Android 手机，尚未执行真机布局回归。
