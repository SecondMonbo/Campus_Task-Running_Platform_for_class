# 后端 B：取消与申诉模块实现说明

> 对应《分工边界与任务图》中的“后端 B：取消与申诉”。接口路径、字段与枚举以 `API约定.md` 和 `openapi.yaml` 为准。

## 1. 交付范围

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/tasks/{taskId}/cancellation-requests` | 任务参与者发起取消申请 |
| `POST` | `/api/v1/cancellation-requests/{requestId}/response` | 被申请方同意或拒绝 |
| `GET` | `/api/v1/admin/cancellation-requests?status=` | 管理员分页查询取消申请 |
| `POST` | `/api/v1/admin/cancellation-requests/{requestId}/arbitration` | 管理员裁决取消争议 |
| `POST` | `/api/v1/tasks/{taskId}/appeals` | 任务参与者对已完成任务申诉 |
| `GET` | `/api/v1/admin/appeals?status=` | 管理员分页查询申诉 |
| `POST` | `/api/v1/admin/appeals/{appealId}/decision` | 管理员裁决申诉 |

## 2. 代码位置

```
edu.campus.taskplatform
├── common/                       # 最小公共层（统一响应、错误码、分页）
│   ├── api/ApiResponse.java
│   ├── api/PageResult.java
│   ├── error/{ErrorCode,ApiException,GlobalExceptionHandler}.java
│   └── web/Pagination.java
└── dispute/
    ├── port/                     # 与登录、任务、账务模块的集成端口（见第 5 节）
    ├── cancellation/             # 取消申请：实体、仓储、服务、控制器、DTO
    ├── appeal/                   # 已完成任务申诉
    └── config/                   # 端口缺失时的“失败关闭”占位配置
```

业务表迁移脚本：

- `src/main/resources/db/migration/V2026102101__create_cancellation_requests.sql`
- `src/main/resources/db/migration/V2026102102__create_appeals.sql`

采用较大版本号，避免与组长、后端 A 的 `V1`、`V2` 等脚本冲突；合并后由组长统一排序。

## 3. 状态迁移

取消申请：

```
PENDING_RESPONSE --同意--> APPROVED      任务 → CANCELLED，冻结报酬全额退回发布者
PENDING_RESPONSE --拒绝--> PENDING_ADMIN 任务、余额、原始流水均不变
PENDING_ADMIN    --裁决--> ARBITRATED    按裁决结果取消任务并释放冻结报酬，或保持任务不变
```

申诉：

```
PENDING --裁决--> DECIDED   任务保持 COMPLETED，原始结算流水不变；必要时产生补偿流水
```

同一任务同时最多一条 `PENDING_RESPONSE` / `PENDING_ADMIN` 申请，由服务层检查与 PostgreSQL 部分唯一索引
`ux_cancellation_active_task` 双重保证；同一参与者对同一任务最多提交一次申诉，由唯一约束
`uq_appeal_task_appellant` 保证。

## 4. 业务规则

### 4.1 取消申请

- **发起**：仅任务参与者（发布者或跑腿员）；仅 `IN_PROGRESS`、`WAITING_FOR_ACCEPTANCE`；已有活动申请时返回 409
  `CANCELLATION_ALREADY_PENDING`。
- **应答**：仅被申请方（任务中与申请人不相同的另一方）；仅 `PENDING_RESPONSE`。
  - `APPROVE`：申请转 `APPROVED`，冻结报酬全额退回发布者，任务转 `CANCELLED`；退款与状态变更同事务。
  - `REJECT`：申请转 `PENDING_ADMIN`，不触碰任务与账务。
- **裁决**：仅管理员；仅 `PENDING_ADMIN`；关联任务必须仍处于 `IN_PROGRESS` 或 `WAITING_FOR_ACCEPTANCE`。
  - `SUPPORT_CONTINUE`：`refundAmount = runnerAmount = 0`，任务状态不变。
  - `SUPPORT_REQUESTER_CANCEL`：`refundAmount` 必须等于冻结报酬，`runnerAmount = 0`，任务转 `CANCELLED`。
  - `CANCEL_PROPORTIONALLY`：`refundAmount + runnerAmount` 必须等于冻结报酬，任务转 `CANCELLED`。
- **幂等**：状态检查在 `SELECT ... FOR UPDATE` 行锁内完成；重复应答、重复裁决均返回 409。

### 4.2 已完成任务申诉

- **提交**：仅任务参与者；仅 `COMPLETED` 任务；每位参与者最多一次；被申诉方为另一位参与者；
  提交不改变任务状态、余额或原始结算流水。
- **裁决**：仅管理员；仅 `PENDING` 申诉。
  - `REJECTED`：`transferAmount` 必须为 0，不产生流水。
  - `SUPPORTED` / `PARTIALLY_SUPPORTED`：`transferAmount > 0` 且不得超过被申诉方当前可用余额，
    从被申诉方可用余额划转给申诉方，账务端口必须返回 1—2 条关联流水 ID。
    **空列表不是合法的成功结果**：返回 `null` 或空列表都视为账务异常，接口返回 500 `INTERNAL_ERROR`，
    事务整体回滚，申诉保持 `PENDING`，不会被标记为已处理。
- **幂等**：申诉行在事务内加锁，重复裁决返回 409 `APPEAL_ALREADY_DECIDED`。

### 4.3 错误码

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 字段缺失、长度越界、分页或枚举非法、裁决金额不符合语义 |
| 401 | `UNAUTHORIZED` | 未登录 |
| 403 | `FORBIDDEN` | 非参与者、非被申请方、非管理员 |
| 404 | `TASK_NOT_FOUND` / `CANCELLATION_REQUEST_NOT_FOUND` / `APPEAL_NOT_FOUND` | 资源不存在 |
| 409 | `TASK_STATE_CONFLICT` | 任务状态不允许当前操作 |
| 409 | `CANCELLATION_STATE_CONFLICT` | 申请状态不允许应答或裁决 |
| 409 | `CANCELLATION_ALREADY_PENDING` | 同一任务已有活动取消申请 |
| 409 | `APPEAL_ALREADY_EXISTS` | 同一参与者重复申诉 |
| 409 | `APPEAL_ALREADY_DECIDED` | 重复裁决 |
| 409 | `INSUFFICIENT_BALANCE` | 被申诉方可用余额不足 |
| 500 | `INTEGRATION_NOT_READY` | 公共能力尚未接入（占位实现） |
| 500 | `INTERNAL_ERROR` | 账务端口在申诉划转时返回 `null`、空列表或超过两条流水，裁决事务回滚 |

## 5. 端口与集成方式

本模块不创建、不修改用户表、任务表、余额表与流水表，只通过三个端口访问：

```java
public interface CurrentUserPort {
    Optional<AuthenticatedUser> currentUser();   // userId + USER/ADMIN
}

public interface TaskParticipationPort {
    Optional<TaskSnapshot> findTaskForUpdate(UUID taskId);  // 必须 SELECT ... FOR UPDATE
    void cancelTask(UUID taskId);                           // 只允许 IN_PROGRESS/WAITING_FOR_ACCEPTANCE → CANCELLED
}

public interface LedgerPort {
    void settleFrozenReward(UUID taskId, UUID publisherId, UUID runnerId, int refundToPublisher, int payToRunner);
    List<UUID> transferAvailable(UUID taskId, UUID appealId, UUID fromUserId, UUID toUserId, int amount);
    int availableBalance(UUID userId);
}
```

集成要求：

1. 三个端口的实现都必须加入调用方已有事务，不自行提交；账务余额与流水必须同事务。
2. `findTaskForUpdate` 必须对任务行加锁，防止“取消裁决”与“确认完成”并发时状态错乱。
3. `TaskParticipationPort.cancelTask` 应在状态不符时抛出 `TASK_STATE_CONFLICT`（409）。
4. `transferAvailable` 必须返回 1—2 条真实流水 ID，不得返回 `null` 或空列表；否则申诉裁决按
   500 `INTERNAL_ERROR` 回滚，申诉保持 `PENDING`。
5. 注册真实 Bean 后，`DisputeIntegrationFallbackConfiguration` 中的占位 Bean 因 `@ConditionalOnMissingBean`
   自动失效，无需改业务代码；集成完成后可删除该配置类与
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

在端口全部接入前，应用仍可正常启动：

- 未接入 `CurrentUserPort` → 所有取消/申诉接口返回 401（失败关闭）；
- 未接入任务或账务端口 → 相关操作返回 500 `INTEGRATION_NOT_READY`。

## 6. 数据库

`cancellation_requests`：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `request_id` | `uuid` | 主键 |
| `task_id` / `requester_id` | `uuid` | 任务、申请人 |
| `reason` | `varchar(200)` | 取消原因 |
| `response_note` | `varchar(500)` | 应答或裁决说明 |
| `status` | `varchar(32)` | 申请状态 |
| `ruling` / `ruling_detail` | `varchar(48)` / `varchar(500)` | 裁决方式与说明 |
| `refund_amount` / `runner_amount` | `integer` | 退回发布者、支付跑腿员 |
| `responded_by/at`、`arbitrated_by/at` | `uuid` / `timestamptz` | 操作留痕 |
| `created_at` / `updated_at` | `timestamptz` | 时间 |

`appeals` 与 `appeal_transaction_ids` 同理，后者保存裁决产生的关联流水 ID（最多两条）。

> `task_id`、`requester_id`、`appellant_id` 等暂不建外键，待组长合并公共用户/任务表后统一补充，
> 避免多人同时改同一张核心表与迁移顺序冲突。

## 7. 测试

```powershell
cd code\backend
.\mvnw.cmd test
```

| 测试类 | 覆盖内容 |
| --- | --- |
| `CancellationServiceTest` | 发起权限/状态/重复申请；同意取消的退款；拒绝转待裁决；裁决三种方式、金额校验、任务已完成冲突、非管理员、重复裁决、分页过滤 |
| `AppealServiceTest` | 已完成约束、参与者约束、每人一次；驳回不划转；支持/部分支持划转与余额校验；重复裁决；任务状态异常；管理员查询 |
| `DisputeApiContractTest` | 7 个接口的路径、201/200/400/401/403/404/409、统一响应结构、分页字段、ID 字符串化 |

测试使用 H2 内存库与任务/账务/登录端口的测试替身，不依赖 PostgreSQL 与其他成员代码。

## 8. 本地运行

数据库（本机 PostgreSQL 18.6）需存在：

- 数据库：`campus_task_platform`
- 登录角色：`campus_dev`，拥有该数据库所有权

```powershell
cd code\backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/campus_task_platform"
$env:DB_USERNAME = "campus_dev"
$env:DB_PASSWORD = "<本地密码>"
.\mvnw.cmd spring-boot:run
```

启动时 Flyway 自动执行两条迁移，Hibernate 以 `ddl-auto=validate` 校验实体与表结构。
当前未接入登录模块，取消/申诉接口会先被 Spring Security 拦截（401）；这是预期的失败关闭行为。

> **骨架修正**：Spring Boot 4 将 Flyway 自动配置拆分到 `spring-boot-flyway` 模块，只引入 `flyway-core`
> 时迁移脚本不会执行，且 `ddl-auto=validate` 会因缺表启动失败。已在 `pom.xml` 中改为引入
> `spring-boot-starter-flyway`（保留 `flyway-database-postgresql`）。

## 9. 与其他成员的边界

- **组长**：登录态、`CurrentUserPort` 实现、账务 `LedgerPort` 实现、统一异常/响应整合、安全配置中
  `/api/v1/admin/**` 的角色限制（本模块已在服务层校验管理员）。
- **后端 A**：`TaskParticipationPort` 实现；任务行锁；任务取消状态迁移。
- **前端 B**：取消/申诉/管理员页面，按 `openapi.yaml` 的字段与错误码对接。
- **本模块不负责**：通知发送（模块 9）、投诉处理、评价、任务主流程与账务表本身。
