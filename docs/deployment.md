# 部署文档 · 多端业务中台套件

本文覆盖 4 种运行方式：**本地 H2 一键启动**（最快验证）、**本地 MySQL**、**Docker Compose 全栈**、**Linux 生产部署（systemd + Nginx）**，最后是小程序导入与发布。

## 1. 环境要求

| 组件 | 版本 | 用途 |
|---|---|---|
| JDK | 21（LTS） | 后端编译与运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+（推荐 24） | 前端构建 |
| MySQL | 8.0+ | 生产数据库（本地/测试用 H2，无需安装） |
| Nginx | 1.24+ | 生产静态资源 + API 反向代理 |
| Docker / Compose | 24+ / v2 | 容器化部署（可选） |
| 微信开发者工具 | 稳定版 | 小程序预览与上传 |

## 2. 方式一：本地 H2 一键启动（推荐用于验收）

后端默认 `spring.profiles.default=local`，使用 H2 文件库 `./data/suite`，启动时自动执行
`classpath:db/h2/schema.sql` + `data.sql`，**不需要安装 MySQL**。

```bash
# 1) 启动后端（端口 8080）
cd backend
mvn spring-boot:run
# 或先打包再运行
mvn -DskipTests package
java -jar target/business-suite-backend.jar

# 2) 启动前端（端口 5173，已配置 /api、/uploads 代理到 8080）
cd frontend
npm install
npm run dev
```

验证：

```bash
curl http://127.0.0.1:8080/api/ping
# {"code":0,"message":"ok","data":{"status":"UP","app":"...","profiles":["local"],...}}

curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

浏览器访问：

- 管理后台：<http://127.0.0.1:5173/#/login>
- H2 控制台：<http://127.0.0.1:8080/h2-console>（JDBC URL `jdbc:h2:file:./data/suite`，用户 `sa`，密码空）

默认账号：`admin/admin123`（超级管理员）、`editor/editor123`（内容运营）、`operator/operator123`（活动运营）。

## 3. 方式二：本地 MySQL

```bash
# 1) 建库建表 + 种子数据
mysql -uroot -p < db/schema.sql
mysql -uroot -p business_suite < db/data.sql

# 2) 以 prod 配置启动（application-prod.yml 使用 MySQL，sql.init 为 never）
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=business_suite
export DB_USERNAME=suite
export DB_PASSWORD='your-strong-password'
export APP_JWT_SECRET='至少-32-字节-的随机字符串-用于-HS256-签名'
cd backend && mvn -DskipTests package && java -jar target/business-suite-backend.jar
```

> 生产环境 `spring.sql.init.mode: never`，**表结构与种子数据必须由 DBA/发布脚本显式执行**，
> 避免应用启动时误改生产库。

## 4. 方式三：Docker Compose 全栈

`docker-compose.yml` 编排 4 个服务：

| 服务 | 镜像/构建 | 端口 | 说明 |
|---|---|---|---|
| `mysql` | mysql:8.0 | 内部 3306 | 首次启动自动执行 `db/schema.sql`、`db/data.sql` |
| `backend` | build `./backend` | 内部 8080 | Spring Boot，prod 配置，健康检查 `/api/ping` |
| `frontend` | build `./frontend` | `${WEB_PORT:-8081}:80` | Nginx 托管 dist 并反代 `/api`、`/uploads` |
| `redis` | redis:7-alpine | 内部 6379 | 可选（`profiles: [cache]`） |

```bash
# 1) 准备环境变量（必填 APP_JWT_SECRET 与 MYSQL_ROOT_PASSWORD）
cp .env.example .env
# 编辑 .env：APP_JWT_SECRET 至少 32 字节；MYSQL_ROOT_PASSWORD 设为强密码

# 2) 构建并启动
docker compose up -d --build

# 3) 查看状态与日志
docker compose ps
docker compose logs -f backend

# 4) 访问
open http://127.0.0.1:8081        # 管理后台
curl http://127.0.0.1:8081/api/ping

# 5) 需要 Redis 缓存时
docker compose --profile cache up -d
# 并在 .env 设置 APP_CACHE_TYPE=redis、APP_REDIS_URI=redis://redis:6379
```

`backend/Dockerfile`：`maven:3.9-eclipse-temurin-21` 构建（先 COPY `pom.xml` 再 `dependency:go-offline` 利用层缓存）
→ `eclipse-temurin:21-jre` 运行，`TZ=Asia/Shanghai`，`JAVA_OPTS` 可调堆内存。

`frontend/Dockerfile`：`node:24-alpine` 执行 `npm install` + `npm run build:only` → `nginx:1.27-alpine` 托管。

## 5. 方式四：Linux 生产部署（systemd + Nginx）

### 5.1 目录约定

```
/opt/business-suite/
├── backend/            # business-suite-backend.jar
├── web/                # 前端 dist 内容
├── logs/               # 应用日志（application-prod.yml 的 ./logs 指向此处）
├── data/uploads/       # 本地对象存储根目录（APP_STORAGE_LOCAL_ROOT）
└── .env                # 环境变量（600 权限，不入库）
```

### 5.2 systemd 服务

`/etc/systemd/system/business-suite.service`：

```ini
[Unit]
Description=Multi-Terminal Business Suite Backend
After=network.target mysql.service

[Service]
Type=simple
User=suite
WorkingDirectory=/opt/business-suite/backend
EnvironmentFile=/opt/business-suite/.env
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar business-suite-backend.jar \
  --spring.profiles.active=prod
SuccessExitStatus=143
Restart=always
RestartSec=5
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

```bash
sudo useradd -r -s /sbin/nologin suite
sudo chown -R suite:suite /opt/business-suite
sudo systemctl daemon-reload
sudo systemctl enable --now business-suite
sudo systemctl status business-suite
journalctl -u business-suite -f
```

### 5.3 Nginx 反向代理 + gzip

`/etc/nginx/conf.d/business-suite.conf`：

```nginx
server {
    listen 80;
    server_name admin.example.com;

    gzip on;
    gzip_comp_level 6;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript
               application/xml image/svg+xml;

    root /opt/business-suite/web;
    index index.html;

    # 前端 hash 路由：所有非静态路径回落到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 带指纹的静态资源长缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 后端 API
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }

    # 上传文件
    location /uploads/ {
        proxy_pass http://127.0.0.1:8080/uploads/;
        proxy_set_header Host $host;
        expires 7d;
    }

    location = /healthz {
        access_log off;
        return 200 "ok\n";
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 5.4 日志与切割

- 应用日志：`application-prod.yml` 配置 `logging.file.name=./logs/business-suite.log`，
  logback 按大小滚动：**单文件 50MB、保留 14 天、总量上限 2GB**。
- Nginx 日志：用系统 `logrotate`（`/etc/logrotate.d/nginx`，默认按天切、保留 14 份）。
- 业务操作审计：`operation_log` 表记录动作、路径、耗时、成功与否，可在后台「系统配置」页查看。

```bash
# 手动清理超过 30 天的归档日志
find /opt/business-suite/logs -name "*.log.*.gz" -mtime +30 -delete
```

### 5.5 发布流程

```bash
# 后端
cd backend && mvn -DskipTests package
scp target/business-suite-backend.jar admin@host:/opt/business-suite/backend/
ssh admin@host 'sudo systemctl restart business-suite'

# 前端
cd frontend && npm run build
rsync -avz --delete dist/ admin@host:/opt/business-suite/web/
```

### 5.6 环境变量清单

见根目录 `.env.example`，关键项：

| 变量 | 必填 | 说明 |
|---|---|---|
| `APP_JWT_SECRET` | 是 | HS256 签名密钥，**≥32 字节**，否则启动失败 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | prod 是 | MySQL 连接 |
| `DB_USERNAME` / `DB_PASSWORD` | prod 是 | 数据库账号（最小权限：仅本库 CRUD） |
| `APP_CACHE_TYPE` | 否 | `memory`（默认）/ `redis` |
| `APP_REDIS_URI` | 否 | `redis://host:6379` |
| `APP_AI_PROVIDER` / `APP_AI_BASE_URL` / `APP_AI_API_KEY` / `APP_AI_MODEL` | 否 | 默认 mock |
| `APP_WECHAT_APP_ID` / `APP_WECHAT_APP_SECRET` / `APP_WECHAT_MOCK` | 小程序是 | 生产需关闭 mock |
| `APP_STORAGE_TYPE` | 否 | `local`（默认）/ `oss` |
| `APP_CORS_ALLOWED_ORIGINS` | 生产是 | 逗号分隔的前端域名 |

安全建议：`APP_JWT_SECRET` 用 `openssl rand -base64 48` 生成；`.env` 权限 `600`；数据库账号不使用 root；Nginx 开启 HTTPS（`certbot --nginx`）。

## 6. 微信小程序导入与发布

小程序为**原生目录结构**（非 uni-app），根目录 `miniprogram/`：

```
miniprogram/
├── app.js              # 全局逻辑、globalData（baseUrl/token/user）
├── app.json            # 页面注册、window、tabBar
├── app.wxss            # 全局样式
├── project.config.json # 开发者工具工程配置（appid=touristappid）
├── sitemap.json
├── utils/
│   ├── request.js      # wx.request 封装：token 注入、统一错误、401 跳登录
│   ├── auth.js         # wx.login → /api/wx/login，token/user 存取
│   └── format.js       # 时间/数字/类型文案格式化
└── pages/
    ├── login/index     # 微信一键登录
    ├── index/index     # 首页：分类入口 + 活动 + 内容流（tabBar）
    ├── content/list    # 内容列表：分类 tab、搜索、下拉刷新、上拉加载
    ├── content/detail  # 内容详情：rich-text 正文 + 浏览量上报
    └── mine/index      # 个人中心：我的报名、退出登录（tabBar）
```

### 6.1 导入步骤

1. 启动后端（见方式一），确认 `http://127.0.0.1:8080/api/ping` 正常。
2. 打开**微信开发者工具** →「导入项目」。
3. 目录选择本仓库的 `miniprogram/`（**不是仓库根目录**）。
4. AppID 选择「测试号」或填写自己的小程序 AppID（`project.config.json` 中默认 `touristappid`）。
5. 项目类型：**小程序**；语言：JavaScript；不使用云服务。
6. 导入后如提示「合法域名校验」，勾选**详情 → 本地设置 → 不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**（`project.config.json` 已预设 `urlCheck: false`，本地开发可直接访问 `http://127.0.0.1:8080`）。
7. 编译运行，进入「登录」页点「微信一键登录」；后端 `app.wechat.mock=true` 时无需真实 appid/secret，任意 code 都能换到 token。

> 真机预览：手机与电脑需在同一局域网，把 `miniprogram/utils/request.js` 的 `BASE_URL` 改为电脑局域网 IP
> （如 `http://192.168.1.10:8080/api`），并在真机调试中关闭域名校验。

### 6.2 发布上线

1. 在 [微信公众平台](https://mp.weixin.qq.com) 配置服务器域名：request 合法域名填生产 API 域名（必须 HTTPS）。
2. 后端开启真实微信登录：`APP_WECHAT_MOCK=false`，配置 `APP_WECHAT_APP_ID`、`APP_WECHAT_APP_SECRET`。
   `POST /api/wx/login` 会用 `code` 调 `jscode2session` 换 `openid`，并按 openid 首次自动注册用户（`source=miniapp`）。
3. 开发者工具「上传」→ 填写版本号与备注 → 公众平台「提交审核」→ 审核通过后「发布」。
4. 生产 `request.js` 的 `BASE_URL` 需改为 HTTPS 域名（小程序正式环境不允许 HTTP 与 IP 直连）。

## 7. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 启动报 `app.jwt.secret` 相关错误 | 密钥短于 32 字节 | 换更长的 `APP_JWT_SECRET` |
| 登录返回 `40102/40103` | token 过期或非法 | 前端会自动 refresh；刷新失败则重新登录 |
| 前端页面空白、控制台 404 `/api/...` | 未配置代理或 Nginx 未反代 | 检查 `vite.config.ts` proxy 或 Nginx `location /api/` |
| 刷新页面 404 | 未配置 history fallback | hash 路由本身无此问题；Nginx 需 `try_files ... /index.html` |
| 小程序请求失败 `url not in domain list` | 未关闭域名校验 | 开发者工具详情 → 本地设置 → 勾选不校验 |
| `mvn test` 报 H2 语法错 | 误用 MySQL 版脚本 | 测试只加载 `classpath:db/h2/*.sql` |
| docker compose 起 backend 失败 | 未设置 `APP_JWT_SECRET` | compose 中该变量为必填（`${APP_JWT_SECRET:?...}`） |
