@echo off

set LUBAN_DLL=.\Tools\Luban\Luban.dll
set CONF_ROOT=.\Datas

dotnet %LUBAN_DLL% ^
    -t client ^
    -c cs-simple-json ^
    -d json ^
    --conf .\luban.conf ^
    -x outputCodeDir=Assets/Gen ^
    -x outputDataDir=.\clientjson ^
    -x l10n.textFile.keyFieldName=key
pause