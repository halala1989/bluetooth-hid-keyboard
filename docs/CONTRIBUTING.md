# Contributing · 参与开发

[English](#english) | [简体中文](#简体中文)

## English

### Before opening a change

1. Choose the branch that owns the affected product. UI and patient-workflow changes normally target `medical-record-app-v4`; lite input changes target `lite-input`; suite documentation targets `main`.
2. Keep patient data, model keys, and local machine configuration out of commits.
3. Describe the user workflow and the safety impact of the change.
4. Preserve historical release artifacts and existing branch boundaries.

### Development checks

- Build the Android project with `gradlew.bat assembleDebug` before submitting code.
- Validate changed resource XML files and JavaScript assets.
- Run `git diff --check` and review the final diff.
- Include a real-device test note when Bluetooth HID, pairing, or OTG behavior changes.
- Update both English and Chinese sections when product behavior or safety boundaries change.

### Pull requests

Keep pull requests focused. State the source branch, target branch, changed workflow, verification performed, and remaining device limitations. UI changes should include screenshots using synthetic patient data.

## 简体中文

### 提交改动前

1. 选择对应产品分支。界面和患者流程一般提交到 `medical-record-app-v4`；Lite 输入功能提交到 `lite-input`；套件说明提交到 `main`。
2. 不得把患者数据、模型密钥或本机配置提交到仓库。
3. 说明改动影响的使用流程和安全边界。
4. 保留历史发布产物和现有分支边界。

### 开发检查

- 提交 Android 代码前运行 `gradlew.bat assembleDebug`。
- 检查修改过的资源 XML 和 JavaScript 文件。
- 运行 `git diff --check` 并审阅最终差异。
- 修改蓝牙 HID、配对或 OTG 行为时，记录真机测试情况。
- 产品行为或安全边界变化时，同时更新中英文说明。

### Pull Request

保持一个 PR 聚焦一项工作。写明来源分支、目标分支、受影响流程、已执行的验证和尚未覆盖的设备限制。界面改动应使用合成患者资料提供截图。
