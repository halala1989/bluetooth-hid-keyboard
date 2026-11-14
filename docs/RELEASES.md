# Release Process · 发布流程

[English](#english) | [简体中文](#简体中文)

## English

### Version naming

- Clinical edition test builds use `4.0-testNN`, increasing the numeric suffix for every published build.
- Android `versionCode` must increase for every installable build.
- Lite edition releases follow the numeric version history of their own branch.
- The repository README should identify the current test or stable product release and link its notes.

### Release contents

Each product branch keeps its installable artifact and notes under `releases/<version>/`:

```text
releases/v4.0-test33/
├── 医键住院管理-v4.0-test33.apk
└── RELEASE_NOTES.md
```

`RELEASE_NOTES.md` must record the version code, version name, user-visible changes, verification performed, SHA256 digest, and any remaining real-device limitation.

### Current clinical release

- Version: `4.0-test33`
- Version code: `40032`
- SHA256: `FEB14A98987A43130E03C1E02B6AE84E72434B495A2D5E0FA5958EBED0C7A049`
- Notes: [`releases/v4.0-test33/RELEASE_NOTES.md`](https://github.com/halala1989/carekey-suite/blob/medical-record-app-v4/releases/v4.0-test33/RELEASE_NOTES.md)

### Release checks

1. Build the Android APK.
2. Parse all resource XML and validate changed scripts.
3. Run `git diff --check`.
4. Confirm package name, version code, version name, launch activity, and signing status.
5. Calculate SHA256 after the final APK is copied into the release directory.
6. Test on a real device whenever pairing, HID, storage, or output behavior changes.

Debug-signed artifacts are test builds and must not be represented as production or clinically certified releases.

## 简体中文

### 版本命名

- 临床版测试构建使用 `4.0-testNN`，每次发布递增数字后缀。
- 每个可安装构建都必须提高 Android `versionCode`。
- Lite 版沿用自身分支的数字版本历史。
- 仓库首页应标明当前测试版或稳定版，并链接对应发布说明。

### 发布内容

每个产品分支把安装包和说明保存在 `releases/<版本>/`：

```text
releases/v4.0-test33/
├── 医键住院管理-v4.0-test33.apk
└── RELEASE_NOTES.md
```

`RELEASE_NOTES.md` 必须记录 versionCode、versionName、用户可见变化、已执行的验证、SHA256 和尚未完成真机验证的限制。

### 当前临床版发布

- 版本：`4.0-test33`
- versionCode：`40032`
- SHA256：`FEB14A98987A43130E03C1E02B6AE84E72434B495A2D5E0FA5958EBED0C7A049`
- 说明：[`releases/v4.0-test33/RELEASE_NOTES.md`](https://github.com/halala1989/carekey-suite/blob/medical-record-app-v4/releases/v4.0-test33/RELEASE_NOTES.md)

### 发布检查

1. 构建 Android APK。
2. 解析全部资源 XML，并检查改动过的脚本。
3. 运行 `git diff --check`。
4. 确认包名、versionCode、versionName、启动页和签名状态。
5. 把最终 APK 复制到发布目录后计算 SHA256。
6. 修改配对、HID、存储或输出行为时，应进行真机测试。

Debug 签名产物属于测试版，不得描述为生产版或通过临床认证的版本。
