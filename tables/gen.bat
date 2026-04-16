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

@echo off
set sourceDir=.\src
set targetDir=..\game\src\main\java\org\sunrise\game\game\config\

echo Clearing target directory: %targetDir%
if exist "%targetDir%" (
    rmdir /S /Q "%targetDir%"
)
mkdir "%targetDir%"

echo Moving files and directories from %sourceDir% to %targetDir%
xcopy "%sourceDir%\*" "%targetDir%\" /E /I /Y

echo Deleting original files and directories from %sourceDir%
rmdir /S /Q "%sourceDir%"

echo successfully.
pause
