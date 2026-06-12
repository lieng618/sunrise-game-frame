@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

set count=0
for %%F in (*-config.example.properties) do (
    set "src=%%F"
    set "dst=!src:.example.properties=.properties!"
    copy /Y "!src!" "!dst!" >nul
    echo Copied: !src! -^> !dst!
    set /a count+=1
)

if !count! equ 0 (
    echo No *-config.example.properties files found in %~dp0
    exit /b 1
)

echo Done. !count! config file^(s^) reset from templates.
