# 校园跑腿平台 · 后端A 任务模块（交付说明）

> 面向：后端A（你本人）向小组交付时的对接说明。模块已实现、可编译、测试全部通过。
> 代码位置：`code/backend/src/main/java/edu/campus/taskplatform/{common,auth,accounting,task}`
> 迁移脚本：`code/backend/src/main/resources/db/migration/V2__create_task_tables.sql`

---

## 一、你负责的任务，全部已实现

| 职责 | 接口 | 实现要点 |
|---|---|---|
| 发布任务 | `POST /api/v1/tasks` | 创建任务(待接单) + 冻结报酬 + 日志，同一事务 |
| 查询任务 | `GET /api/v1/tasks`、`GET /api/v1/tasks/{id}`、`GET /api/v1/users/me/tasks?as=publisher\|runner` | 分页/状态/关键字筛选；page 从 0，size 仅 10/20/50 |
| 修改任务 | `PATCH /api/v1/tasks/{id}` | 仅待接单；报酬变动按差额补冻结/解冻 |
| 撤回任务 | `POST /api/v1/tasks/{id}/withdrawal` | 仅待接单；置已取消 + 全额解冻退回 |
| 接单 | `POST /api/v1/tasks/{id}/acceptance` | 悲观锁保证并发下唯一成功；发布者不能接自己单 |
| 送达 | `POST /api/v1/tasks/{id}/delivery` | 仅跑腿员；进行中→待验收，记录送达时间 |
| 确认完成 | `POST /api/v1/tasks/{id}/completion` | 仅发布者；待验收→已完成 + 结算给跑腿员，幂等 |
| 评价 | `POST /api/v1/tasks/{id}/reviews` | 仅已完成；一条任务只能评价一次 |
| 接入账务 | `AccountingService` 接口 + `DemoAccountingService` | 冻结/解冻/差额调整/结算，全部在任务事务内 |

**状态机**：待接单 → 进行中 → 待验收 → 已完成；待接单 → 已取消（撤回）。已完成/已取消为终态。
写操作一律**重新读库最新状态**再校验；不提供"任意改状态"接口；重复请求幂等（状态只前进一次、账务只动一次）。

---

## 二、必须与组长/其他成员对齐的 3 个点（重要）

1. **当前用户从会话解析**：`SessionCurrentUserService` 从 Session 属性读取当前用户，属性名常量
   `SessionCurrentUserService.SESSION_ATTRIBUTE = "CURRENT_USER"`。
   → **组长登录模块须在登录成功后写入**：`session.setAttribute("CURRENT_USER", currentUser)`。
   否则任务接口会返回 401。（`CurrentUser` 是一个 record：`userId(Long) + role(String)`。）

2. **账务服务接入**：任务模块只依赖 `AccountingService` 接口（`accounting` 包）。仓库暂用内存实现
   `DemoAccountingService` 供联调/测试。
   → **组长账务服务就绪后，实现该接口并以 `@Primary` 注册一个真实 Bean**（余额+流水，与任务同事务提交），
   任务模块代码**零改动**。注意账务的 `taskId` 参数是字符串。

3. **迁移脚本编号**：任务表脚本命名为 `V2__create_task_tables.sql`（避免占用 V1）。
   → **由组长统一合并迁移**，若组长 V1 已用于用户/账务表，V2 保持即可；如需重排编号请组长统一改。
   表结构：`task`、`task_operation_log`、`task_review`（余额/流水/钱包表归组长，不在此脚本）。

> 另：送达通知属通知模块（后端B/组长）。当前送达仅写日志留痕，通知触发接口待通知模块就绪后接入（`TaskService.deliverTask` 内已注释预留）。

---

## 三、运行与验证

```bash
cd code/backend
./mvnw test          # 全部测试：状态机全链路 + 失败场景 + 并发接单 + 幂等，共 30+ 用例
./mvnw spring-boot:run
```

**生产/本地联调前置**（PostgreSQL）：
- 建库：`campus_task_platform`；用户名/密码见 `application.yml`（默认 `campus_dev`，可用环境变量覆盖）。
- `spring.jpa.hibernate.ddl-auto=validate` + Flyway 自动执行 `V2` 建表。

---

## 四、代码文件清单

**新增（main）**
- `common/`：`ApiResponse`、`ErrorCode`、`PageResult`、`TaskException`、`GlobalExceptionHandler`
- `auth/`：`CurrentUser`、`CurrentUserService`、`SessionCurrentUserService`
- `accounting/`：`AccountingService`、`InsufficientBalanceException`、`DemoAccountingService`
- `task/`：`TaskStatus`、`Task`、`TaskOperationLog`、`TaskReview`、`TaskRepository`、`TaskOperationLogRepository`、`TaskReviewRepository`、`TaskService`、`TaskController`
- `task/dto/`：`TaskCreateRequest`、`TaskUpdateRequest`、`WithdrawalRequest`、`CreateReviewRequest`、`TaskView`
- `db/migration/V2__create_task_tables.sql`

**新增（test）**
- `TaskServiceIntegrationTest`（服务层，T1–T18 关键场景 + 并发 + 幂等）
- `TaskControllerTest`（HTTP 层：成功/失败/认证/统一错误）

**说明**：响应统一为 `{code,message,data}`，错误码与 HTTP 状态严格对齐 `openapi.yaml`
（400 VALIDATION_ERROR / 401 / 403 FORBIDDEN / 404 RESOURCE_NOT_FOUND / 409 TASK_STATE_CONFLICT、INSUFFICIENT_BALANCE、DUPLICATE_ACTION / 500）。
