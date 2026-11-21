# AI Coding 实践记录 · 多端业务中台套件

本项目在需求拆解、代码生成、测试补齐、文档撰写等环节使用了 AI 编程助手（DeepSeek / Claude 类工具），
但**架构决策、接口契约、并发与安全逻辑均由人工评审确认**。本文记录真实用法与踩过的坑，作为工程实践的一部分。

## 1. 工作流：从一句话需求到可交付代码

```
需求 → 拆解（AI 出草案，人确认）→ 契约先定（DTO/接口/错误码）→ 分批实现（AI 写码，人 review）
     → 测试补齐（AI 写用例，人确认断言）→ 联调（前端/小程序对后端）→ 文档（AI 起草，人校对事实）
```

关键原则：

1. **先定契约再写代码**。让 AI 一次性输出「表结构 → DTO → 接口 → 错误码」的草案，人工裁剪后再逐模块实现，避免边写边改字段。
2. **小步提交、按文件 review**。每次只让 AI 改 1~3 个文件，改完立刻编译/测试，不积压大 diff。
3. **测试是验收标准**。要求 AI「先给失败用例，再给实现」，人确认断言是否覆盖边界（并发、状态机非法流转、权限缺失）。
4. **不信任生成内容中的数字**。README/文档里的测试数、版本号、截图必须在本地实测后回填。

## 2. 需求拆解模板（实际使用）

```text
【背景】贵州派诺数海观澜 · 数字内容 + 遥感数据 + 文旅活动业务
【目标】实现 <模块>，满足 <角色> 的 <场景>
【输入】表结构：<粘贴相关表 DDL>
【约束】
  - 技术栈：Spring Boot 3.3 + Java 21 + MyBatis-Plus；统一 Result<T>
  - 必须：参数校验、权限注解、分页、事务边界明确
  - 禁止：新增依赖、改已有公共类签名、SQL 拼接
【输出】1) 接口清单（方法/路径/权限）2) DTO 字段 3) 关键 SQL 4) 边界与错误码 5) 测试用例清单
```

先要「接口清单 + DTO + 错误码」，确认后再要「实现 + 测试」，比一次性要求整模块代码的质量高得多。

## 3. Review 清单（每批 AI 代码必查）

**后端**

- [ ] 事务注解是否加在 public 方法、异常是否触发回滚（`@Transactional(rollbackFor = Exception.class)`）
- [ ] 并发扣减是否用「条件更新 + 影响行数判断」，而不是先查后改
- [ ] 唯一键冲突是否转成业务错误码（而不是 500）
- [ ] 分页 `size` 是否有上限（本项目 `PageQuery` 上限 100）
- [ ] 权限注解是否与菜单/按钮种子数据一致（新增接口必须同步 `sys_menu`）
- [ ] 日志是否含 `traceId`、是否泄露手机号/密码/密钥
- [ ] 时间字段是否统一 `yyyy-MM-dd HH:mm:ss` + `Asia/Shanghai`

**前端**

- [ ] 组件路径是否与 `sys_menu.component` 完全一致（动态路由靠字符串匹配）
- [ ] 请求是否走统一封装（token、错误 toast、取消），而不是裸 `fetch`
- [ ] 是否有加载/空/错误三态，而不是白屏
- [ ] 权限按钮是否用 `auth.can('xxx:yyy')` 控制
- [ ] 表单校验规则与后端 `@Valid` 是否一致
- [ ] TypeScript 是否有 `any` 逃逸、未使用变量

**小程序**

- [ ] 每个页面四件套（js/json/wxml/wxss）齐全且注册进 `app.json`
- [ ] 请求 token 注入与 401 跳登录
- [ ] 下拉刷新要 `wx.stopPullDownRefresh()`，上拉加载要有 `finished` 终止条件
- [ ] iOS 日期解析兼容（`-` → `/`）
- [ ] tabBar 页面跳转用 `wx.switchTab`，非 tabBar 用 `navigateTo`

## 4. 真实踩坑与修正

| # | 坑 | 现象 | 修正 |
|---|---|---|---|
| 1 | PowerShell 写文件带 BOM | `javac` 报 `illegal character '\ufeff'` | 源码一律用文件写入工具（UTF-8 无 BOM），禁止 `Set-Content`/`Out-File` |
| 2 | 前端工具函数类型与用法不一致 | `format.ts` 里 `CONTENT_TYPE` 定义为 `Record<string,string>`，视图按 `{text,type}` 取用导致 TS 报错 | 统一改为 `Record<string, StatusMeta>`，并让 `statusMeta()` 同时支持 number/string 键 |
| 3 | 动态路由组件路径拼错 | 菜单 `component` 写 `content/list` 但文件在 `views/content/index.vue`，页面空白 | 建立「菜单 component ↔ views 路径」对照表，路由注册时对缺失组件回退 404 页并 `console.warn` |
| 4 | 401 刷新死循环 | 刷新接口自身 401 时再次触发刷新 | 刷新用裸 axios（不经过拦截器）+ 模块级 `refreshPromise` 单飞 + `_retried` 标记 |
| 5 | H2 与 MySQL 方言差异 | `INSERT IGNORE`、`AUTO_INCREMENT`、`DATEADD` 在对方库报错 | 维护两套种子脚本（`db/*.sql` 与 `classpath:db/h2/*.sql`），测试只加载 H2 版本 |
| 6 | 报名并发超卖 | 先查 `remain > 0` 再更新，压测出现负库存 | 改为 `UPDATE ... SET remain = remain - 1 WHERE id=? AND remain>0` + 影响行数判断，测试用线程池并发验证只成功 `capacity` 次 |
| 7 | 菜单新增导致角色路由断言失败 | 新增菜单后按角色过滤的路由树断言变化 | 新菜单只授权给 admin，且先跑测试确认 editor/operator 断言不变 |
| 8 | 截图空白 | 直接对 hash 路由截图时页面还没渲染完 / 未登录被守卫踢回登录页 | 截图脚本先通过 API 登录拿 token、注入 localStorage，再导航并等待渲染（`Page.captureScreenshot` 前 sleep / 等待元素） |
| 9 | ECharts 容器尺寸为 0 | 在 `v-if` 隐藏容器里初始化，图表不显示 | `nextTick` + 数据就绪后再 `init`，并监听 `window.resize`、卸载时 `dispose` |
| 10 | Element Plus 图标未注册 | `Unknown custom element: <DataBoard>` | `main.ts` 全量注册 `@element-plus/icons-vue`，或按需局部导入 |
| 11 | `npm install` 在受限网络下极慢 | 长时间无 `package-lock.json` | 复用已有 `node_modules`、设置镜像源；构建前确认依赖目录完整 |
| 12 | 文档数字与实测不符 | AI 起草的 README 写了「测试全部通过」但没数字 | 交付前必须本地跑 `mvn test` 并把输出中的 `Tests run: N` 填进 README |

## 5. 提示词示例（可直接复用）

**写单体服务**

```text
你是 Java 后端工程师。在现有 Spring Boot 3.3 项目中实现【活动报名服务】：
- 已有：ActivitySignupMapper、PageQuery、Result<T>、BizException(错误码)
- 要求：
  1) @Transactional 方法 create(userId, req)：校验活动状态/报名时间窗 → 条件扣减名额 → 生成核销码 → 插入报名单
  2) 名额为 0 抛 42202；唯一键冲突抛 42203；时间窗不符抛 42204
  3) 不新增依赖，不改已有类签名
返回：完整类代码 + 需要新增的测试用例清单（含并发场景）
```

**写前端页面**

```text
你是 Vue 3 + TS + Element Plus 工程师。实现「活动管理」页面：
- 接口：listActivities({page,size,keyword,status}) / createActivity / updateActivity / createSession ...
- 要求：筛选栏 + el-table（含 type=expand 展示场次）+ 新增/编辑 el-dialog + 分页
- 复用 @/components/DataState（loading/error/empty 三态）、@/utils/format（状态映射）
- 权限：auth.can('activity:write') 控制按钮
- 时间字段用 el-date-picker type=datetime value-format="YYYY-MM-DD HH:mm:ss"
输出：单文件 SFC，scoped 样式，无 any
```

**写测试**

```text
为 SignupService 写 JUnit 5 + Spring Boot Test 用例，覆盖：
1) 正常报名扣减 1 个名额
2) 名额为 0 抛 42202
3) 同用户同场次重复报名抛 42203（唯一键兜底）
4) 20 线程并发抢 5 个名额，最终 remain=0 且成功插入 5 条
使用 @SpringBootTest + test profile（H2），数据准备用现有种子或 @Sql
```

**代码 review**

```text
请以资深 reviewer 视角 review 这段 diff，只挑「会导致 bug / 安全问题 / 明显性能问题」的点，
按严重程度排序并给出可直接应用的修改；不要重写整个文件，只给最小 patch。
```

## 6. 人机分工

| 环节 | AI | 人 |
|---|---|---|
| 需求拆解 | 出草案、列遗漏点 | 定优先级、裁剪范围 |
| 架构 | 给常见方案对比 | 做最终选型（如 Mock 优先于真实三方） |
| 编码 | 生成 70%~80% 常规代码 | 关键并发/安全逻辑逐行确认 |
| 测试 | 生成用例骨架 | 补边界与并发场景 |
| 文档 | 起草结构与措辞 | 核对版本、数字、路径真实性 |
| 验收 | 无 | 实跑命令、截图、对照验收清单 |

结论：AI 显著提升了样板代码和文档的产出速度，但**契约设计、并发正确性、权限边界、交付事实核对**仍必须由人负责，这也是本项目把「测试全绿 + 真实截图 + 可复现命令」作为交付底线的理由。
