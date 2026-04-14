@echo off

REM MySQL set
set db_user=root
set db_password=123456
set db_host=localhost
set db_port=3306
set sql_file=..\..\gen\src\main\java\org\sunrise\game\genDb\sql\sunrise.sql

REM MySQL bin path
set mysql_bin="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

if not exist %mysql_bin% (
    echo MySQL executable not found in the specified path.
    exit /b 1
)

REM 导入SQL文件
%mysql_bin% -u%db_user% -p%db_password% -h%db_host% -P%db_port% < %sql_file%

REM 检查SQL导入结果
if %ERRORLEVEL%==0 (
    echo Database imported successfully.
) else (
    echo Failed to import the database.
)

pause
