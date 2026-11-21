# 迭代路线图 · 多端业务中台套件

## 1. 当前版本：v1.0.0（作品集交付版）

### 1.1 已交付范围

**后端（Spring Boot 3.3.5 + Java 21 + MyBatis-Plus + H2/MySQL）**

- [x] 统一响应体 `Result<T>` / 全局异常 / `@Valid` 参数校验 / `traceId` 链路
- [x] 错误码字典（40001~50401，HTTP 状态取前三位）
- [x] JWT 双 token（access 7200s / refresh 604800s）+ 退出黑名单
- [x] RBAC：用户-角色-菜单/按钮权限，`@RequirePerm` 切面鉴权，动态路由返回
- [x] 内容中心状态机（草稿→待审→发布/驳回→下架）+ 分类树 + 标签
- [x] 地理资源点位：区域树 + 边界框查询 + Haversine 附近点位（含距离文案）
- [x] 活动/场次/报名：名额条件更新并发扣减 + 唯一键幂等 + 核销码核销与日志
- [x] 统计看板：SQL 聚合（总量/趋势/分类占比/区域分布/场次转化率）
- [x] AI 抽象层：Mock Provider + OpenAI 兼容 Provider + 失败自动降级 + 简易 RAG 引用
- [x] 缓存抽象：内存实现（穿透空值缓存 + TTL 抖动防雪崩），预留 Redis 实现
- [x] 对象存储抽象：本地实现，预留 OSS/S3
- [x] 微信登录（mock 模式）+ 小程序用户自动注册
- [x] 系统信息接口 + 操作日志分页接口
- [x] 单元测试 **45 个，0 失败**（鉴权 9 / 内容状态机 7 / 地理 5 / 分页 4 / AI 5 / 内容服务 8 / 报名并发 7）

**管理后台（Vue 3.5 + Vite 5 + TS + Element Plus + Pinia + ECharts）**

- [x] 登录页（演示账号一键填充）+ 左侧深色菜单 + 顶部面包屑/角色标识/后端健康状态
- [x] 后端动态路由渲染 + 按钮级 `v-if`/禁用权限控制
- [x] 看板：9 张指标卡 + 4 张 ECharts 图（趋势/分类/区域/转化率）+ 7/14/30 天切换
- [x] 内容管理：分页/搜索/分类/状态筛选 + 新增编辑弹窗 + 提审/发布/驳回/下架/删除
- [x] 分类管理（树表）、资源点位（类型/经纬度/区域）、活动与场次（展开行）、报名核销（扫码/输码）
- [x] 用户管理（重置密码）、角色权限（菜单树勾选）、系统配置（运行时信息 + 操作日志）
- [x] AI 助手（摘要/标题生成 + 内容库问答）
- [x] axios 封装：token 注入、401 静默刷新（单飞）、统一错误 toast（带 traceId）
- [x] 加载/空/错误三态组件，表格分页与请求取消

**微信小程序（原生）**

- [x] 5 个页面：登录、首页（tabBar）、内容列表、内容详情、个人中心（tabBar）
- [x] `wx.request` 封装（token、统一错误、401 跳登录）+ `wx.login` 登录 + 本地存储会话
- [x] 页面生命周期（onLoad/onShow/onPullDownRefresh/onReachBottom）
- [x] 下拉刷新、上拉加载、分享、阅读量上报

**工程化与文档**

- [x] Docker Compose（mysql + backend + frontend + 可选 redis）、两个 Dockerfile、Nginx 配置
- [x] `.env.example`、`.gitignore`、MIT LICENSE
- [x] docs：requirements / architecture / database（ER 图 + 18 表全字段）/ api / deployment / roadmap / ai-coding
- [x] `docs/screenshots/` 真实运行截图（登录页、数据看板、内容管理）

### 1.2 技术栈实际版本

| 层 | 技术 | 实际版本 |
|---|---|---|
| 后端 | Spring Boot | 3.3.5 |
| 后端 | Java | 21 |
| 后端 | MyBatis-Plus | 3.5.7 |
| 后端 | JJWT | 0.12.6 |
| 后端 | H2 / MySQL | 2.2.x（测试、本地）/ 8.0（生产） |
| 前端 | Vue | 3.5.x |
| 前端 | Vite | 5.4.x |
| 前端 | TypeScript | 5.6.x |
| 前端 | Element Plus | 2.8.x |
| 前端 | Pinia / Vue Router | 2.2.x / 4.4.x |
| 前端 | ECharts | 5.5.x |
| 前端 | axios | 1.7.x |

## 2. 已知未完成项（诚实说明）

| 项 | 现状 | 原因/后续 |
|---|---|---|
| 小程序 uni-app + H5 构建 | **未做**，改为原生小程序结构 | 任务要求为原生目录结构与 5 个页面，且明确不需要真机编译；若需 H5 演示可后续用 uni-app 重写（约 1~2 天） |
| 真实微信登录 | mock 模式，未接 `jscode2session` 实网调用 | 需企业主体小程序 AppID/Secret 与合法 HTTPS 域名，属于部署环境依赖；代码已预留（`APP_WECHAT_MOCK=false` 即启用真实调用） |
| Redis 缓存实际运行 | 抽象与配置就绪，默认走内存实现 | 本机无 Redis 服务；compose 中已提供 `redis` 服务（`--profile cache`） |
| 对象存储 OSS/S3 | 接口与配置就绪，默认本地磁盘 | 需云账号与 Bucket，属环境依赖 |
| 操作日志自动切面 | 表 + 分页接口 + 演示数据已完成，**未做自动记录切面** | 当前由业务代码手动写入；下一步用 `@Log` 注解 + AOP 统一采集 |
| 小程序地图页 | 附近点位接口已就绪，未做 `map` 组件页 | 需在小程序后台配置位置权限与腾讯地图 Key |
| 真实支付/短信 | 未涉及 | 超出本项目业务范围 |
| 自动化 E2E | 未做（仅后端 45 单测 + 手工验证） | 计划引入 Playwright 覆盖登录→内容发布→报名核销主链路 |
| CI/CD | 未配置 | 计划 GitHub Actions：mvn test + npm build + 镜像构建 |

## 3. 后续规划

### v1.1（工程完备性，约 1 周）

- [ ] `@Log` 注解 + AOP 自动写 `operation_log`（记录 user/traceId/耗时/结果）
- [ ] 请求参数脱敏、限流（登录接口按 IP + 账号双维度）
- [ ] 内容中心增加版本历史与富文本图片上传（复用 `StorageService`）
- [ ] 前端表格组件封装（`useTable` composable：分页/搜索/重置/取消请求）
- [ ] Playwright E2E：登录 → 发布内容 → 小程序端可见 → 报名 → 核销
- [ ] GitHub Actions：后端测试 + 前端构建 + Docker 镜像

### v1.2（业务纵深，约 2 周）

- [ ] 订单/支付：报名费支付流水、退款、对账（对接微信支付）
- [ ] 数据大屏：WebSocket 实时看板、地图热力图（点位聚类）
- [ ] 遥感数据服务：地块绘制（GeoJSON）+ 面积计算 + 影像切片展示
- [ ] 消息中心：站内信 + 小程序订阅消息（报名成功/开赛提醒）
- [ ] 多租户：数据行级隔离（按租户 id + MyBatis-Plus 插件）

### v2.0（平台化，1~2 个月）

- [ ] 微服务拆分：内容服务 / 活动服务 / 地理服务 / AI 服务（Spring Cloud Alibaba 或 Kubernetes）
- [ ] 统一网关：认证、限流、灰度
- [ ] 可观测性：Prometheus + Grafana + 链路追踪（OpenTelemetry）
- [ ] 内容审核接入第三方（敏感词 + 图片审核）
- [ ] 小程序端升级 uni-app，一套代码输出微信小程序 + H5 + App

## 4. 版本记录

| 版本 | 日期 | 内容 |
|---|---|---|
| v1.0.0 | 2026-11 | 首个作品集交付版：后端 45 测试全绿、管理后台 10 个业务页面、小程序 5 页、文档 7 篇、3 张真实截图 |
