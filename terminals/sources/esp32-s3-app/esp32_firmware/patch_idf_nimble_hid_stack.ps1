# 修复 ESP-IDF esp_hid 组件 NimBLE HID 事件任务栈过小（2048B -> 6144B）
# 症状：固件启动后反复重启，日志 "***ERROR*** A stack overflow in task ble_hidd_events has been detected."
# 用法：powershell -ExecutionPolicy Bypass -File patch_idf_nimble_hid_stack.ps1 [-IdfPath <ESP-IDF 根目录>]
param(
    [string]$IdfPath = "$env:USERPROFILE\esp\v5.2.2\esp-idf"
)
$ErrorActionPreference = "Stop"
$f = Join-Path $IdfPath "components\esp_hid\src\nimble_hidd.c"
if (-not (Test-Path -LiteralPath $f)) { Write-Error "找不到文件: $f"; exit 1 }
$c = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
if ($c.Contains('.task_stack_size = 6144,')) {
    Write-Output "已修复过（6144），无需修改: $f"
    exit 0
}
$old = '.task_stack_size = 2048,'
if (-not $c.Contains($old)) {
    Write-Error "未找到 2048 的栈配置，IDF 版本可能不同，请手动检查: $f"
    exit 1
}
$c = $c.Replace($old, '.task_stack_size = 6144,')
[System.IO.File]::WriteAllText($f, $c, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "已修复: $f (.task_stack_size 2048 -> 6144)。请重新编译烧录。"