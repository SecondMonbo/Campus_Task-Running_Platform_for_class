# CLAUDE.md

> 本文件描述**当前工作区的真实实现**，供后续开发和 AI 协作使用。
> 接口字段以 `code/development_doc/openapi.yaml` 为准；文档之间冲突时，优先级为：
> **运行中的代码 > openapi.yaml > API约定.md > 前后端接口差异与ID统一.md > 其他说明文档**。
>
> 本文件于 **2026-11-14** 按代码重建。此前版本中“通知/投诉/管理员用户查询未实现”“URL 层不按角色拦截 `/admin/**`”等描述已过时。
>
> **本地文件说明**：当前本机 `.git/info/exclude` 排除了 `CLAUDE.md`、`learn.md` 和 `image_local/`，所以本文件与 `learn.md` 只在本机生效、不进入版本控制。`learn.md` 是用户个人的阅读笔记，**除非用户明确要求，否则不要主动读取、引用或修改它**。

---

## 1. 项目概览

校园跑腿平台，前后端分离的单体应用：

- **前端**：Vue 3 + Vite + Vue Router。开发时 Vite 代理 `/api`、`/actuator` 到后端。
- **后端**：Java 21 + Spring Boot 4.1.1，按 `auth / user / task / accounting / notification / complaint / dispute` 分包的单体应用。
- **数据库**：PostgreSQL 18.6，Flyway 管理表结构，Hibernate `ddl-auto=validate` 只做校验。
- **测试**：后端 H2 内存库 + JUnit 5；前端 Node 内置测试 + 可选 Playwright 浏览器场景。
- **认证**：Spring Security Session + Cookie + CSRF，不使用 JWT。

| 层 | 技术 | 版本/说明 |
| --- | --- | --- |
| 后端运行时 | Java | 21 |
| 后端框架 | Spring Boot | 4.1.1（`spring-boot-starter-webmvc`） |
| 构建 | Maven Wrapper | `code/backend/mvnw`、`mvnw.cmd` |
| 持久化 | Spring Data JPA / Hibernate | `ddl-auto=validate` |
| 迁移 | Flyway | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| 数据库 | PostgreSQL | 18.6（`code/compose.yaml`） |
| 测试库 | H2 | `MODE=PostgreSQL` |
| 前端 | Vue 3 / Vue Router 4 | `vue ^3.5`、`vue-router ^4.6` |
| 前端构建 | Vite 8 | Node `^20.19.0` 或 `>=22.12.0` |

> Spring Boot 4 把 Flyway 自动配置拆到了独立模块。只引 `flyway-core` 时迁移不会执行，
> `ddl-auto=validate` 会因缺表启动失败。`pom.xml` 使用 `spring-boot-starter-flyway`，不要回退。

---

## 2. 常用命令

### 后端（在 `code/backend`）

```powershell
# 运行全部测试（H2，不依赖 PostgreSQL）
.\mvnw.cmd test

# 启动后端（需要先准备 PostgreSQL）
$env:DB_URL = "jdbc:postgresql://localhost:5432/campus_task_platform"
$env:DB_USERNAME = "campus_dev"
$env:DB_PASSWORD = "<本地密码>"
.\mvnw.cmd spring-boot:run

# 打包
.\mvnw.cmd package
```

Bash / macOS / Linux 使用 `./mvnw`。

### 前端（在 `code/frontend`）

```powershell
npm ci
npm run dev        # 连接本机 Spring Boot，默认代理到 http://localhost:8080
npm run dev:demo   # 进程内有状态 Mock，仅认证与 GET/POST /tasks
npm run dev:apifox # 代理到 Apifox 云端 Mock
npm test           # node --test tests/*.test.js
npm run build      # Vite 生产构建
```

### 数据库（在 `code`）

```powershell
Copy-Item .env.example .env   # 然后填写 POSTGRES_PASSWORD
docker compose up -d
```

完整数据库、账号与管理员提权步骤见 `code/README.md`。公开注册只能创建 `USER`；
本地管理员账号需要先注册，再在数据库中受控地把 `role` 改为 `ADMIN`，重新登录生效。

---

## 3. 配置与环境变量

后端 `code/backend/src/main/resources/application.yml`：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/campus_task_platform` | PostgreSQL 连接 |
| `DB_USERNAME` | `campus_dev` | 数据库用户 |
| `DB_PASSWORD` | 空 | 不写入仓库 |
| `SERVER_PORT` | `8080` | 后端端口 |
| `SESSION_COOKIE_SECURE` | `false` | 生产 HTTPS 时应设为 `true` |

其他固定配置：

- Session Cookie：`HttpOnly=true`、`SameSite=Lax`、`Path=/`。
- Actuator 仅暴露 `health`、`info`，健康检查不展示细节。
- JPA：`open-in-view=false`、`ddl-auto=validate`。
- Flyway：`classpath:db/migration`，启动时自动执行迁移。
- 排除了 Spring Boot 自动配置的 `UserDetailsServiceAutoConfiguration`（项目使用自定义登录）。

测试配置位于 `code/backend/src/main/resources/application-test.yml`（注意在 `main/resources`，不在 `test/resources`）：
H2 内存库、`MODE=PostgreSQL`、`DB_CLOSE_DELAY=-1`、Flyway 关闭、`ddl-auto=none`。
各测试类通过 `@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")` 建表。

前端环境变量（`code/frontend/.env.example`）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `API_PROXY_TARGET` | `http://localhost:8080` | Vite 代理目标 |
| `VITE_API_BASE_URL` | 空 | 仅跨域部署时设置，不含 `/api/v1` |

`code/frontend/.env.apifox` 提供 Apifox Mock 目标；`dev:apifox` 会剥离本机 Cookie/Authorization，不保存云端凭证。

---

## 4. 仓库结构

```text
Campus_Task-Running_Platform_for_class/
├── CLAUDE.md                     # 本文件（本机 .git/info/exclude 排除）
├── README.md                     # 占位文件（内容仅为 "this is a readme"）
├── code/
│   ├── README.md                 # 启动/数据库/管理员账号说明
│   ├── compose.yaml              # PostgreSQL 18.6
│   ├── .env.example
│   ├── backend/
│   │   ├── pom.xml
│   │   ├── mvnw / mvnw.cmd
│   │   ├── src/main/java/edu/campus/taskplatform/
│   │   ├── src/main/resources/application.yml
│   │   ├── src/main/resources/application-test.yml
│   │   ├── src/main/resources/db/migration/
│   │   ├── src/test/java/edu/campus/taskplatform/
│   │   └── 任务发布接口核查说明.md
│   ├── frontend/
│   │   ├── package.json
│   │   ├── vite.config.js
│   │   ├── vite.demo.js          # 本地有状态 Mock
│   │   ├── .env.example / .env.apifox
│   │   ├── src/{api,components,composables,domain,views}/
│   │   ├── tests/
│   │   ├── README.md
│   │   ├── FRONTEND_A_WEEK1.md
│   │   └── 任务发布页面说明.md
│   └── development_doc/          # 契约与交付文档
├── docs/王宏涛_需求分析.md        # 需求分析章节
├── image/、image_local/           # 需求/设计图片（image_local 本机排除）
├── 往届同学需求分析例子2023/       # 参考资料
├── learn.md                      # 用户个人笔记（本机排除，不主动读取）
├── note.md                       # 用户中心/举报 Part B 的关键记忆与契约笔记（已入库）
└── .github/modernize/.../architecture-diagram.md  # 旧架构图，内容已过时
```

后端包结构：

```text
edu.campus.taskplatform
├── CampusTaskPlatformApplication
├── config/
│   └── SecurityConfiguration          # 公开路径、Session、CSRF、/admin/** 角色
├── auth/                              # 注册/登录/退出/当前用户/CSRF/管理员用户查询
├── user/                              # 个人中心：资料、密码、评分展示
├── task/                              # 任务主流程、操作日志、评价
├── accounting/                        # 钱包账户、流水、AccountingService
├── notification/                      # 任务通知、系统通知、管理员发布
├── complaint/                         # 投诉提交与管理员处理留痕
├── dispute/
│   ├── cancellation/                  # 取消申请：实体/仓储/服务/控制器/DTO
│   ├── appeal/                        # 已完成任务申诉
│   ├── port/                          # CurrentUserPort/TaskParticipationPort/LedgerPort
│   └── config/                        # 三个端口的真实适配器 + 失败关闭占位配置
└── common/
    ├── ApiResponse / PageResult / ErrorCode / TaskException / GlobalExceptionHandler
    ├── api/                           # 争议模块使用的 ApiResponse/PageResult record
    ├── error/                         # 争议模块使用的 ErrorCode/ApiException
    └── web/Pagination.java            # page/size 统一校验
```

---

## 5. 身份、认证与安全

### 5.1 Session 与当前用户

- 登录成功后 `AuthSessionService.signIn`：
  1. 创建/轮换 Session ID（`changeSessionId`）；
  2. 写入 Spring Security `SecurityContext`（`ROLE_USER` / `ROLE_ADMIN`）；
  3. 向 Session 写入 `CurrentUser(Long userId, String role)`；
  4. 清空旧 CSRF Token。
- Session 属性名固定为 `SessionCurrentUserService.SESSION_ATTRIBUTE = "CURRENT_USER"`。
- 业务代码取当前用户一律走 `CurrentUserService.requireCurrentUser()`；
  争议模块走 `CurrentUserPort` 适配器。**请求体不接受前端传入的 `userId` 或 `role`。**
- `AuthUserDto` 字段：`userId`（字符串）、`username`、`displayName`、`role`、`phone`。

### 5.2 公开路径与授权

`SecurityConfiguration` 中：

- 公开：`/actuator/health`、`/api/v1/auth/csrf`、`/api/v1/auth/register`、`/api/v1/auth/login`。
- `/api/v1/admin/**`：需要 `ROLE_ADMIN`，普通用户在过滤器链即返回 **403（空正文）**。
- 其他请求一律要求已认证；未认证由 `HttpStatusEntryPoint` 返回 **401（空正文）**。
- `formLogin`、`httpBasic` 关闭。

**管理员权限是双层校验**：URL 层用 `hasRole("ADMIN")`，服务层还会调用
`CurrentUserService.requireAdmin()`（争议模块用 `CurrentUserPort.requireAdmin()`）。
当安全上下文角色与业务 Session 角色不一致时，URL 层放行后仍会被服务层拒绝并返回
`{ code: "FORBIDDEN" }`。新增管理接口必须同时保留这两层，不能只依赖前端隐藏按钮。

`GET /api/v1/auth/me` 未登录时返回 401；前端把 `auth/me` 的 401 视为匿名（`null`），不当作错误。

### 5.3 CSRF

- 使用 `CookieCsrfTokenRepository.withHttpOnlyFalse()`，Cookie 名 `XSRF-TOKEN`，
  请求头 `X-XSRF-TOKEN`。
- `GET /api/v1/auth/csrf` 直接返回 Spring 的 `CsrfToken` 对象
  （`{ headerName, parameterName, token }`），**不是**统一 `{code,data}` 包装。
- 前端所有非 `GET/HEAD` 请求先获取 CSRF Token，再放入 `X-XSRF-TOKEN`；
  登录/注册/退出成功后清空本地 CSRF 缓存并重新获取。
- 新增写接口通常不需要单独配置 CSRF；前端只要走统一 `client.request` 即可。
- 调试时注意：**写请求缺少 CSRF 时，CSRF 过滤器会先返回 403，即使当前未登录**；
  想验证未登录 401，需要同时带上有效 CSRF Token。

### 5.4 注册与初始钱包

`AuthService.register` 在同一事务中：

1. 创建 `app_user`（BCrypt 密码哈希，角色固定 `USER`）；
2. 创建 `wallet_account`；
3. 发放 100 虚拟币初始余额，并写入一条 `INCOME` 流水。

用户名唯一；重复注册返回 409 `DUPLICATE_ACTION`（消息“用户名已被使用”）。
校验：用户名 3–50，密码 8–128，显示名 1–50，手机号 ≤30。注册成功后自动登录。

### 5.5 用户中心

- `GET /api/v1/users/me/profile`：本人资料 + `rating`（收到的评价均分，保留一位小数）、
  `reviewCount`、`creditScore`。`creditScore` 是预留字段，信誉分机制上线前恒为 `null`。
- `PATCH /api/v1/users/me`：仅可修改 `displayName`（1–50）与 `phone`（≤30，
  仅数字/空格/`+`/`-`）；`username`、`role` 不可改；手机号空白会清空为 `null`。
- `POST /api/v1/users/me/password`：校验当前密码、新旧不同、新密码 8–128；
  当前密码错误返回 400 `VALIDATION_ERROR`。
- 三个接口都从 Session 取用户，不接收请求体中的 `userId`。

---

## 6. API 契约与 ID 约定

### 6.1 通用规则

- 前缀：`/api/v1`。JSON 字段 `camelCase`；时间 ISO 8601 UTC；虚拟币为整数。
- 成功/错误统一为：

```json
{ "code": "OK", "message": "success", "data": {} }
{ "code": "TASK_STATE_CONFLICT", "message": "...", "data": null }
```

- 分页统一为：

```json
{ "items": [], "page": 0, "size": 10, "total": 0 }
```

- `page` 从 0 开始；`size` 只接受 `10`、`20`、`50`；空列表是成功响应。
- 前端只根据 HTTP 状态码和 `code` 展示提示，不解析服务端堆栈。
- 例外：`GET /api/v1/auth/csrf` 返回原始 `CsrfToken`，不走统一包装。

### 6.2 ID 类型（重要）

| 标识 | 服务端/数据库 | API | 说明 |
| --- | --- | --- | --- |
| `taskId` | `String` / `VARCHAR(36)`，内容是 UUID 文本 | string | 任务主键 |
| `userId`、`publisherId`、`runnerId` | `Long` / `BIGINT` | string | 内部用 Long，序列化时字符串化 |
| `requestId`、`appealId`、`complaintId`、`transactionId` | `UUID` | string | 争议/投诉/流水自身主键 |
| `notificationId`、`reviewId`、`logId` | `String`（UUID 文本） | string | 任务相关资源的字符串主键 |

不要把 `taskId` 当成 `UUID` 对象，用户 ID 也不是 UUID。争议模块的端口使用
`Long` 用户 ID，`TaskSnapshot.taskId` 为 `String`。

### 6.3 已实现接口

认证与用户中心：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | 注册并自动登录，201 |
| `POST` | `/api/v1/auth/login` | 登录 |
| `POST` | `/api/v1/auth/logout` | 退出（需已登录） |
| `GET` | `/api/v1/auth/me` | 当前用户 |
| `GET` | `/api/v1/auth/csrf` | 获取 CSRF Token（原始对象） |
| `GET` | `/api/v1/users/me/profile` | 本人资料与评分展示 |
| `PATCH` | `/api/v1/users/me` | 修改显示名/手机号 |
| `POST` | `/api/v1/users/me/password` | 修改密码 |
| `GET` | `/api/v1/users/me/tasks` | 我的任务，必须传 `as=publisher\|runner` |

任务：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/tasks` | 浏览/筛选/分页（`status`、`keyword`） |
| `POST` | `/api/v1/tasks` | 发布任务，201，冻结报酬 |
| `GET` | `/api/v1/tasks/{taskId}` | 任务详情（任意已登录用户） |
| `PATCH` | `/api/v1/tasks/{taskId}` | 修改待接单任务 |
| `POST` | `/api/v1/tasks/{taskId}/withdrawal` | 撤回待接单任务，全额解冻 |
| `POST` | `/api/v1/tasks/{taskId}/acceptance` | 接单 |
| `POST` | `/api/v1/tasks/{taskId}/delivery` | 送达 |
| `POST` | `/api/v1/tasks/{taskId}/completion` | 确认完成并结算 |
| `POST` | `/api/v1/tasks/{taskId}/reviews` | 评价跑腿员，201 |

钱包：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/wallet` | 可用/冻结余额 |
| `GET` | `/api/v1/wallet/transactions` | 本人流水，可按 `type` 过滤 |

通知：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/notifications` | 本人通知，`page`、`size`、`unreadOnly`、`type=TASK\|SYSTEM` |
| `POST` | `/api/v1/notifications/{notificationId}/read` | 标记本人通知已读，幂等 |
| `POST` | `/api/v1/admin/notifications` | 管理员发布系统通知，201，广播给全部现有用户 |

投诉：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/complaints` | 提交投诉/举报，201 |
| `GET` | `/api/v1/admin/complaints` | 管理员分页查询，可按 `status` |
| `POST` | `/api/v1/admin/complaints/{complaintId}/actions` | 管理员处理（仅留痕） |

取消与申诉：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/tasks/{taskId}/cancellation-requests` | 发起取消申请，201 |
| `POST` | `/api/v1/cancellation-requests/{requestId}/response` | 被申请方同意/拒绝 |
| `GET` | `/api/v1/admin/cancellation-requests` | 管理员分页查询，可按 `status` |
| `POST` | `/api/v1/admin/cancellation-requests/{requestId}/arbitration` | 管理员裁决取消 |
| `POST` | `/api/v1/tasks/{taskId}/appeals` | 对已完成任务申诉，201 |
| `GET` | `/api/v1/admin/appeals` | 管理员分页查询申诉 |
| `POST` | `/api/v1/admin/appeals/{appealId}/decision` | 管理员裁决申诉 |

管理员查询与健康检查：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/users` | 用户查询，`keyword` 模糊匹配账号/显示名/手机号 |
| `GET` | `/api/v1/admin/transactions` | 交易流水，`userId`/`taskId` 精确筛选 |
| `GET` | `/actuator/health` | 健康检查 |

### 6.4 契约中存在、代码尚未实现或未覆盖

前端/契约中仍有以下缺口，不要把它们当成已联通，也不要用假数据掩盖：

- `GET /api/v1/admin/operation-logs`：管理台“操作日志”标签仍禁用并标注“暂未开放”。
- 本人取消申请/申诉的列表与详情：只有创建、应答和管理员查询，没有个人历史列表。
- 通知“全部标为已读”：只有单条已读。
- 系统通知定向发送：当前 `POST /admin/notifications` 只广播给全部现有用户，没有指定用户列表。
- 专用管理员任务查询：管理台“任务查询”复用普通 `GET /tasks`，没有全量范围、发布者/时间筛选。
- 投诉处罚扩展：不执行扣信用分、调账、功能限制、冻结账号，也不发提醒；图片上传、限制期限、第二管理员复核未实现。
- 信用分机制：只有展示字段，`creditScore` 恒为 `null`；没有变更、查询规则或低分限制。
- 密码找回、连续登录失败锁定未实现。
- 异常任务处理（强制关闭、转派等）没有接口。
- 任务详情没有公开展示路由；前端统一复用 `/my-tasks/:taskId`。
- 任务、钱包、管理列表的日期区间筛选未实现。

---

## 7. 业务规则

### 7.1 任务状态机

```text
WAITING_FOR_TAKER --接单--> IN_PROGRESS --送达--> WAITING_FOR_ACCEPTANCE --确认完成--> COMPLETED
WAITING_FOR_TAKER --撤回--> CANCELLED
IN_PROGRESS / WAITING_FOR_ACCEPTANCE --取消生效--> CANCELLED
```

- `COMPLETED`、`CANCELLED` 是终态。
- 没有“任意改状态”的接口；只能通过接单、送达、确认完成、撤回、取消裁决等业务动作改变状态。
- 每次写操作都重新读数据库最新状态再校验，不信任前端缓存。
- 接单使用 `TaskRepository.findByIdForUpdate`（`PESSIMISTIC_WRITE`）保证并发下只有一个请求成功；
  `Task` 另有 `@Version` 乐观锁字段。
- 发布者不能接自己发布的任务；接单需要任务处于 `WAITING_FOR_TAKER` 且未过截止时间。
- 送达仅限实际跑腿员；确认完成仅限发布者；评价仅限已完成任务的发布者，且每任务一次。
- 修改/撤回仅限发布者且任务必须仍是 `WAITING_FOR_TAKER`；修改报酬按差额补冻结/解冻。
- 操作留痕写入 `task_operation_log`，动作包括 `PUBLISH/UPDATE/WITHDRAW/ACCEPT/DELIVER/COMPLETE/REVIEW`。

### 7.2 账务语义

`AccountingService` 是账务唯一入口，任务和争议都通过它改余额：

| 方法 | 语义 |
| --- | --- |
| `freeze(userId, taskId, amount)` | 可用余额 → 冻结余额，写 `FREEZE` 流水 |
| `unfreeze(userId, taskId, amount)` | 冻结余额 → 可用余额，写 `UNFREEZE` 流水 |
| `adjustFrozen(userId, taskId, delta)` | 修改报酬时按差额冻结/解冻 |
| `settle(taskId, publisherId, runnerId, amount)` | 发布者冻结 → 跑腿员可用，写 `EXPENSE`/`INCOME` |
| `settleFrozenReward(...)` | 取消生效：释放冻结，按裁决退回发布者/支付跑腿员 |
| `transferAvailable(...)` | 申诉裁决：被申诉方可用 → 申诉方可用，写 `EXPENSE`/`COMPENSATION` |
| `openAccount(userId, initial)` | 注册时创建钱包并写初始 `INCOME` |

- 流水类型：`INCOME`、`EXPENSE`、`FINE`、`COMPENSATION`、`FREEZE`、`UNFREEZE`。
- `amount` 非 0；`balance_after` 非负。
- 所有余额变更与流水写入必须和业务状态变更在**同一数据库事务**中提交或回滚。
- 钱包行使用悲观锁；涉及两个用户时按 `userId` 升序加锁，避免死锁。
- 不要绕过 `AccountingService`/`LedgerPort` 直接更新 `wallet_account` / `wallet_transaction`。

### 7.3 取消申请

```text
PENDING_RESPONSE --同意--> APPROVED      任务 CANCELLED，冻结报酬全额退回发布者
PENDING_RESPONSE --拒绝--> PENDING_ADMIN 任务、余额、原流水都不动
PENDING_ADMIN    --裁决--> ARBITRATED    按裁决取消任务或保持原状
```

- 发起：仅任务参与者；任务必须是 `IN_PROGRESS` 或 `WAITING_FOR_ACCEPTANCE`；
  同一任务同时只允许一条活动申请（服务层检查 + 数据库部分唯一索引 `ux_cancellation_active_task`）。
- 应答：仅“另一位参与者”；仅 `PENDING_RESPONSE`。
- 裁决：仅管理员；仅 `PENDING_ADMIN`；关联任务仍须可取消。
  - `SUPPORT_CONTINUE`：`refundAmount = runnerAmount = 0`，任务不变。
  - `SUPPORT_REQUESTER_CANCEL`：`refundAmount` 必须等于冻结报酬，`runnerAmount = 0`。
  - `CANCEL_PROPORTIONALLY`：`refundAmount + runnerAmount` 必须等于冻结报酬。
- 状态检查在 `findByIdForUpdate` 行锁内完成，重复应答/裁决返回 409。
- 取消成功后任务转 `CANCELLED`，通过 `TaskParticipationPort.cancelTask` 写入任务表；
  争议模块不直接写 `task`。

### 7.4 已完成任务申诉

- 提交：仅任务参与者；仅 `COMPLETED` 任务；每位参与者对同一任务最多一次
  （唯一约束 `uq_appeal_task_appellant`）。
- 申诉不改变任务 `COMPLETED` 状态，不修改原始结算流水。
- 裁决：仅管理员；仅 `PENDING`；关联任务仍须 `COMPLETED`。
  - `REJECTED`：`transferAmount` 必须为 0，不产生流水。
  - `SUPPORTED` / `PARTIALLY_SUPPORTED`：金额 > 0，且不得超过被申诉方可用余额；
    从被申诉方划转给申诉方，并记录 `EXPENSE`/`COMPENSATION` 两条流水。
- 账务端口必须返回 1–2 条真实流水 ID；`null`、空列表或超过两条都视为账务异常，
  返回 500 `INTERNAL_ERROR` 并整体回滚，申诉保持 `PENDING`。

### 7.5 投诉与举报

- `POST /complaints` 的投诉人来自 Session：
  - 关联任务时，任务参与者提交投诉，被投诉人省略则取另一方参与者；被投诉人必须是另一位参与者。
  - 非参与者可以针对任务内容举报，但**不能指定被投诉用户**。
  - 不关联任务时，可提交一般问题；指定的被投诉用户必须存在且不能是本人。
- 管理端列表按 `createdAt`、`complaintId` 倒序分页，可按 `status=PENDING|PROCESSED`。
- 处理仅保存留痕，只开放 `WARNING`、`LOG_ONLY`、`OFFLINE_REFERRAL`；
  `CREDIT_ADJUSTMENT`、`FINANCIAL_ADJUSTMENT`、`FUNCTION_RESTRICTION`、`ACCOUNT_FREEZE`
  以及非空调整金额会被拒绝（400 `VALIDATION_ERROR`）。
- 处理要求至少一种不重复的已开放方式和 1–1000 字非空白说明；
  事务内锁定投诉行，只允许从 `PENDING` 处理一次，重复/并发处理返回 409 `DUPLICATE_ACTION`。
- 处理不会自动发送通知或执行处罚。

### 7.6 通知

- `notification.type`：
  - `TASK`：任务事件通知，由 `TaskService` 在接单、送达、确认完成、评价后写入；
    携带 `relatedTaskId`。
  - `SYSTEM`：管理员发布的系统通知，不关联任务，`relatedTaskId = null`。
- 任务通知通过 `NotificationService.notify` 使用 `REQUIRES_NEW` 独立事务；
  通知失败不回滚任务主流程（`TaskService.safeNotify` 捕获并记日志）。
- 管理员 `POST /admin/notifications` 校验标题 1–100、内容 1–500（去首尾空白）；
  在同一事务中为当前全部已注册用户各写入一条 `SYSTEM` 通知，返回
  `{ recipientCount, publishedAt }`（201）。
- 通知列表按 `createdAt`、`notificationId` 倒序；支持 `unreadOnly` 和 `type`。
- 标记已读只允许本人操作；通知不存在或不属于当前用户返回 404，重复标记幂等。
- 已读状态按用户保存，系统通知没有任务跳转入口。

### 7.7 管理员查询

- `GET /admin/users?keyword&page&size`：
  - 服务层 `requireAdmin()`；`keyword` 最长 100，去空白后按
    `username`、`displayName`、`phone` 模糊匹配（不区分大小写）。
  - 按 `createdAt`、`userId` 倒序；返回 `userId`（字符串）、`username`、`displayName`、
    `role`、`phone`、`createdAt`，**不返回密码哈希**。
- `GET /admin/transactions?userId&taskId&page&size`：
  - 服务层 `requireAdmin()`；`userId` 必须是正整数，`taskId` 最长 36；同时提供取交集。
  - 按 `createdAt`、`transactionId` 倒序，返回真实账本记录。

---

## 8. 事务、并发、幂等

- 写方法统一 `@Transactional`；任务状态、账务、流水、操作日志同事务。
- 行锁顺序约定：**申请/申诉/投诉行 → 任务行 → 钱包账户（按 userId 升序）**。新代码沿用，
  不要反向加锁。
- 重复请求通过“锁内状态检查 + 唯一约束”保证幂等：重复接单、送达、确认完成、应答、
  裁决、投诉处理都不会重复改状态或重复记账。
- 数据库唯一约束是并发穿透服务层检查时的最后防线；捕获
  `DataIntegrityViolationException` 并转换成稳定的 409 错误码。
- 任务接单用悲观锁 + `@Version`；接单、送达、确认完成都在事务内先读取最新状态。
- 取消/申诉/投诉的处理入口都先对自身行加 `PESSIMISTIC_WRITE`，再读任务。

---

## 9. 数据库与 Flyway

迁移脚本按版本顺序执行：

| 脚本 | 内容 |
| --- | --- |
| `V2__create_task_tables.sql` | `task`、`task_operation_log`、`task_review` |
| `V2026102101__create_cancellation_requests.sql` | `cancellation_requests` + 活动申请部分唯一索引 |
| `V2026102102__create_appeals.sql` | `appeals`、`appeal_transaction_ids` |
| `V2026102801__align_dispute_identity_types.sql` | 争议表 `task_id` → `VARCHAR(36)`，用户列 → `BIGINT` |
| `V2026102802__create_persistent_wallet.sql` | `wallet_account`、`wallet_transaction` |
| `V2026102803__create_user_accounts.sql` | `app_user`，并给钱包/流水加用户外键 |
| `V2026110401__create_notifications.sql` | `notification` 表与用户时间索引 |
| `V2026110501__create_complaints.sql` | `complaints`、`complaint_actions` |
| `V2026111401__add_notification_type.sql` | `notification.type`（`TASK`/`SYSTEM`）+ CHECK + 索引 |

表关系与约束：

- `wallet_account.user_id` → `app_user.user_id`（外键）。
- `wallet_transaction.user_id` → `app_user.user_id`（外键）。
- `complaints.reporter_id/target_user_id/handled_by` → `app_user`；`complaints.task_id` → `task`（外键）。
- `appeal_transaction_ids.appeal_id` → `appeals.appeal_id`（外键，级联删除）。
- `task`、`cancellation_requests`、`appeals`、`notification` 之间**没有外键**，是刻意的模块边界；
  需要补外键时先协调，再写新迁移。
- `cancellation_requests`：状态/裁决枚举 CHECK；金额非负；`ux_cancellation_active_task`
  部分唯一索引（同一任务最多一条待应答/待裁决）。
- `appeals`：`(task_id, appellant_id)` 唯一；申诉人≠被申诉人；枚举 CHECK；金额非负。
- `complaints`：状态 CHECK；待处理/已处理的审计字段组合 CHECK；`complaint_actions`
  只允许 `WARNING/LOG_ONLY/OFFLINE_REFERRAL`，且 `(complaint_id, action)` 唯一。
- `task`：`reward > 0`；状态列没有数据库 CHECK，由应用层枚举约束。
- `task_review`：`(task_id, reviewer_id)` 唯一；评分 1–5。
- `notification`：`type` CHECK；`user_id` 无外键。

迁移规则：

1. **不要修改已在本地/共享库执行过的迁移脚本**；新增 `V<版本>__<描述>.sql`。
2. `V2026102801` 在发现任何争议记录时会主动失败，因为旧的 UUID 用户 ID 无法可靠映射到
   `BIGINT`。不要删除这段检查或猜测映射；先导出数据并取得权威映射。
3. 测试使用 H2 + `ddl-auto=create-drop`，不执行 Flyway；生产/本地 PostgreSQL 才验证迁移。
4. 新增列优先使用带默认值的可空/回填策略，避免 `ddl-auto=validate` 因实体与迁移不一致启动失败。

---

## 10. 后端模块与公共层

### 10.1 两套公共响应/错误类型（现存技术债）

仓库里同时存在两套结构相同、来源不同的公共类型：

| 用途 | 类型 | 使用方 |
| --- | --- | --- |
| 统一响应 | `common.ApiResponse`（class） | auth / user / task / accounting / notification / complaint |
| 统一响应 | `common.api.ApiResponse`（record） | dispute |
| 分页 | `common.PageResult`（class） | auth / user / task / accounting / notification / complaint |
| 分页 | `common.api.PageResult`（record） | dispute |
| 错误码 | `common.ErrorCode` + `common.TaskException` | auth / user / task / accounting / notification / complaint |
| 错误码 | `common.error.ErrorCode` + `common.error.ApiException` | dispute |
| 分页校验 | 控制器/服务内联校验 | task、notification |
| 分页校验 | `common.web.Pagination` | dispute、accounting、complaint、admin user |

`common.GlobalExceptionHandler` 同时处理 `TaskException`、`ApiException`、
`InsufficientBalanceException`、参数校验异常、`DataIntegrityViolationException` 和
未预期 `Exception`，因此两套类型对外输出一致。

**约定**：新代码跟随所在模块的既有风格，不要在一个模块里混用两套；统一是一次独立重构，
需要同时改 Controller、Service、测试，不要顺手做。

### 10.2 争议模块的端口

`dispute` 不直接写公共表，只依赖三个端口：

| 端口 | 实现 | 作用 |
| --- | --- | --- |
| `CurrentUserPort` | `SessionCurrentUserAdapter` | 从 Session 解析用户/角色 |
| `TaskParticipationPort` | `TaskParticipationAdapter` | 锁任务、读快照、取消任务 |
| `LedgerPort` | `AccountingLedgerAdapter` | 冻结释放、余额划转、查余额 |

`DisputeIntegrationFallbackConfiguration` 是早期“失败关闭”的占位配置，通过
`META-INF/spring/...AutoConfiguration.imports` 注册；真实适配器存在时，
`@ConditionalOnMissingBean` 会让占位 Bean 失效。集成已经完成后，这个类和 imports 文件
可以在单独清理时删除，但删除前要确认三个适配器都在。

### 10.3 错误码

`common.error.ErrorCode` 是当前最完整的错误码集合：

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 字段缺失、长度越界、分页/枚举非法、金额语义不符 |
| 401 | `UNAUTHORIZED` | 未登录 |
| 403 | `FORBIDDEN` | 非参与者、非被申请方、非管理员 |
| 404 | `TASK_NOT_FOUND` / `CANCELLATION_REQUEST_NOT_FOUND` / `APPEAL_NOT_FOUND` | 资源不存在 |
| 409 | `TASK_STATE_CONFLICT` | 任务状态不允许 |
| 409 | `CANCELLATION_STATE_CONFLICT` | 申请状态不允许 |
| 409 | `CANCELLATION_ALREADY_PENDING` | 已有活动取消申请 |
| 409 | `APPEAL_ALREADY_EXISTS` | 重复申诉 |
| 409 | `APPEAL_ALREADY_DECIDED` | 重复裁决 |
| 409 | `INSUFFICIENT_BALANCE` | 余额不足 |
| 409 | `DUPLICATE_ACTION` | 重复业务操作 / 唯一约束冲突 / 投诉重复处理 |
| 500 | `INTEGRATION_NOT_READY` | 公共能力未接入（占位实现） |
| 500 | `INTERNAL_ERROR` | 未预期错误 / 账务返回无效流水 |

`common.ErrorCode` 另有 `OK`、`RESOURCE_NOT_FOUND` 等基础码，auth/user/task/accounting/
notification/complaint 使用。争议模块的 `TASK_NOT_FOUND` 等码不会自动出现在 `common.ErrorCode` 中。

### 10.4 异常处理

- `TaskException(ErrorCode, message)` / `ApiException(ErrorCode, message)` → 对应 HTTP 状态 +
  统一错误体。
- `InsufficientBalanceException` → 409 `INSUFFICIENT_BALANCE`。
- `BindException`、`ConstraintViolationException`、`HttpMessageNotReadableException`、
  缺少参数、参数类型不匹配 → 400 `VALIDATION_ERROR`。
- `DataIntegrityViolationException` → 409 `DUPLICATE_ACTION`。
- 其他未预期异常 → 500 `INTERNAL_ERROR`，服务端记录日志，不把堆栈返回前端。
- 注意：URL 层 401/403（未登录、非管理员）由安全过滤器直接返回空正文，不经过
  `GlobalExceptionHandler`；服务层 403 才带 `{code:"FORBIDDEN"}`。

---

## 11. 前端

### 11.1 路由

| 路由 | 视图 | 说明 |
| --- | --- | --- |
| `/` | — | 重定向到 `/tasks` |
| `/login`、`/register` | `AuthView.vue` | 登录/注册，登录后返回 `redirect` |
| `/tasks` | `TaskHallView.vue` | 任务大厅、筛选、分页、后端健康检查 |
| `/tasks/new` | `TaskPublishView.vue` | 发布任务，成功后跳回任务大厅 |
| `/my-tasks` | `MyTasksView.vue` | 我接取的/我发布的，按 `?as=publisher` 切换 |
| `/my-tasks/:taskId` | `TaskDetailView.vue` | 任务详情与业务操作 |
| `/wallet` | `WalletView.vue` | 余额与流水 |
| `/notifications` | `NotificationsView.vue` | 通知中心，支持类型与未读筛选 |
| `/disputes` | `DisputesView.vue` | 按申请编号应答取消 |
| `/profile` | `ProfileView.vue` | 个人资料、密码、评分展示 |
| `/admin` | `AdminView.vue` | 管理台，`meta.admin`，仅 `ADMIN` |
| `/:pathMatch(.*)*` | `NotFoundView.vue` | 404 |

路由守卫统一调用 `loadSession()`；未登录跳 `/login?redirect=...`；普通用户访问
`meta.admin` 路由会被送回 `/my-tasks`。服务端仍会重新鉴权。

### 11.2 API 客户端

`src/api/client.js`：

- 所有请求 `credentials: 'include'`；默认 15 秒超时，支持 `AbortController`。
- 查询参数会省略 `undefined/null/''`，但保留 `0` 和 `false`（分页 `page=0` 必须发出）。
- 非 `GET/HEAD` 自动先取 `/api/v1/auth/csrf`，并带 `X-XSRF-TOKEN`。
- 业务成功响应必须是 `{ code: 'OK', data: ... }`，否则抛 `ApiError`。
- 非认证接口返回 401 时派发 `campus:auth-expired`，由 `App.vue` 清会话并跳登录；
  `/auth/me`、`/auth/login`、`/auth/register` 的 401 不触发该事件。
- 登录/注册/退出成功后轮换 CSRF 缓存。
- 写操作不自动重试；409 保留服务端 `code`。

`src/api/index.js` 是唯一的接口封装层。页面不直接拼 URL。
ID 一律按字符串传递；不提交操作者 `userId`/`role` 或目标状态。

### 11.3 状态与组合式函数

- `composables/session.js`：`session`、`setSession`、`clearSession`、`loadSession`；
  并发 `/auth/me` 只发一次；401 解析为 `null`；旧请求不能覆盖新登录。
- `composables/state.js`：`revision`/`changed()` 全局刷新信号、`notify` toast、
  `useResource`（带 AbortController）、`usePaged`（0 起始分页，筛选/每页条数变化回首页，
  末页消失时自动请求最后有效页）。
- `App.vue`：顶栏铃铛与侧边栏未读角标通过 `GET /notifications?unreadOnly=true&page=0&size=10`
  读取 `total`，并随 `revision` 刷新；仅管理员显示“管理工作台”入口。
- `domain/auth.js`：`safeDestination` 只允许站内路径。
- `domain/tasks.js`：状态中文映射、流水类型映射、`taskPermissions`、
  `validateTaskDraft`、`validateProfileDraft`、`validatePasswordChange`、
  `validateCancellationRuling`、`validateAppealDecision`、格式化函数。
- `domain/admin.js`：投诉处理动作标签、`recordOnlyActions`、`validateComplaintDecision`。
- `domain/notifications.js`：`notificationTypes`（`TASK`/`SYSTEM` 中文标签）、
  `validateSystemNotification`。

### 11.4 页面与操作

- `TaskActions.vue` 统一承载任务操作：接单、送达、验收、编辑、撤回、评价、申请取消、
  申诉、投诉、举报非法任务。冲突（409）会提示并刷新任务。
- `MyTasksView.vue` 提供“我接取的/我发布的”切换、状态筛选、统计卡片。
- `TaskDetailView.vue` 展示进度、取送信息、报酬和操作区；路由仍是 `/my-tasks/:taskId`。
- `NotificationsView.vue`：
  - 类型分段：全部 / 任务通知 / 系统通知；
  - “仅看未读”切换；
  - 系统通知使用蓝色喇叭图标和“系统通知”标签，与任务通知区分；
  - 任务通知有“查看任务”，系统通知只有“标记已读”。
- `ProfileView.vue`：资料编辑、修改密码、评分/评价数展示。
- `AdminView.vue` 模块：
  - 取消裁决、任务申诉、投诉处理、用户查询、任务查询、交易流水；
  - 操作日志仍禁用（`pendingSections = ['operation-logs']`）；
  - 页头“发布系统通知”打开 `SystemNoticeModal.vue`，发布后提示 `recipientCount`；
  - 用户查询调用 `GET /admin/users`，关键字查询、空态/错误/重试沿用 `usePaged`。
- `AdminDecision.vue` 承载取消/申诉/投诉的处理弹窗。
- `SystemNoticeModal.vue` 发布系统通知，前端校验与后端 `SystemNotificationRequest` 对齐。

### 11.5 开发模式

- `npm run dev`：真实后端，Vite 代理 `/api`、`/actuator`。
- `npm run dev:demo`：`vite.demo.js` 进程内有状态 Mock，只实现 CSRF、
  注册/登录/退出、`/auth/me`、`GET /tasks`、`POST /tasks`；其他接口返回 404。
- `npm run dev:apifox`：代理到 Apifox 云端 Mock，剥离本机 Cookie/Authorization，
  不保存云端凭证；只验证路径/字段/代理联通，不保证身份和跨请求状态。

### 11.6 前端现状缺口

- 任务详情没有独立的公开路由；通知/任务大厅统一跳 `/my-tasks/:taskId`。
- 通知没有“全部已读”；取消/申诉没有个人历史列表与详情页。
- 管理台“任务查询”复用普通 `GET /tasks`，没有管理员专用范围。
- 管理台“操作日志”未实现，入口禁用。
- 投诉处罚、信用分、账号安全扩展未实现；相关按钮禁用或只展示占位。
- 前端不能用假数据回退接口失败；失败必须显示错误并允许重试。

---

## 12. 测试

### 12.1 后端

命令：`cd code/backend && .\mvnw.cmd test`

| 测试类 | 用例数 | 覆盖 |
| --- | ---: | --- |
| `CampusTaskPlatformApplicationTests` | 1 | Spring 上下文启动 |
| `AuthControllerTest` | 3 | 注册/登录/退出/当前用户、Session、CSRF、初始钱包 |
| `PersistentAccountingServiceTest` | 3 | 冻结/解冻/结算/划转、余额与流水 |
| `TaskServiceIntegrationTest` | 24 | 任务状态迁移、权限、并发接单、幂等、评价 |
| `TaskControllerTest` | 9 | HTTP 状态码、统一响应、认证/错误 |
| `CancellationServiceTest` | 22 | 发起/应答/裁决、金额校验、重复操作、分页 |
| `AppealServiceTest` | 18 | 已完成约束、每人一次、驳回/支持/部分支持、重复裁决 |
| `DisputeApiContractTest` | 11 | 7 个接口的路径、状态码、响应结构、ID 字符串化 |
| `DisputeModuleIntegrationTest` | 2 | 真实适配器 + H2 下取消/申诉完整流程 |
| `AdminEndpointsIntegrationTest` | 34 | 管理员鉴权、投诉处理、交易流水筛选/分页/并发 |
| `UserCenterIntegrationTest` | 7 | 资料、密码、评分展示与权限 |
| `NotificationAdminIntegrationTest` | 8 | 通知类型、系统通知广播、用户查询、鉴权/CSRF/分页 |

共同约定：

- `@SpringBootTest` + `@ActiveProfiles("test")`。
- `@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")`。
- 使用 H2，不依赖 PostgreSQL。
- 争议模块通过 `DisputeTestPortConfiguration` 注入
  `MutableCurrentUserPort`、`InMemoryTaskPort`、`InMemoryLedgerPort`。
- `DisputeApiContractTest` 使用独立 MockMvc，不加载安全过滤器链。
- `TaskControllerTest` 用 `@Primary CurrentUserService` 测试桩固定用户。
- `AdminEndpointsIntegrationTest` / `NotificationAdminIntegrationTest` 通过
  `MockHttpSession` + `SecurityContext` 同时构造安全上下文和业务 Session。

### 12.2 前端

命令：`cd code/frontend && npm test`

| 文件 | 用例数 | 覆盖 |
| --- | ---: | --- |
| `tests/client.test.js` | 11 | CSRF、credentials、错误码、401 事件、超时/取消、查询参数 |
| `tests/tasks.test.js` | 6 | 任务权限、取消裁决与申诉金额校验 |
| `tests/task-publish.test.js` | 7 | 发布表单校验 `validateTaskDraft` |
| `tests/session.test.js` | 6 | `/auth/me` 单请求复用、401→null、竞态、安全跳转 |
| `tests/profile.test.js` | 3 | 资料/密码校验、举报权限 |
| `tests/admin.test.js` | 4 | 投诉留痕校验、管理列表分页与末页修正、请求取消 |
| `tests/notifications.test.js` | 2 | 通知类型标签、系统通知表单校验 |

### 12.3 浏览器场景（可选）

需要先启动 `npm run dev:demo` 和 Playwright CLI：

- `tests/browser-smoke.js`：拦截 `/api/v1` 验证前端固定成功/失败场景。
- `tests/auth-browser-smoke.js`：使用真实本地认证接口，业务接口用固定响应。
- `tests/admin-browser-smoke.js`：拦截接口，覆盖投诉处理、未开放处罚、并发冲突提示、
  流水筛选、分页、空结果、重试和移动端布局。

浏览器场景不访问真实用户账务；真实后端事务、并发、幂等和权限由后端集成测试验证。

---

## 13. 文档地图与可信度

| 文档 | 用途 | 可信度 |
| --- | --- | --- |
| `code/development_doc/openapi.yaml` | 机器可读接口契约 | 高，字段/枚举以此为准；实现状态见 6.4 |
| `code/development_doc/API约定.md` | 总则、响应、状态枚举、事务规则 | 高 |
| `code/development_doc/前后端接口差异与ID统一.md` | 当前前后端差异、ID 基准、缺口优先级 | 高，最接近现状 |
| `code/development_doc/分工边界与任务图.md` | 五人职责与开发顺序 | 中，历史分工参考 |
| `code/development_doc/后端A-PartA任务与通知功能交付说明.md` | 任务编辑/撤回/评价 + 通知基础 | 中高；未覆盖 `SYSTEM` 类型与系统通知发布 |
| `code/development_doc/后端A-PartB用户中心与非法任务举报交付说明.md` | 用户中心与举报 | 高，功能级说明 |
| `code/development_doc/后端A-PartB管理员处理流程交付说明.md` | 管理员鉴权、裁决与处理 | 高，功能级说明 |
| `code/development_doc/后端A-任务模块对接说明.md` | 任务模块交付说明 | 中高，早期交付细节可能与后续合并有出入 |
| `code/backend/后端B取消与申诉实现说明.md` | 取消/申诉交付说明 | **部分过时**：仍写 UUID 用户/任务端口，当前代码为 `Long` 用户、`String` 任务 |
| `code/backend/任务发布接口核查说明.md` | Part A `POST /tasks` 核查 | 高，功能级说明 |
| `code/frontend/任务发布页面说明.md` | Part A 发布页面、校验与错误处理 | 高，功能级说明 |
| `code/frontend/FRONTEND_A_WEEK1.md` | 前端 A 第一周交付 | 中，历史说明 |
| `code/frontend/README.md` | 前端运行、共用契约、缺口 | 中高；其中“用户查询未开放”已过时 |
| `code/development_doc/软件架构图.md` | 组件关系 | 部分过时（骨架阶段表述） |
| `.github/modernize/.../architecture-diagram.md` | 旧架构图 | **过时** |
| `README.md` | 占位文件 | 无参考价值 |
| `code/README.md` | 启动、数据库、管理员账号 | 高 |

发现文档与代码不一致时：以代码和 `openapi.yaml` 为准，并同步更新
`前后端接口差异与ID统一.md`；不要单方面改公共字段或枚举。

---

## 14. 修改约定（容易踩坑）

1. **接口字段/枚举/路径改变**：先改 `openapi.yaml` 与 `API约定.md`，再同步后端 DTO、
   前端 `api/index.js`、页面和文档；不要只改一端。
2. **ID 类型**：`taskId` 是字符串 UUID；用户 ID 内部 `Long`、API 字符串；
   争议/投诉/流水主键是 `UUID`。序列化时手动 `toString()`，不要依赖全局 Jackson 配置。
3. **事务边界**：所有写操作 `@Transactional`；状态、余额、流水、日志同事务。
   账务方法会加入调用方事务，不要在里面自行提交。
4. **账务唯一入口**：只通过 `AccountingService`/`LedgerPort` 改余额；禁止直接写
   `wallet_account`/`wallet_transaction`。
5. **任务唯一入口**：任务状态只通过 `TaskService` 或 `TaskParticipationPort` 改；
   争议模块不得直接写 `task` 表。
6. **管理员鉴权**：URL 层的 `/api/v1/admin/**` 与每个管理接口服务层的
   `requireAdmin()` 都要保留；不能只依赖前端隐藏按钮或路径前缀。
7. **CSRF**：新增写接口后，前端确保走统一 `client.request`，不要裸 `fetch` 绕过 Token。
8. **迁移**：新增脚本，不改已执行脚本；争议表暂不补外键，补之前先协调；
   `V2026102801` 的数据保护检查不要删除。
9. **测试**：新业务至少覆盖成功路径 + 一个失败路径；后端测试必须能在 H2 下跑，
   不要依赖本地 PostgreSQL 或其他成员的未提交代码。
10. **前端错误处理**：请求失败要展示错误并允许重试，不能回退成假数据或把读取失败
    显示成“没有数据”。
11. **公共层**：不要在 `common` 和 `common.error`/`common.api` 之间随手搬家；
    统一重构单独提交。
12. **未实现功能**：前端入口保持禁用/标注，不要为了“看起来完整”伪造接口或假数据。
13. **文档边界**：`code/development_doc/` 是多成员共享契约，改动前先确认影响面；
    新增契约时同时更新 OpenAPI、约定文档和差异盘点。
14. **分支与协作**：不要直接向 `main` 提交；按成员分支（`dev-*`）开发，合并前同步契约文档。
    提交/推送前确认当前分支，不要把本机配置、密码或个人笔记带进版本库。
15. **个人文件**：`learn.md`、`image_local/` 等被本机排除，是用户本地资料；除非明确要求，
    不要读取、引用或修改。`note.md` 是已入库的 Part B 交接笔记，可以查阅，但不要把它当成契约来源。

---

## 15. 快速排查

| 现象 | 检查 |
| --- | --- |
| 启动报缺表 / Flyway 未执行 | 是否误删 `spring-boot-starter-flyway`；`DB_*` 是否指向正确库 |
| 启动报 schema 校验失败 | 实体字段与迁移列类型/长度是否一致；是否改了旧迁移；新列是否已写迁移 |
| `V2026102801` 启动失败 | 本地争议表已有数据，迁移拒绝静默转换；按提示导出并取得映射 |
| 所有业务接口 401 | 未登录或 Session 丢失；检查 Cookie、`CURRENT_USER` 属性 |
| 写接口 403 | 缺 CSRF Token；前端是否先请求 `/auth/csrf` 并带 `X-XSRF-TOKEN` |
| 管理接口普通用户 403 | URL 层 `hasRole("ADMIN")` 或服务层 `requireAdmin()`；检查安全上下文和业务 Session 角色是否一致 |
| 通知没有 `type` | 是否执行 `V2026111401`；H2 测试用实体建表，不验证 Flyway |
| 系统通知收不到 | 发布时用户是否已注册；广播只覆盖当前全部现有用户 |
| 前后端字段对不上 | 对照 `openapi.yaml` 与 `前后端接口差异与ID统一.md` |
| 操作日志 / 个人争议列表 404 | 后端尚未实现，见 6.4 |
| 前端本地能跑、真实联调失败 | 是否用了 `dev:demo`/`dev:apifox` 的 Mock 数据；它们只覆盖有限接口 |
| 列表页码/筛选异常 | 检查 `usePaged` 是否回首页、末页修正；后端 `size` 只接受 10/20/50 |
| 修改/撤回后通知缺失 | 当前只有接单、送达、完成、评价触发任务通知；修改/撤回不触发 |
| 管理员发布通知只有部分人收到 | 当前实现是发布时全量广播，没有定向投递；检查发布时用户表范围 |
