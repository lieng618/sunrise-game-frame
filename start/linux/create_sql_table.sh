#!/bin/bash

# MySQL 配置
db_user="root"
db_password="123456"
db_host="localhost"
db_port="3306"
sql_file="../../gen/src/main/java/org/sunrise/game/genDb/sql/sunrise.sql"

# 导入 SQL 文件
mysql -u$db_user -p$db_password -h$db_host -P$db_port < $sql_file

# 检查 SQL 导入结果
if [ $? -eq 0 ]; then
    echo "Database imported successfully."
else
    echo "Failed to import the database."
fi