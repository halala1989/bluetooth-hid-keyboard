$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$terminals = Join-Path $root "terminals"
$catalogPath = Join-Path $terminals "catalog.json"

if (-not (Test-Path -LiteralPath $catalogPath)) {
    throw "Terminal catalog is missing: $catalogPath"
}

$catalog = Get-Content -LiteralPath $catalogPath -Raw | ConvertFrom-Json
$required = @(
    "sources/phone-bluetooth",
    "sources/plain-bluetooth",
    "sources/pico-w-master",
    "sources/esp32-s3-app",
    "sources/multi-target-6",
    "sources/pico-w-keyboard-android-client",
    "sources/pico-w-firmware-independent"
)

$rows = foreach ($relative in $required) {
    $path = Join-Path $terminals $relative
    [pscustomobject]@{
        Source = $relative
        Present = Test-Path -LiteralPath $path
        Files = if (Test-Path -LiteralPath $path) {
            (Get-ChildItem -LiteralPath $path -Recurse -File).Count
        } else { 0 }
    }
}

$rows | Format-Table -AutoSize
if ($rows.Where({ -not $_.Present }).Count -gt 0) {
    throw "One or more terminal source snapshots are missing."
}

$ids = @($catalog.terminals | Select-Object -ExpandProperty id)
$expectedIds = @("phone_hid", "pico_w", "esp32_s3", "yd_esp32_s3", "esp32_legacy")
$actualKey = (($ids | Sort-Object) -join ",")
$expectedKey = (($expectedIds | Sort-Object) -join ",")
if ($actualKey -ne $expectedKey) {
    throw "Terminal catalog does not match the Android terminal registry."
}

Write-Host "Terminal sources and catalog verified."
