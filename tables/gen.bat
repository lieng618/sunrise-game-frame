@echo off
setlocal
set LUBAN_DLL=.\Tools\Luban\Luban.dll
set CONF_ROOT=.\Datas

dotnet %LUBAN_DLL% ^
    -t all ^
    -c java-json ^
    -d json  ^
    --conf .\luban.conf ^
    -x outputCodeDir=src ^
    -x outputDataDir=.\json ^
    -x l10n.textFile.keyFieldName=key

if errorlevel 1 goto :generation_failed

if not exist ".\src" (
    echo Luban generation produced no source files.
    goto :generation_failed
)

set sourceDir=.\src
set targetDir=..\game\src\main\java\org\sunrise\game\game\config\

echo Clearing target directory: %targetDir%
if exist "%targetDir%" (
    rmdir /S /Q "%targetDir%"
    if errorlevel 1 goto :move_failed
)
mkdir "%targetDir%"
if errorlevel 1 goto :move_failed

echo Moving files and directories from %sourceDir% to %targetDir%
xcopy "%sourceDir%\*" "%targetDir%\" /E /I /Y
if errorlevel 1 goto :move_failed

echo Deleting original files and directories from %sourceDir%
rmdir /S /Q "%sourceDir%"
if errorlevel 1 goto :move_failed

echo successfully.
pause
exit /b 0

:generation_failed
echo Luban generation failed. Existing generated files were not changed.
pause
exit /b 1

:move_failed
echo Moving generated files failed. Source files remain in %sourceDir%.
pause
exit /b 1
