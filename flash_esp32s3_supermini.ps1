[CmdletBinding()]
param(
    [string]$Port = "",
    [int]$Baud = 460800,
    [int]$WaitSeconds = 180,
    [string]$FirmwarePath = "",
    [string]$IdfPath = "C:\Users\halal\esp\v5.2.2\esp-idf"
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-DownloadPorts {
    @(Get-CimInstance Win32_PnPEntity |
        Where-Object {
            $_.PNPDeviceID -match "VID_303A&PID_1001" -and
            $_.Name -match "\(COM(?<num>\d+)\)"
        } |
        ForEach-Object {
            [void]($_.Name -match "\((?<port>COM\d+)\)")
            [pscustomobject]@{
                Port = $Matches["port"]
                Name = $_.Name
                PnpId = $_.PNPDeviceID
            }
        } |
        Sort-Object Port)
}

function Wait-ForDownloadPort {
    param([string]$RequestedPort, [int]$TimeoutSeconds)

    if ($RequestedPort) {
        if ($RequestedPort -notmatch "^COM\d+$") {
            throw "串口格式不正确：$RequestedPort"
        }
        return $RequestedPort.ToUpperInvariant()
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $announced = $false
    do {
        $ports = @(Get-DownloadPorts)
        if ($ports.Count -eq 1) {
            Write-Host "检测到下载串口：$($ports[0].Port)  $($ports[0].Name)" -ForegroundColor Green
            return $ports[0].Port
        }
        if ($ports.Count -gt 1) {
            Write-Host "检测到多个 ESP32-S3 下载串口：" -ForegroundColor Yellow
            $ports | Format-Table Port, Name, PnpId -AutoSize
            throw "请使用 -Port 参数指定板子的串口。"
        }
        if (-not $announced) {
            Write-Host ""
            Write-Host "等待 ESP32-S3 进入下载模式..." -ForegroundColor Yellow
            Write-Host "请按住 BOOT，拔插一次 USB，再松开 BOOT。" -ForegroundColor Yellow
            Write-Host "等待时间：$TimeoutSeconds 秒" -ForegroundColor DarkGray
            $announced = $true
        } else {
            Write-Host "." -NoNewline -ForegroundColor DarkGray
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "未检测到 ESP32-S3 下载串口（VID_303A:PID_1001）。请按 BOOT 后重新插 USB。"
}

function Get-Sha256Hex([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

if (-not $FirmwarePath) {
    $FirmwarePath = Join-Path $PSScriptRoot "releases\v4.0-test30\firmware\ESP32-S3-SuperMini-0001-bridge-merged.bin"
}
$FirmwarePath = [IO.Path]::GetFullPath($FirmwarePath)
if (-not (Test-Path -LiteralPath $FirmwarePath)) {
    throw "找不到 Super Mini 固件：$FirmwarePath"
}

$expectedHash = "948AD92A7771D5E8887643F0D3CC77CEA651881C148265B07BBC612628344609"
$actualHash = Get-Sha256Hex $FirmwarePath
if ($actualHash -ne $expectedHash) {
    throw "固件 SHA256 不匹配，拒绝烧录。`n期望：$expectedHash`n实际：$actualHash"
}

Write-Host "固件：$FirmwarePath"
Write-Host "SHA256 校验通过：$actualHash" -ForegroundColor Green
$selectedPort = Wait-ForDownloadPort -RequestedPort $Port -TimeoutSeconds $WaitSeconds

$python = Join-Path $env:USERPROFILE ".espressif\python_env\idf5.2_py3.11_env\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $python)) {
    if (-not (Test-Path -LiteralPath (Join-Path $IdfPath "export.ps1"))) {
        throw "找不到 ESP-IDF 或 esptool 环境：$IdfPath"
    }
    Write-Step "加载 ESP-IDF 环境"
    . (Join-Path $IdfPath "export.ps1") *> $null
    $python = "python"
}

Write-Step "烧录 ESP32-S3 Super Mini 0001（$selectedPort，$Baud baud）"
& $python -m esptool `
    --chip esp32s3 `
    --port $selectedPort `
    --baud $Baud `
    --before default_reset `
    --after hard_reset `
    write_flash `
    -z `
    --flash_mode dio `
    --flash_freq 80m `
    --flash_size 4MB `
    0x0 $FirmwarePath
if ($LASTEXITCODE -ne 0) { throw "烧录失败" }

Write-Step "回读校验"
& $python -m esptool `
    --chip esp32s3 `
    --port $selectedPort `
    --baud $Baud `
    verify_flash `
    --flash_mode dio `
    --flash_freq 80m `
    --flash_size 4MB `
    0x0 $FirmwarePath
if ($LASTEXITCODE -ne 0) { throw "回读校验失败" }

Write-Host ""
Write-Host "烧录和校验成功。" -ForegroundColor Green
Write-Host "如果板子仍停留在下载模式，请拔插一次 USB（不要按 BOOT）。" -ForegroundColor Yellow
Write-Host "正常运行时，设备会广播：ESP32蓝牙键盘终端-0001" -ForegroundColor Green
Write-Host "USB 序列号：0001" -ForegroundColor Green
