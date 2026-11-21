# 需求文档 · 多端业务中台套件

## 1. 项目背景与对标

| 项 | 内容 |
|---|---|
| 对标公司 | 贵州派诺数海观澜数字产业发展有限公司 |
| 对标岗位 | 全栈开发工程师（Java / Vue / 微信小程序） |
| 公司业务 | 数字文化创意软件开发、数据处理与存储、AI 应用软件开发、大数据服务、物联网技术服务、**卫星遥感数据处理与地理遥感信息服务**、互联网直播技术服务 |
| 本项目业务场景 | 面向「数字内容 + 数据服务」型公司的业务中台：**文旅内容发布 + 遥感地块/文旅资源点位管理 + 活动预约报名 + 核销 + 数据看板 + AI 助手** |
| 交付形态 | Spring Boot 后端 + Vue3 运营后台 + 微信小程序端，三端共用一套 RESTful API |

选择这个场景的原因：公司经营范围同时包含「数字内容（文旅/直播/动漫）」和「卫星遥感数据服务」两条线，
而内容审核发布、地理点位聚合、预约报名核销、数据看板正是这类公司最典型的内部系统需求；
同时「三端一体」能完整呈现 JD 要求的 Java + Vue + 小程序全栈能力。

## 2. 用户角色与典型场景

| 角色 | 说明 | 典型操作 |
|---|---|---|
| 超级管理员 `admin` | 系统 owner，拥有全部权限 | 用户/角色/菜单授权、系统配置与操作日志、内容终审、全部数据看板 |
| 内容运营 `editor` | 负责内容生产与初审 | 新建/编辑内容、提交审核、管理分类与标签、使用 AI 生成摘要与标题 |
| 活动运营 `operator` | 负责活动与现场核销 | 创建活动与场次、查看报名单、扫码/输入核销码核销 |
| 小程序游客 | 未登录的微信用户 | 浏览首页内容流、搜索资讯、查看活动、阅读详情 |
| 小程序用户 | 微信登录用户 | 活动报名、查看「我的报名」、分享内容 |

主要用例：

1. 运营在后台完成「草稿 → 待审 → 已发布 →（下架）」的内容全流程，小程序端立即可见。
2. 用户在小程序选择活动场次报名，系统用 `UPDATE ... WHERE remain > 0` 保证并发下不超卖。
3. 活动运营在核销页输入 8 位核销码完成核销，结果写入核销日志。
4. 管理员在数据看板查看内容趋势、分类占比、区域分布与场次转化率。
5. 内容编辑调用 AI 助手生成摘要/标题建议，无 API Key 时自动降级为本地 mock，保证离线可跑。

## 3. 功能需求清单

### A. 后端（Spring Boot 3.3 + MyBatis-Plus）

- [x] 统一响应体 `Result<T>`（`code/message/data/traceId/timestamp`）与全局异常处理
- [x] 参数校验（`@Valid` + 自定义状态机校验），统一错误码字典
- [x] traceId 贯穿请求链路（响应体返回，便于前端报错定位）
- [x] RESTful 资源命名 + `page/size/sort` 分页 + 关键字/状态/分类过滤
- [x] 内容中心：内容 CRUD、分类树、标签、封面、置顶、排序、阅读量
- [x] 内容审核状态机：草稿 → 待审 → 已发布 → 下架，驳回带原因
- [x] 点位管理：地理点位 CRUD、区域树、按边界框/关键字查询、按经纬度计算距离（Haversine）
- [x] 活动与场次：活动 CRUD、场次 CRUD、报名开放时间判断
- [x] 预约报名：报名、我的报名、取消、核销码生成、核销（并发安全）
- [x] RBAC：用户 / 角色 / 菜单 / 按钮权限，动态菜单树，`@RequirePerm` 注解鉴权
- [x] JWT 登录：access token（2h）+ refresh token（7d），token 黑名单（登出即失效）
- [x] 统计看板：总量指标、内容趋势、分类占比、区域分布、场次报名转化率
- [x] 微信小程序登录：`code2session` 抽象 + mock 模式（无 appId 可离线演示）
- [x] 对象存储适配层：本地实现 + 可切换 OSS（`StorageService` 接口）
- [x] AI 能力：`AiService` 抽象 + MockProvider + OpenAI 兼容 Provider，摘要/标题/问答（简易 RAG）
- [x] 缓存：热点内容缓存 + 空值缓存防穿透 + 随机 TTL 防雪崩，内存/Redis 双实现
- [x] 系统信息与操作日志查询接口（`/api/system/info`、`/api/system/logs`）

### B. 运营后台（Vue 3 + Vite + TypeScript）

- [x] 登录页（演示账号一键填充）、JWT 持久化
- [x] 动态路由与菜单：登录后按接口返回的菜单树注册路由，无权限菜单不出现
- [x] 布局：深色侧栏、面包屑、折叠、后端健康检查指示灯
- [x] 数据看板：9 项指标卡 + ECharts 趋势/占比/分布图 + 转化率表格
- [x] 内容管理：分页/搜索/筛选、新增编辑弹窗、提审/发布/驳回/下架/删除
- [x] 分类管理：树表、子分类、启用停用
- [x] 点位管理：区域筛选、点位类型、经纬度编辑
- [x] 活动管理：活动 + 展开行场次管理（新增/编辑/删除场次）
- [x] 报名核销：输入核销码核销 + 报名单分页筛选
- [x] 用户管理：新增/编辑/重置密码/删除、角色多选
- [x] 角色权限：菜单树勾选（含按钮权限）、半选节点回填
- [x] 系统配置：运行时信息 + 操作日志分页查询
- [x] AI 助手：摘要/标题生成 + 内容库问答（带 provider/model/token/耗时展示）
- [x] axios 封装：token 注入、401 单飞刷新重试、统一错误 toast（带 traceId）
- [x] 加载态（skeleton）/ 空态（empty）/ 错误态（重试）三态组件
- [x] Pinia 状态管理、Element Plus 中文语言包、hash 路由

### C. 微信小程序端（原生）

- [x] 首页：搜索、分类入口、近期活动、精选内容流
- [x] 资讯列表：分类 tab、关键字搜索、下拉刷新、上拉加载更多、到底提示
- [x] 内容详情：富文本渲染、阅读量 +1、分享
- [x] 我的：登录态、报名统计、我的报名列表、退出登录
- [x] 登录页：`wx.login` 换 token、演示账号说明
- [x] `utils/request.js`：wx.request 封装（Bearer token、统一错误 toast、401 跳登录）
- [x] `utils/auth.js`：登录、会话读写、`ensureLogin`
- [x] `utils/format.js`：时间格式化、状态文案、富文本降级
- [x] tabBar 三页导航、页面生命周期（onLoad/onShow/onPullDownRefresh/onReachBottom）

### D. 工程与文档

- [x] MySQL 8 建表 + 种子数据（`db/schema.sql`、`db/data.sql`）
- [x] H2（MySQL 模式）测试库，`mvn test` 不依赖外部服务
- [x] 45 个后端自动化测试（并发扣减、状态机、RBAC、AI 兜底、分页过滤等）
- [x] Docker Compose 编排（MySQL + 后端 + Nginx 前端 + 可选 Redis）
- [x] 文档 7 篇 + 真实运行截图 3 张
- [x] AI Coding 使用记录（`docs/ai-coding.md`）

## 4. 非功能需求

| 维度 | 要求 | 实现方式 |
|---|---|---|
| 安全 | 密码不可逆存储、接口鉴权、越权拦截 | PBKDF2 加盐散列；JWT + 拦截器 + `@RequirePerm`；按钮级权限 |
| 并发 | 名额不超卖、重复报名不产生脏数据 | 条件更新 `remain > 0` + 数据库唯一键 `(session_id,user_id,status)` 兜底 |
| 性能 | 列表分页、热点数据缓存 | 分页查询走索引；内容详情缓存 + 空值缓存 + TTL 抖动 |
| 可用性 | 外部依赖不可用时系统仍可演示 | AI/微信/存储/缓存全部有 mock 或内存实现，全部可配置切换 |
| 可观测 | 错误可定位 | 统一错误码 + traceId + 操作日志表 + prod 滚动日志 |
| 可移植 | 本地无 MySQL/Docker 也能跑 | `local` profile 使用 H2 文件库；`prod` profile 使用 MySQL |
| 可维护 | 分层清晰、注释中文 | controller/service/mapper/entity/dto 分离；领域服务承载状态机与并发逻辑 |

## 5. JD 原文要点逐条映射

> 来源：贵州派诺数海观澜数字产业发展有限公司《全栈开发工程师》岗位 JD。
> 表中路径均相对于仓库根目录。

| # | JD 原文要点 | 本项目实现位置 | 说明 |
|---|---|---|---|
| 1 | 业务系统前后端开发、微信小程序开发及日常维护 | `backend/`、`frontend/`、`miniprogram/` | 三端齐全，接口共用 |
| 2 | 需求分析、技术方案设计、功能实现 | `docs/requirements.md`、`docs/architecture.md` | 需求→架构→实现链路完整 |
| 3 | 后端业务逻辑、API 接口、数据库设计、第三方服务对接 | `backend/src/main/java/com/painuo/suite/service/`、`controller/`、`db/schema.sql`、`ai/`、`storage/`、`wechat` 登录 | 含微信登录、对象存储、AI 三类第三方适配 |
| 4 | Web 前端页面、交互、小程序开发与优化 | `frontend/src/views/`、`frontend/src/layout/`、`miniprogram/pages/` | 12 个后台页面 + 5 个小程序页面 |
| 5 | 熟练运用 AI Coding 工具辅助编码/调试/重构/排查 | `docs/ai-coding.md` | 记录本项目的拆需求模板、review 清单与踩坑修正 |
| 6 | Java：Spring Boot、Spring MVC、MyBatis、实际项目经验 | `backend/pom.xml`、`controller/`（MVC）、`mapper/`（MyBatis-Plus）、`service/` | Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 |
| 7 | MySQL：独立完成表设计、SQL、基本性能优化 | `db/schema.sql`、`db/data.sql`、`docs/database.md`、`mapper/*.xml` 或注解 SQL | 18 张表、联合索引、聚合统计 SQL、条件更新扣减 |
| 8 | HTML/CSS/JS + Vue2/Vue3 独立完成常规业务页面 | `frontend/src/views/**`、`frontend/src/styles/index.css` | Vue 3.5 组合式 API + 原生 CSS 变量主题 |
| 9 | 微信小程序：生命周期、API 调用、页面交互、基础发布流程 | `miniprogram/pages/**`、`miniprogram/utils/request.js`、`miniprogram/app.js` | onLoad/onShow/onPullDownRefresh/onReachBottom；wx.request/wx.login/wx.setStorage；`docs/deployment.md` 写导入与发布 |
| 10 | RESTful API、前后端分离 | `docs/api.md`、`backend/src/main/java/com/painuo/suite/controller/`、`frontend/src/api/` | 资源化路径、统一响应体、分页规范 |
| 11 | Git、Linux 部署、问题排查 | `docs/deployment.md`、`docker-compose.yml`、`backend/Dockerfile`、`frontend/nginx.conf`、`backend/src/main/resources/application-prod.yml` | systemd/Docker 两套方案、Nginx 反代与 gzip、日志切割 |
| 12 | 加分：TypeScript | `frontend/src/**/*.ts`、`tsconfig.json` | 全量 TS，含接口类型定义 |
| 13 | 加分：Redis | `backend/src/main/java/com/painuo/suite/cache/`、`application-prod.yml` | 缓存抽象 + Redis 实现 + 内存兜底，TTL 抖动/空值缓存 |
| 14 | 加分：Docker / Nginx | `docker-compose.yml`、`frontend/nginx.conf`、`backend/Dockerfile` | 多阶段构建 + 健康检查 + 反向代理 |
| 15 | 加分：AI 大模型 API 接入 / AI Agent | `backend/src/main/java/com/painuo/suite/ai/`、`frontend/src/views/ai/index.vue` | OpenAI 兼容协议、mock 降级、RAG 检索内容库 |
| 16 | 加分：Python | — | 未使用（非本项目技术选型），在 `docs/roadmap.md` 记录为未覆盖项 |

## 6. 验收标准与实测结果

| 验收项 | 标准 | 实测结果 |
|---|---|---|
| 后端单测 | 全绿 | `mvn test` → **Tests run: 45, Failures: 0, Errors: 0** |
| 后端启动 | 能起、能登录、能查列表 | `local` profile（H2）+ `GET /api/ping`、登录、内容列表、看板接口均 200 |
| 管理后台构建 | `npm run build` 成功 | 见 README §10，`vite build` 产出 `dist/` |
| 小程序端 | 目录结构符合原生规范、可导入开发者工具 | `miniprogram/` 含 app.* + 5 个页面四件套 + utils，导入步骤见 `docs/deployment.md` |
| 种子数据 | 登录即见内容与看板 | 14 篇内容、3 个活动、10 个点位、3 个账号、8 条操作日志 |
| 截图 | ≥3 张真实界面 | `docs/screenshots/01-login.png`、`02-dashboard.png`、`03-content-list.png` |
