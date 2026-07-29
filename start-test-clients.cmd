@echo off
setlocal
where pwsh.exe >nul 2>&1
if errorlevel 1 (
	echo PowerShell 7 ^(pwsh.exe^) was not found in PATH.
	pause
	exit /b 1
)
pwsh.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-test-clients.ps1" %*
if errorlevel 1 (
	echo.
	echo Pixel TZZ Pro test client launcher failed.
	pause
	exit /b 1
)
endlocal
