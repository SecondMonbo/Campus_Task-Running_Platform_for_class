# 前后端接口差异与 ID 统一

盘点依据：`frontend/src/api/index.js`、前端页面调用、后端 Spring Controller、`openapi.yaml` 和 `API约定.md`。接口路径均省略公共前缀 `/api/v1`。

## ID 规则

| 标识 | 服务端/数据库类型 | API 类型 | 说明 |
|---|---|---|---|
| `taskId` | `String`；任务表为 `VARCHAR(36)`，内容为 UUID 文本 | string | 与任务模块已实现的模型一致，取消/申诉/投诉模块以同样的字符串引用任务 |
| 用户身份（如 `userId`、`publisherId`、`runnerId`） | `Long`；数据库为 `BIGINT` | string | 认证会话、任务与账务使用同一用户身份；API 按既有约定统一输出字符串 |
| 取消申请、申诉、投诉、账务流水等各自资源 ID | `UUID` | string | 独立资源主键，不作为跨模块用户或任务身份 |

前向迁移 `V2026102801` 会把争议表的任务 ID 改为 `VARCHAR(36)`，把关联用户 ID 改为 `BIGINT`。旧争议数据中的 UUID 用户身份没有可靠的数字用户映射，因此迁移遇到任何争议记录都会明确失败；不得猜测映射或静默丢弃记录。若要保留这些记录，需先由数据所有者提供权威映射并另行设计数据迁移。

## 前端调用与后端路由

| 前端功能/调用 | 前端路径 | 后端当前实现 | 结果与说明 |
|---|---|---|---|
| CSRF | `GET /auth/csrf` | `GET /auth/csrf` | 已实现；前端以 `X-XSRF-TOKEN` 提交写请求 |
| 注册、登录、退出、当前用户 | `/auth/register`、`/auth/login`、`/auth/logout`、`GET /auth/me` | `AuthController`、BCrypt 密码校验、Session 认证 | 已实现；注册自动登录并原子创建钱包，初始余额 100 |
| 任务大厅、详情、接单、送达、验收、我的任务 | `GET /tasks`、`GET /tasks/{taskId}`、任务状态操作、`GET /users/me/tasks` | `TaskController` 提供对应路由 | 前后端已接通；主流程和账务结算已有自动化集成测试及联调验证 |
| 发布、编辑、撤回任务、评价 | 任务发布页、任务操作组件及 `src/api/index.js` | `POST /tasks`、`PATCH /tasks/{taskId}`、`POST /tasks/{taskId}/withdrawal`、`POST /tasks/{taskId}/reviews` | 前后端已接通；编辑、撤回、评价均有页面操作入口 |
| 钱包和流水 | `GET /wallet`、`GET /wallet/transactions` | `WalletController`、PostgreSQL 账本查询服务 | 已实现并要求登录；初始授信及每次余额变更都留有流水 |
| 通知、标记已读 | 通知中心、应用级未读数；`GET /notifications`、`POST /notifications/{notificationId}/read` | `NotificationController`、`NotificationService` | 已实现本人通知分页、未读筛选和标记已读；任务接单、送达、完成、评价等事件会触发通知 |
| 取消申请与应答 | `POST /tasks/{taskId}/cancellation-requests`、`POST /cancellation-requests/{requestId}/response` | `CancellationController` + 真实会话、任务、账务适配器 | 已实现并通过数据库集成测试；业务操作共用事务 |
| 取消申请管理 | `GET /admin/cancellation-requests`、`POST /admin/cancellation-requests/{requestId}/arbitration` | `AdminCancellationController` + 真实适配器 | 已实现；需登录闭环提供管理员会话 |
| 完成任务申诉与管理 | `POST /tasks/{taskId}/appeals`、`GET /admin/appeals`、`POST /admin/appeals/{appealId}/decision` | `AppealController`、`AdminAppealController` + 真实适配器 | 已实现并通过数据库集成测试；账务流水 ID 写入申诉记录 |
| 投诉提交与处理 | `POST /complaints`、`GET /admin/complaints`、`POST /admin/complaints/{complaintId}/actions` | `ComplaintController`、`AdminComplaintController` | 已实现提交、状态筛选、数据库分页及处理留痕；管理员双重鉴权与加锁防重复处理，处罚暂未开放 |
| 管理员交易流水 | `GET /admin/transactions` | `AdminTransactionController` | 已实现全量查询、用户/任务精确筛选、数据库分页及稳定排序；仅管理员可访问 |
| 管理员用户、操作日志 | 目标接口 `GET /admin/users`、`GET /admin/operation-logs` | 无对应 Controller | **未实现**；管理工作台对应标签禁用并标注暂未开放 |
| 管理员任务查询 | 管理员工作台复用 `GET /tasks` 并提交关键字等查询参数 | `TaskController` 普通任务列表路由 | 已有基本查询入口；尚无专用管理员筛选接口，发布时间区间、发布者筛选及状态变更记录等需求未覆盖 |
| 用户资料与账号安全扩展 | 需求中的资料维护、密码修改/找回、连续登录失败锁定 | 当前无对应资料、密码找回或账号锁定接口 | **未实现或未覆盖**；当前已实现注册、登录、退出、当前用户查询和角色鉴权 |
| 信用分管理 | 信用分变更、查询展示、低信用分限制 | 无对应信用分服务、控制器及数据模型 | **未实现**；当前钱包虚拟币和交易流水不等同于信用分 |
| 操作日志 | 任务模块内部操作记录 | `TaskOperationLog` 及任务服务写入逻辑 | 仅覆盖部分任务操作；尚无覆盖全系统关键操作的统一日志查询接口 |

## 对齐结论与优先级

1. **已完成：核心任务和争议账务。** 任务发布、编辑、撤回、接单、送达、验收、结算、评价、取消与申诉裁决接口已实现；余额变更通过账务服务和流水记录处理。
2. **已完成：通知与本阶段管理功能。** 通知列表/已读、投诉提交及处理留痕、管理员交易流水查询已实现；管理端由服务端鉴权，未实现的用户和日志入口保持禁用。
3. **P1：合并后环境验收。** 自动化测试使用 H2；仍需在干净 PostgreSQL 数据库验证 Flyway 迁移，并对关键页面和接口做联调验收。
4. **后续需求缺口：信用分与账号管理。** 信用分规则、查询与低分限制，以及资料维护、密码找回/修改和登录失败锁定尚未覆盖；应按课程需求确认优先级后单独拆分。
5. **后续管理端缺口。** 管理员用户查询、异常订单处理、完整任务筛选、统一操作日志查询，以及投诉处罚（扣信用分、调账、功能限制、冻结等）尚未完整实现。投诉处理目前仅保存有限处理留痕，不会自动处罚或通知。

所有已实现接口应继续遵守 `{code, message, data}` 响应封装及分页 `{items, page, size, total}`；尚未实现接口应以需求文档和 OpenAPI 为后续契约，不应被视为已经联通。
