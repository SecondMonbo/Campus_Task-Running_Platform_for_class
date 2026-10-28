# 前后端接口差异与 ID 统一

盘点依据：`frontend/src/api/index.js`、前端页面调用、后端 Spring Controller、`openapi.yaml` 和 `API约定.md`。接口路径均省略公共前缀 `/api/v1`。

## ID 规则

| 标识 | 服务端/数据库类型 | API 类型 | 说明 |
|---|---|---|---|
| `taskId` | `String`；任务表为 `VARCHAR(36)`，内容为 UUID 文本 | string | 与任务模块已实现的模型一致，取消/申诉模块以同样的字符串引用任务 |
| 用户身份（如 `userId`、`publisherId`、`runnerId`） | `Long`；数据库为 `BIGINT` | string | 认证会话、任务与账务使用同一用户身份；API 按既有约定统一输出字符串 |
| 取消申请、申诉、账务流水等各自资源 ID | `UUID` | string | 独立资源主键，不作为跨模块用户或任务身份 |

前向迁移 `V2026102801` 会把争议表的任务 ID 改为 `VARCHAR(36)`，把关联用户 ID 改为 `BIGINT`。旧争议数据中的 UUID 用户身份没有可靠的数字用户映射，因此迁移遇到任何争议记录都会明确失败；不得猜测映射或静默丢弃记录。若要保留这些记录，需先由数据所有者提供权威映射并另行设计数据迁移。

## 前端调用与后端路由

| 前端功能/调用 | 前端路径 | 后端当前实现 | 结果与说明 |
|---|---|---|---|
| CSRF | `GET /auth/csrf` | `GET /auth/csrf` | 已实现；前端以 `X-XSRF-TOKEN` 提交写请求 |
| 注册、登录、退出、当前用户 | `/auth/register`、`/auth/login`、`/auth/logout`、`GET /auth/me` | `AuthController`、BCrypt 密码校验、Session 认证 | 已实现；注册自动登录并原子创建钱包，初始余额 100 |
| 任务大厅、详情、接单、送达、验收、我的任务 | `GET /tasks`、`GET /tasks/{taskId}`、任务状态操作、`GET /users/me/tasks` | `TaskController` 提供对应路由 | 路径已实现；需在真实认证和账务模块接入后做端到端验收 |
| 发布、编辑、撤回任务、评价 | API 约定和后端 Controller 有对应路由 | `POST /tasks`、`PATCH /tasks/{taskId}`、撤回、评价 | 后端已实现，但当前前端 `src/api/index.js` 未提供这些调用/页面流程 |
| 钱包和流水 | `GET /wallet`、`GET /wallet/transactions` | `WalletController`、PostgreSQL 账本查询服务 | 已实现并要求登录；初始授信及每次余额变更都留有流水 |
| 通知、标记已读 | `GET /notifications`、`POST /notifications/{notificationId}/read` | 无对应 Controller | **缺失**；通知页面及应用级未读数无法联调 |
| 取消申请与应答 | `POST /tasks/{taskId}/cancellation-requests`、`POST /cancellation-requests/{requestId}/response` | `CancellationController` + 真实会话、任务、账务适配器 | 已实现并通过数据库集成测试；业务操作共用事务 |
| 取消申请管理 | `GET /admin/cancellation-requests`、`POST /admin/cancellation-requests/{requestId}/arbitration` | `AdminCancellationController` + 真实适配器 | 已实现；需登录闭环提供管理员会话 |
| 完成任务申诉与管理 | `POST /tasks/{taskId}/appeals`、`GET /admin/appeals`、`POST /admin/appeals/{appealId}/decision` | `AppealController`、`AdminAppealController` + 真实适配器 | 已实现并通过数据库集成测试；账务流水 ID 写入申诉记录 |
| 投诉提交与处理 | `POST /complaints`、`GET /admin/complaints`、`POST /admin/complaints/{complaintId}/actions` | 无对应 Controller | **缺失**；前端已暴露提交与管理处理 UI |
| 管理员用户、交易、操作日志 | `GET /admin/users`、`GET /admin/transactions`、`GET /admin/operation-logs` | 无对应 Controller | **缺失**；管理工作台目前可见对应查询标签 |
| 管理员任务查询 | 前端管理员任务标签复用 `GET /tasks`，提交 `keyword` 等查询参数 | 只有普通任务列表路由，无专用 `/admin/tasks` | 路径可用性取决于任务列表查询和授权策略；当前 OpenAPI 未定义管理员专用资源 |

## 对齐结论与优先级

1. **P0：身份和任务引用。** 已按上表统一争议模块 Java 类型和数据库 schema；API 字段仍使用字符串 ID，现有前端无需改 ID 解析逻辑。
2. **P1：管理员账号配置。** 公开注册只能创建 `USER`。需在本地通过受控数据库操作将指定账号提升为 `ADMIN`；不得开放客户端自选角色。
3. **P2：通知。** 前端已有页面及调用，后端尚无对应 Controller/服务。
4. **P2：投诉与管理查询。** 补齐投诉处理、用户、管理员流水和操作日志 API，或在明确范围后从前端隐藏未实现入口。
5. **P3：管理任务查询及任务写入 UI。** 明确是否需要独立的管理员任务路由；决定后同步授权规则和 OpenAPI。

所有已实现接口应继续遵守 `{code, message, data}` 响应封装及分页 `{items, page, size, total}`；尚未实现接口应以本文和 OpenAPI 为待实现契约，不应被视为已经联通。
