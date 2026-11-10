@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0flash_esp32s3_supermini.ps1"
echo.
pause
