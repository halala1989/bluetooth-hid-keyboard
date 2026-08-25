param(
    [string]$BuildDir = "D:\pico_build\pico_ble_hid",
    [string]$SdkPath = $env:PICO_SDK_PATH
)

$ErrorActionPreference = "Stop"

if (-not $SdkPath) { $SdkPath = "D:\pico\pico-sdk" }
if (-not (Test-Path $SdkPath)) { throw "PICO_SDK_PATH not found: $SdkPath" }

$ninja = Get-Command ninja -ErrorAction SilentlyContinue
if (-not $ninja -and (Test-Path "D:\pico\ninja")) {
    $env:Path = "D:\pico\ninja;" + $env:Path
}

$gcc = Get-Command gcc -ErrorAction SilentlyContinue
if (-not $gcc -and (Test-Path "D:\pico\winlibs\mingw64\bin")) {
    $env:Path = "D:\pico\winlibs\mingw64\bin;" + $env:Path
}

$env:PICO_SDK_PATH = $SdkPath
$source = Join-Path $PSScriptRoot "pico_firmware"

cmake -S $source -B $BuildDir -G Ninja `
    "-DPICO_SDK_PATH=$SdkPath" `
    "-DPICO_BOARD=pico_w" `
    "-DPICO_PLATFORM=rp2040"
cmake --build $BuildDir -j 8

$uf2 = Join-Path $BuildDir "pico_ble_hid_keyboard.uf2"
$dest = Join-Path $PSScriptRoot "firmware\pico_ble_hid_keyboard.uf2"
New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
Copy-Item $uf2 $dest -Force
Write-Host "Firmware copied to $dest"
