# 配置与环境变量

配置文件在 `config/`，启动时作为 jar 的第一个参数传入。敏感项写成 `${ENV:default}`，由框架在启动时替换。

**解析顺序：** 环境变量 → JVM `-D` 参数 → 默认值

| 写法 | 含义 |
|------|------|
| `${ENV}` | 必填，缺失则启动失败 |
| `${ENV:default}` | 优先读环境变量，否则用默认值 |

**相关文件：**

| 文件 | 说明 |
|------|------|
| `*-config.properties` | 实际配置，本地可直接用 |
| `*-config.example.properties` | 模板，可复制为 `local-*.properties` |
| `start/docker/.env` | Docker 密钥，从 `.env.example` 复制 |

---

## 本地开发

直接跑启动脚本即可，无需设环境变量（会用默认值：DB `123456`、GM `admin/sunrise`）。

```bash
mvn clean package
start\windows\single\runallone.bat          # Windows 单进程
sh start/linux/server_run_allone.sh       # Linux 单进程
```

生产或多机部署时，启动前注入环境变量（**同一终端窗口**内设置后启动）：

```powershell
# PowerShell
$env:JDBC_PASSWORD = "your-db-pass"
$env:ADMIN_PASSWORD = "your-gm-pass"
$env:PLAYER_JWT_SECRET = "your-32-char-secret"
.\start\windows\single\runallone.bat
```

```bash
# Linux
export JDBC_PASSWORD=your-db-pass
export ADMIN_PASSWORD=your-gm-pass
sh start/linux/server_run_allone.sh
```

也可用 JVM 参数：`-DJDBC_PASSWORD=xxx`

**注意：** `CONFIG_PATH` 默认 `../tables/json`，相对**配置文件所在目录**（`config/`），与启动时工作目录无关。Docker 等场景可设绝对路径如 `/app/tables/json`。

---

## Docker

统一挂载仓库 `config/`，不再维护 `start/docker/config/` 副本。

```bash
mvn clean package
cd start/docker
cp .env.example .env    # Windows: copy .env.example .env
# 编辑 .env：JDBC_PASSWORD、ADMIN_PASSWORD、PLAYER_JWT_SECRET
docker compose up -d --build
```

- `.env`：各服务共享的密钥（`JDBC_PASSWORD` 同时作为 MySQL root 密码）
- `docker-compose.yml` 的 `environment`：按服务注入地址差异（`MASTER_ADDRESS`、`REPORT_ADDRESS` 等），一般无需改

改配置后重启对应容器；不支持热更新。

---

## 环境变量一览

| 变量 | 配置项 | 本地默认值 | 说明 |
|------|--------|------------|------|
| `JDBC_URL` | `jdbc.url` | `jdbc:mysql://127.0.0.1:3306/sunrise` | 数据库连接 |
| `JDBC_USER` | `jdbc.user` | `root` | |
| `JDBC_PASSWORD` | `jdbc.password` | `123456` | **生产必改** |
| `MASTER_ADDRESS` | `master.address` | `127.0.0.1` | 中心服地址 |
| `REPORT_ADDRESS` | `report.address` | `127.0.0.1` | 本节点上报地址 |
| `EXTERNAL_ADDRESS` | `external.address` | `127.0.0.1` | 网关对外地址 |
| `HTTP_ADDRESS` | `http.address` | `127.0.0.1` | 客户端工具用 |
| `ADMIN_USER` | `admin.user` | `admin` | GM 账号 |
| `ADMIN_PASSWORD` | `admin.password` | `sunrise` | **生产必改** |
| `PLAYER_JWT_SECRET` | `player.jwt.secret` | 见配置文件 | **生产必改** |
| `MAIL_SMTP_USERNAME` | `mail.smtp.username` | — | 邮件（http/runallone） |
| `MAIL_SMTP_PASSWORD` | `mail.smtp.password` | — | |
| `CONFIG_PATH` | `config.path` | `../tables/json` | 策划表目录（相对 `config/`） |
| `CONFIG_NAV_PATH` | `config.nav.path` | `../tables/mapjson` | 地图导航 |
