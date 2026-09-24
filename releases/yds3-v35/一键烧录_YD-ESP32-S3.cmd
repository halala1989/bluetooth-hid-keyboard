@echo off
chcp 65001 >nul
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0flash_yds3_firmware.ps1" %*
set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (
  echo [OK] 烧录完成。
) else (
  echo [FAIL] 烧录失败，错误码：%RC%
)
echo.
pause
exit /b %RC%
