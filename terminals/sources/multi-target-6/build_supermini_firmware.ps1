param(
    [Parameter(Mandatory = $true)]
    [string]$IdfPath,
    [string]$BuildDir = "C:\esp32_supermini_bridge_build",
    [string]$OutputDir = "",
    [string]$SourceDir = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path (Join-Path $IdfPath "export.ps1"))) {
    throw "ESP-IDF not found: $IdfPath"
}

. (Join-Path $IdfPath "export.ps1")

$source = if ($SourceDir) {
    [IO.Path]::GetFullPath($SourceDir)
} else {
    Join-Path $PSScriptRoot "esp32_bridge_firmware"
}
$defaults = Join-Path $source "sdkconfig.defaults"

New-Item -ItemType Directory -Force $BuildDir | Out-Null

Push-Location $source
try {
    & idf.py -B $BuildDir `
        "-DSDKCONFIG=$BuildDir\sdkconfig" `
        "-DSDKCONFIG_DEFAULTS=$defaults" `
        "-DBRIDGE_DEVICE_NAME=ESP32蓝牙键盘终端" `
        "-DBRIDGE_USB_PRODUCT_NAME=ESP32蓝牙键盘终端" `
        "-DBRIDGE_SERIAL_NUMBER=0001" `
        build
    if ($LASTEXITCODE -ne 0) { throw "Firmware build failed" }

    Push-Location $BuildDir
    try {
        & python -m esptool --chip esp32s3 merge_bin `
            -o (Join-Path $BuildDir "merged-flash.bin") `
            "@flash_args"
        if ($LASTEXITCODE -ne 0) { throw "Merged binary build failed" }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}

if (-not $OutputDir) {
    $OutputDir = Join-Path $PSScriptRoot "releases\supermini-gbk"
}
New-Item -ItemType Directory -Force $OutputDir | Out-Null

$target = Join-Path $OutputDir "ESP32-S3-SuperMini-0001-bridge-merged.bin"
Copy-Item (Join-Path $BuildDir "merged-flash.bin") $target -Force
$hash = (Get-FileHash $target -Algorithm SHA256).Hash
"$hash  ESP32-S3-SuperMini-0001-bridge-merged.bin" |
    Set-Content (Join-Path $OutputDir "SHA256SUMS.txt") -Encoding ascii

Write-Host "Firmware: $target"
Write-Host "SHA256:  $hash"
