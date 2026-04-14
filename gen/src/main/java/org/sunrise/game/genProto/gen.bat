@echo off

REM 定义目标目录（即 game 模块的 proto 生成目录）
set GAME_PROTO_DIR=..\..\..\..\..\..\..\..\gen\src\main\java

REM 检查生成目录是否存在，不存在则创建
if not exist %GAME_PROTO_DIR%\org\sunrise\game\genProto\gen (
    mkdir %GAME_PROTO_DIR%\org\sunrise\game\genProto\gen
)

REM 清空目标目录中的旧文件
del /s /q %GAME_PROTO_DIR%\org\sunrise\game\genProto\gen\*

REM 循环处理 proto 目录下的每个 .proto 文件
for %%i in (proto\*.proto) do (
    echo 正在处理 %%i
    call proto\protoc.exe --proto_path=proto --java_out=%GAME_PROTO_DIR% %%i
)

echo 生成完成

REM 暂停控制台，等待用户按任意键关闭
pause