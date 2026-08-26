# PowerShell 环境自检：克隆完仓库后让 ChatGPT/本人运行，确认环境是否齐全
# 用法:  powershell -ExecutionPolicy Bypass -File tools\setup_check.ps1
$ErrorActionPreference = "Continue"

function Section($title) { Write-Host "`n===== $title =====" -ForegroundColor Cyan }
function Ok($msg)  { Write-Host "  [OK]   $msg" -ForegroundColor Green }
function Warn($msg){ Write-Host "  [--]   $msg" -ForegroundColor Yellow }
function Miss($msg){ Write-Host "  [MISS] $msg" -ForegroundColor Red }

Section "仓库基本信息"
$git = Get-Command git -ErrorAction SilentlyContinue
if ($git) {
    Ok "git: $((git --version))"
    Ok "当前分支: $((git branch --show-current))"
    $tags = (git tag) -join ", "
    Ok "本地标签: $tags"
    $remotes = (git remote -v | Select-Object -First 1)
    Ok "remote: $remotes"
} else {
    Miss "未安装 git（GitHub 克隆需要）"
}

Section "JDK（编译 APK 需要）"
$javaHome = $env:JAVA_HOME
if ($javaHome) { Ok "JAVA_HOME=$javaHome" } else { Warn "JAVA_HOME 未设置" }
$java = Get-Command java -ErrorAction SilentlyContinue
if ($java) {
    $ver = (& java -version 2>&1 | Select-Object -First 1)
    Ok "java: $ver"
    if ($ver -match "17") { Ok "JDK 17 满足要求" } else { Warn "建议使用 JDK 17" }
} else {
    Miss "未找到 java（编译 APK 需要 JDK 17）"
}

Section "Android SDK（编译 APK 需要）"
$ah = $env:ANDROID_HOME
if ($ah) { Ok "ANDROID_HOME=$ah" } else { Warn "ANDROID_HOME 未设置（可指向 Android SDK 目录）" }
$sdkRoot = if ($ah) { $ah } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (Test-Path $sdkRoot) {
    Ok "检测到 Android SDK: $sdkRoot"
    $buildTools = Get-ChildItem "$sdkRoot\build-tools" -Directory -ErrorAction SilentlyContinue | Select-Object -Last 1
    if ($buildTools) { Ok "build-tools: $($buildTools.Name)" } else { Warn "build-tools 目录为空" }
} else {
    Warn "未在默认位置找到 Android SDK（装 Android Studio 会自动创建）"
}

Section "Gradle Wrapper（APK 构建入口）"
if (Test-Path "android\gradlew.bat") { Ok "存在 android\gradlew.bat" } else { Miss "缺少 gradlew.bat" }

Section "Pico SDK（仅编译 Pico W 固件需要，可选）"
$ps = $env:PICO_SDK_PATH
if ($ps) { Ok "PICO_SDK_PATH=$ps" } else { Warn "PICO_SDK_PATH 未设置（不编译固件可忽略）" }

Section "结论"
Write-Host "`n- 只想要 APK 成品：releases/ 目录里已有编译好的 APK，无需任何工具链。"
Write-Host "- 要自己编译 APK：需要 JDK 17 + Android SDK（+ 可选 Android Studio）。"
Write-Host "- 要编译 Pico 固件（master 旧产品线）：额外需要 Pico SDK + CMake/Ninja/arm-none-eabi-gcc。"
Write-Host "- 缺什么告诉我，我可以帮你安装。`n"
