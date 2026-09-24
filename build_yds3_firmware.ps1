param(
    [string]$BuildDir = "C:\esp32_yds3_bridge_build",
    [string]$IdfPath = "C:\Users\halal\esp\v5.2.2\esp-idf",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path (Join-Path $IdfPath "export.ps1"))) {
    throw "ESP-IDF not found: $IdfPath"
}

. (Join-Path $IdfPath "export.ps1")

$source = Join-Path $PSScriptRoot "esp32_bridge_firmware"
$defaults = @(
    (Join-Path $source "sdkconfig.defaults"),
    (Join-Path $source "sdkconfig.yds3.defaults")
) -join [IO.Path]::PathSeparator

New-Item -ItemType Directory -Force $BuildDir | Out-Null

Push-Location $source
try {
    & idf.py -B $BuildDir `
        "-DSDKCONFIG=$BuildDir\sdkconfig" `
        "-DSDKCONFIG_DEFAULTS=$defaults" `
        "-DBRIDGE_DEVICE_NAME=YD-ESP32-S3 Bridge" `
        "-DBRIDGE_USB_PRODUCT_NAME=YD-ESP32-S3 Keyboard" `
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
    $OutputDir = Join-Path $PSScriptRoot "releases\yds3-v1"
}
New-Item -ItemType Directory -Force $OutputDir | Out-Null

$artifacts = @(
    "merged-flash.bin",
    "esp32_bridge_firmware.bin",
    "bootloader\bootloader.bin",
    "partition_table\partition-table.bin",
    "flasher_args.json",
    "flash_args",
    "sdkconfig"
)
foreach ($relative in $artifacts) {
    $src = Join-Path $BuildDir $relative
    if (Test-Path $src) {
        $name = if ($relative -eq "merged-flash.bin") { "YD-ESP32-S3-bridge-merged.bin" } else { Split-Path $relative -Leaf }
        Copy-Item $src (Join-Path $OutputDir $name) -Force
    }
}

$hashTarget = Join-Path $OutputDir "YD-ESP32-S3-bridge-merged.bin"
if (Test-Path $hashTarget) {
    $hash = (Get-FileHash $hashTarget -Algorithm SHA256).Hash
    "$hash  YD-ESP32-S3-bridge-merged.bin" | Set-Content (Join-Path $OutputDir "SHA256SUMS.txt") -Encoding ascii
    Write-Host "Firmware: $hashTarget"
    Write-Host "SHA256:  $hash"
}
