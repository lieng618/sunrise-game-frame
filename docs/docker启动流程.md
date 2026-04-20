# Docker 部署指南

## 一、安装 Docker Desktop

### 1. 下载安装

下载地址：https://docs.docker.com/desktop/setup/install/windows-install/

安装步骤：

1. 双击 `Docker Desktop Installer.exe`
2. 勾选 **"Use WSL 2 instead of Hyper-V"**（关键）
3. 完成安装后点击 **Close and restart** 重启电脑

### 2. 验证安装

```bash
docker --version
docker compose version
```

### 3. 配置镜像加速

在国内访问 Docker Hub 经常超时，需要配置镜像加速器。

打开 **Docker Desktop → Settings → Docker Engine**，在 JSON 配置中添加：

```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

保存后 Docker Desktop 会自动重启。

---

## 二、项目结构

```
start/docker/
├── docker-compose.yml        # Docker Compose 编排文件
└── config/                   # Docker 专用配置文件
    ├── center-config.properties
    ├── external-config.properties
    ├── game-config.properties
    ├── global-config.properties
    ├── gmback-config.properties
    └── http-config.properties
```

根目录保留：

```
Dockerfile                    # 构建镜像（纯运行时镜像，直接拷贝已有 JAR）
.dockerignore                 # 排除不必要的文件，加速构建
```

---

## 三、部署步骤

### 0. 编译项目（必须先执行）

在 IDE 中或通过命令行执行 Maven 编译：

```bash
# 在项目根目录执行
mvn clean package -DskipTests
```

编译完成后，所有 JAR 文件会输出到 `start/jar/` 目录。

### 1. 修改 external.address

编辑 `start/docker/config/external-config.properties`，将 `external.address` 改为客户端可访问的 IP：

```properties
# 本机测试
external.address=127.0.0.1

# 远程部署（改为服务器公网 IP）
external.address=49.232.236.230
```

> `external.address` 会通过 HTTP 服务下发给客户端，客户端用它连接 External 服务器。如果设为 `127.0.0.1`，远程客户端无法连接。

### 2. 构建并启动所有服务

```bash
cd start/docker
docker compose up -d --build
```

### 3. 查看日志

```bash
docker compose logs -f center     # 查看 Center 日志
docker compose logs -f game       # 查看 Game 日志
docker compose logs -f            # 查看所有日志
```

---

## 四、常用命令

所有命令需在 `start/docker/` 目录下执行。

### 服务管理

| 操作 | 命令 | 说明 |
|------|------|------|
| 首次部署 | `docker compose up -d --build` | 构建镜像并启动 |
| 启动服务 | `docker compose start` | 启动已停止的容器（不重新构建） |
| 停止服务 | `docker compose stop` | 停止容器（保留容器和数据） |
| 重启服务 | `docker compose restart` | 重启所有容器 |
| 删除容器 | `docker compose down` | 停止并删除容器（保留数据卷） |
| 彻底清理 | `docker compose down -v` | 停止并删除容器和数据卷 |

### 按场景选择命令

| 场景 | 命令 |
|------|------|
| 日常启停 | `docker compose stop` / `docker compose start` |
| 修改了配置文件 | `docker compose up -d` |
| 修改了代码 | `mvn clean package -DskipTests` → `docker compose up -d --build` |
| MySQL 端口冲突 | 修改 docker-compose.yml 中 MySQL 的宿主机端口 |

### 查看状态

```bash
docker compose ps                # 查看各容器运行状态
docker compose logs -f           # 实时查看所有日志
docker compose logs -f center    # 只看 Center 服务日志
docker compose logs --tail 100   # 查看最近 100 行日志
```

---

## 五、端口说明

### 对外暴露的端口

| 服务 | 端口 | 协议 | 用途 |
|------|------|------|------|
| External | 10000 | TCP | 客户端 TCP 连接 |
| External | 10001 | WebSocket | 客户端 WebSocket 连接 |
| Http | 8090 | HTTP | 地址分发接口 |
| GmBack | 8010 | HTTP | GM 后台管理 |
| MySQL | 13306 | TCP | 数据库（宿主机端口，可改） |

### 端口映射格式

```
"宿主机端口:容器端口"
```

例如 `"13306:3306"` 表示宿主机 13306 端口映射到容器内 3306 端口。

如果宿主机端口冲突，修改 docker-compose.yml 中的 `ports` 即可，容器内端口不要改。

### 不需要对外暴露的端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Center | 8000 | 仅内部 RPC 通信，Docker 内部网络自动互通 |

---

## 六、配置说明

Docker 配置文件与本地配置文件的关键区别：

| 配置项 | 本地值 | Docker 值 | 说明 |
|--------|--------|-----------|------|
| `jdbc.url` | `127.0.0.1:3306` | `mysql:3306` | Docker 容器名 |
| `master.address` | `127.0.0.1` | `center` | Center 容器名 |
| `report.address` | `127.0.0.1` | 各自容器名 | 如 `game`、`external` 等 |
| `external.address` | `127.0.0.1` | 宿主机 IP | 客户端连接地址 |
| `config.path` | `E:/.../tables/json` | `/app/tables/json` | 容器内路径 |
| `admin.uipath` | `E:/.../admin-ui` | `/app/admin-ui` | 容器内路径 |

> Docker 内部服务间通信使用容器名作为域名（如 `mysql`、`center`），由 Docker 内部 DNS 自动解析。

---

## 七、启动顺序

```
MySQL（健康检查通过）
  → Center（等 MySQL 就绪）
    → External / Game / Global / Http / GmBack（等 Center 启动）
```

docker-compose.yml 中通过 `depends_on` 控制启动顺序，Center 服务依赖 MySQL 健康检查，其他服务依赖 Center。

---

## 八、常见问题

### 1. Docker Hub 连接超时

```
failed to fetch oauth token: dial tcp 104.244.46.63:443: connectex: ...
```

**原因**：国内无法访问 Docker Hub。

**解决**：配置镜像加速器（见上方第三节）。

### 2. MySQL 端口冲突

```
ports are not available: listen tcp 0.0.0.0:3306: bind: Only one usage of each socket address
```

**原因**：宿主机已安装 MySQL 占用了 3306 端口。

**解决**：修改 docker-compose.yml 中 MySQL 的宿主机端口：

```yaml
ports:
  - "13306:3306"    # 改为其他未被占用的端口
```

### 3. 使用宿主机 MySQL

如果不想用 Docker 里的 MySQL，可以：

1. 删除 docker-compose.yml 中的 `mysql` 服务
2. 修改所有配置文件中的 `jdbc.url`：

```properties
jdbc.url=jdbc:mysql://host.docker.internal:3306/sunrise
```

> `host.docker.internal` 是 Docker 提供的特殊域名，容器内通过它访问宿主机网络。

### 4. 客户端连接不上 External 服务

**原因**：`external.address` 配置不正确。

**解决**：确保 `external.address` 是客户端能访问到的 IP 地址，不能是容器名或 `127.0.0.1`（远程部署时）。
