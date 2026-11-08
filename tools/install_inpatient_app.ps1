$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root "医键住院管理-v4.0-test18.apk"
$adb = "C:\Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $apk)) {
    throw "APK not found: $apk"
}

if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB not found. Install Android platform-tools first."
}

$devices = & $adb devices
$connected = $devices | Where-Object { $_ -match "\sdevice$" }
if (-not $connected) {
    Write-Host "No Android phone is connected."
    Write-Host "Enable USB debugging on the phone, connect the USB cable, then run this script again."
    Read-Host "Press Enter to close"
    exit 1
}

$installOutput = & $adb install -r $apk 2>&1
Write-Host ($installOutput -join "`n")

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Installation failed. If the message mentions a signature mismatch, uninstall the old debug build first:"
    Write-Host "adb uninstall com.hidble.carekeyboard"
    Read-Host "Press Enter to close"
    exit $LASTEXITCODE
}

& $adb shell monkey -p com.hidble.carekeyboard -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Installed and opened on the connected phone."
