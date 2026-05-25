# gmback-ui

GM 后台独立前端（Vite + Vue 3 SPA）。**gmback 仅提供 REST API**，不托管静态页面；生产环境由 Nginx 托管 `dist/` 并将 `/api` 反代到 gmback。

## 架构说明

| 组件 | 说明 |
|------|------|
| **gmback-ui** | 浏览器访问的静态站点 + Vue Router（History 模式） |
| **gmback** | Javalin API，路径均为 `/api/...`，端口由 `admin.port` 配置（默认 `8010`） |

前端所有请求使用**相对路径**（如 `/api/login`），因此生产环境需保证页面与 API **同源**（同一域名/端口），由 Nginx 统一入口：

```
浏览器 → Nginx:443/80
           ├─ /、/monitor、…     → dist/（SPA，try_files 回退 index.html）
           └─ /api/*             → http://127.0.0.1:8010（gmback）
```

开发时由 Vite 的 `server.proxy` 完成同样的 `/api` 转发，无需 Nginx。

## 环境要求

- Node.js 18+
- 已启动 gmback（`GmBackServer` 或 `RunAllOne`，`config/*.properties` 中 `admin.port`，默认 `8010`）

## 开发

```bash
# 终端 1：启动 gmback（admin.port 默认 8010）

# 终端 2
cd gmback-ui
npm install
npm run dev
```

浏览器：**http://localhost:5173/**

`.env.development`：

```properties
VITE_DEV_PORT=5173
VITE_API_PROXY_TARGET=http://127.0.0.1:8010
```

`vite.config.js` 会把开发环境的 `/api` 代理到 `VITE_API_PROXY_TARGET`，与生产 Nginx 行为一致。

## 生产构建

```bash
cd gmback-ui
npm install
npm run build
```

产物在 **`dist/`**（`index.html` + `assets/`）。将**整个 `dist` 目录**部署到服务器，不要只拷贝部分文件。

`.env.production` 中的 `VITE_API_PROXY_TARGET` 仅作构建期参考；**运行时 API 地址由 Nginx 反代决定**，无需在构建产物里写死后端地址。

---

## Nginx 部署

### 1. 前置条件

1. gmback 已启动且本机可访问，例如：`curl http://127.0.0.1:8010/api/server-status`（需带有效 `Authorization` 或先测 `/api/login`）。
2. 已将 `dist/` 放到服务器，例如 `/var/www/gmback-ui/dist`。
3. 确认 `config` 里 `admin.port` 与下面 `proxy_pass` 端口一致。

### 2. 最小配置（HTTP）

将 `root` 改为你的 `dist` 绝对路径，将 `8010` 改为实际 `admin.port`：

```nginx
server {
    listen 80;
    server_name gm.example.com;   # 改为你的域名或 _

    root /var/www/gmback-ui/dist;
    index index.html;

    # Vue Router History：先找静态文件，否则回退 SPA 入口
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 带 hash 的构建资源可长期缓存（文件名含内容 hash）
    location /assets/ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    # 所有 API 转发到 gmback（保留 /api 前缀，勿在 proxy_pass 末尾加 /）
    location /api/ {
        proxy_pass http://127.0.0.1:8010;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # 前端通过 Authorization 头传递 JWT，无需额外配置
    }
}
```

重载配置：

```bash
nginx -t && nginx -s reload
```

访问：**http://gm.example.com/**（与 gmback 同源，Cookie / `localStorage` 与 `/api` 请求均走该域名）。

### 3. HTTPS（推荐）

在 80 上跳转 443，并为 443 使用与上文相同的 `root`、`location /`、`location /api/`：

```nginx
server {
    listen 80;
    server_name gm.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name gm.example.com;

    ssl_certificate     /etc/nginx/cert/fullchain.pem;
    ssl_certificate_key /etc/nginx/cert/privkey.pem;

    root /var/www/gmback-ui/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /assets/ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8010;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 4. gmback 不在本机时

若 API 在另一台机器或容器：

```nginx
location /api/ {
    proxy_pass http://10.0.0.5:8010;   # 改为 gmback 实际地址:端口
    # ... 其余 proxy_set_header 同上
}
```

确保该地址从 Nginx 所在主机可达；**不要将 `8010` 直接暴露到公网**，优先只通过 Nginx 访问。

### 5. 可选：限制访问来源

GM 后台建议仅内网或 VPN 访问，可在 `server` 内增加：

```nginx
allow 10.0.0.0/8;
allow 192.168.0.0/16;
deny all;
```

### 6. 配置注意点

| 项 | 说明 |
|----|------|
| **`try_files`** | 必须包含 `/index.html`，否则刷新 `/monitor` 等路由会 404 |
| **`proxy_pass`** | 使用 `http://127.0.0.1:8010` **不要**写成 `http://127.0.0.1:8010/`，否则会去掉 `/api` 前缀导致后端 404 |
| **`location /api/`** | 与前端请求路径一致；也可写 `location /api { ... }`，效果相同 |
| **子路径部署** | 当前 `createWebHistory()` 未设置 `base`，仅支持站点根路径 `/`。若必须挂在 `https://domain.com/gm/`，需改 `vite.config.js` 的 `base` 与 `router` 的 `history` base，并调整 Nginx `alias`/`try_files`，本仓库默认未启用 |
| **防火墙** | 公网只开放 80/443；`admin.port` 仅本机或内网监听 |

### 7. 常见问题

**刷新子路由返回 404**  
→ 检查 `location /` 是否配置了 `try_files ... /index.html`。

**登录后接口 502 / Connection refused**  
→ gmback 未启动或 `proxy_pass` 端口与 `admin.port` 不一致。

**接口 404 且路径像 `/login` 而不是 `/api/login`**  
→ `proxy_pass` 末尾多写了 `/`，导致 `/api` 被剥离。

**接口 401**  
→ 正常鉴权失败或 JWT 过期；检查请求头是否带 `Authorization`（前端 `apiFetch` 已自动附加）。

**静态资源 404**  
→ `root` 必须指向 **`dist` 目录本身**（其下应有 `index.html` 和 `assets/`），不要指到 `gmback-ui` 项目根。

---

## 目录结构

```
gmback-ui/
├── index.html              # 唯一 HTML 入口
├── vite.config.js
├── .env.development
├── .env.production
└── src/
    ├── main.js             # 应用入口
    ├── App.vue             # 根组件（会话门闸）
    ├── api/
    │   └── client.js       # apiFetch、鉴权 token
    ├── assets/
    │   └── styles/         # 全局样式（Tailwind + EP + 布局/业务）
    ├── components/
    │   └── layout/         # 侧栏、顶栏等可复用布局组件
    ├── composables/
    │   └── useAuth.js      # 登录、权限、会话
    ├── constants/
    │   ├── menu.js         # 菜单与路由元数据
    │   └── menu-icons.js
    ├── layouts/
    │   └── MainLayout.vue  # 登录后主壳
    ├── plugins/
    │   └── element-plus.js # 按需注册 EP 组件
    ├── router/
    │   ├── index.js
    │   ├── routes.js
    │   └── guards.js
    ├── utils/index.js      # 工具函数（API 响应、分页、对话框等，统一导出）
    └── views/              # 业务页面（按路由懒加载）
        ├── auth/
        │   └── LoginView.vue
        ├── Monitor.vue
        └── ...
```

## 新增页面

1. 在 `src/views/` 添加 `FooBar.vue`。
2. 在 `src/constants/menu.js` 的 `MENU_ITEMS` 中增加一项（`key` 与组件名对应：`foo_bar` → `FooBar.vue`）。
3. 路由与懒加载由 `router/routes.js` 自动生成，无需改 Java 后端。

```js
import {reactive, toRefs, onMounted} from 'vue';
import {ElementPlus} from '@/plugins/element-plus';
import {apiFetch, isApiSuccess, apiMsg} from '@/utils';

export default {
    setup() {
        // ...
        return {...toRefs(state)};
    },
};
```

## 路径别名

`@` → `src/`（见 `vite.config.js`）

## 技术栈

`vue`、`vue-router`、`element-plus`、`@element-plus/icons-vue`、`tailwindcss`
