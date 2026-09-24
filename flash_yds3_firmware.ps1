[CmdletBinding()]
param(
    [string]$Port = "",
    [int]$Baud = 460800,
    [switch]$Rebuild,
    [switch]$Monitor,
    [switch]$ListPorts,
    [string]$IdfPath = "C:\Users\halal\esp\v5.2.2\esp-idf",
    [string]$BuildDir = "C:\esp32_yds3_bridge_build",
    [string]$FirmwarePath = ""
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-Sha256Hex([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        try {
            $bytes = $sha.ComputeHash($stream)
            return [BitConverter]::ToString($bytes).Replace("-", "")
        } finally {
            $sha.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Get-BoardSerialCandidates {
    $result = @()
    $devices = Get-PnpDevice -Class Ports -PresentOnly -ErrorAction SilentlyContinue |
        Where-Object { $_.Status -eq "OK" }

    foreach ($device in $devices) {
        $name = [string]$device.FriendlyName
        $instance = [string]$device.InstanceId
        if ($name -notmatch "\(COM(?<num>\d+)\)") { continue }

        $port = "COM" + $Matches["num"]
        $isCh343 = ($instance -match "VID_1A86") -or
            ($name -match "CH343|CH34x|USB-SERIAL CH34|USB-Enhanced-SERIAL")
        $isFtdi = ($instance -match "VID_0403") -or
            ($name -match "FT232|USB Serial Port")
        $isEspressifUsb = ($instance -match "VID_303A") -or
            ($name -match "USB Serial/JTAG|USB 串行设备")

        $rank = 9
        if ($isFtdi -or $isCh343) { $rank = 0 }
        elseif ($isEspressifUsb) { $rank = 1 }

        $result += [pscustomobject]@{
            Port = $port
            Name = $name
            InstanceId = $instance
            Rank = $rank
            Recommended = $rank -le 1
            IsBluetooth = $instance -match "BTHENUM"
        }
    }

    return @($result | Sort-Object Rank, Port)
}

function Select-BoardSerialPort {
    param(
        [string]$RequestedPort,
        [object[]]$Candidates
    )

    if ($RequestedPort) {
        if ($RequestedPort -notmatch "^COM\d+$") {
            throw "串口格式不正确：$RequestedPort（示例：COM11）"
        }
        return $RequestedPort.ToUpperInvariant()
    }

    $all = if ($null -ne $Candidates) { @($Candidates) } else { @(Get-BoardSerialCandidates) }
    $recommended = @($all | Where-Object { $_.Recommended })

    if ($recommended.Count -eq 1) {
        $picked = $recommended[0]
        Write-Host "自动选择：$($picked.Port)  $($picked.Name)" -ForegroundColor Green
        return $picked.Port
    }

    if ($recommended.Count -gt 1) {
        Write-Host "检测到多个可烧录串口：" -ForegroundColor Yellow
        for ($i = 0; $i -lt $recommended.Count; $i++) {
            Write-Host "  [$($i + 1)] $($recommended[$i].Port)  $($recommended[$i].Name)"
        }
        $answer = Read-Host "请选择序号（直接回车选 1）"
        if (-not $answer) { return $recommended[0].Port }
        $index = 0
        if (-not [int]::TryParse($answer, [ref]$index) -or $index -lt 1 -or $index -gt $recommended.Count) {
            throw "无效的序号：$answer"
        }
        return $recommended[$index - 1].Port
    }

    $otherPorts = @($all | Where-Object { -not $_.IsBluetooth -and -not $_.Recommended })
    $message = @(
        "没有检测到 YD-ESP32-S3 的烧录串口。",
        "请确认：",
        "  1. 数据线接在板子的 COM 串口；",
        "  2. 该板使用 FT232RQ，应出现 USB Serial Port (COMx)；",
        "  3. 若使用其他批次板卡，也可能出现 CH343/CH34x (COMx)。",
        "",
        "也可以用参数手动指定，例如：",
        "  .\flash_yds3_firmware.ps1 -Port COM7"
    )
    if ($otherPorts.Count -gt 0) {
        $message += @("", "当前检测到的其他 USB 串口（不会自动烧录）：")
        $message += $otherPorts | ForEach-Object { "  $($_.Port)  $($_.Name)" }
    }
    Write-Host ($message -join [Environment]::NewLine) -ForegroundColor Red
    exit 1
}

$candidates = @(Get-BoardSerialCandidates)
if ($ListPorts) {
    if ($candidates.Count -eq 0) {
        Write-Host "未检测到串口设备。" -ForegroundColor Yellow
    } else {
        $candidates | Format-Table Port, Name, Recommended, InstanceId -AutoSize
    }
    return
}

$source = Join-Path $PSScriptRoot "esp32_bridge_firmware"
$buildScript = Join-Path $PSScriptRoot "build_yds3_firmware.ps1"
if (-not $FirmwarePath) {
    $FirmwarePath = Join-Path $PSScriptRoot "releases\yds3-v1\YD-ESP32-S3-bridge-merged.bin"
}
$FirmwarePath = [IO.Path]::GetFullPath($FirmwarePath)

if ($Rebuild -or -not (Test-Path $FirmwarePath)) {
    Write-Step "编译 YD-ESP32-S3 N16R8 固件"
    & $buildScript -BuildDir $BuildDir -IdfPath $IdfPath
    if ($LASTEXITCODE -ne 0) { throw "固件编译失败" }
} else {
    Write-Host "使用现成固件：$FirmwarePath" -ForegroundColor Green
}

if (-not (Test-Path $FirmwarePath)) {
    throw "找不到固件：$FirmwarePath"
}

$sumFile = Join-Path (Split-Path $FirmwarePath -Parent) "SHA256SUMS.txt"
if (Test-Path $sumFile) {
    $line = Get-Content $sumFile | Where-Object { $_ -match "YD-ESP32-S3-bridge-merged\.bin" } | Select-Object -First 1
    if ($line) {
        $expected = ($line -split "\s+")[0]
        $actual = Get-Sha256Hex $FirmwarePath
        if ($expected -and $actual -ne $expected) {
            throw "固件校验失败：SHA256 不匹配，拒绝烧录。"
        }
        Write-Host "固件 SHA256 校验通过：$actual" -ForegroundColor Green
    }
}

$selectedPort = Select-BoardSerialPort -RequestedPort $Port -Candidates $candidates

if (-not (Test-Path (Join-Path $IdfPath "export.ps1"))) {
    throw "ESP-IDF not found: $IdfPath"
}
Write-Step "加载 ESP-IDF / esptool 环境"
. (Join-Path $IdfPath "export.ps1") *> $null
if ($LASTEXITCODE -ne 0) { throw "ESP-IDF export 失败" }

Write-Step "烧录到 $selectedPort（$Baud baud）"
Write-Host "固件：$FirmwarePath"
& python -m esptool `
    --chip esp32s3 `
    --port $selectedPort `
    --baud $Baud `
    --before default_reset `
    --after hard_reset `
    write_flash `
    -z `
    --flash_mode dio `
    --flash_freq 80m `
    --flash_size 16MB `
    0x0 $FirmwarePath
if ($LASTEXITCODE -ne 0) { throw "烧录失败" }

Write-Step "回读校验"
& python -m esptool `
    --chip esp32s3 `
    --port $selectedPort `
    --baud $Baud `
    verify_flash `
    --flash_mode dio `
    --flash_freq 80m `
    --flash_size 16MB `
    0x0 $FirmwarePath
if ($LASTEXITCODE -ne 0) { throw "回读校验失败" }

Write-Host ""
Write-Host "烧录成功，板子已复位。" -ForegroundColor Green
Write-Host "下一步：把 ESP32-S3 原生 USB Type-C 接到目标电脑。" -ForegroundColor Green

if ($Monitor) {
    Write-Step "打开串口监视器（按 Ctrl+] 退出）"
    Push-Location $source
    try {
        & idf.py -B $BuildDir -p $selectedPort monitor
    } finally {
        Pop-Location
    }
}
