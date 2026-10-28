# 校园跑腿平台 API 约定（V1）

> 本文作为前后端并行开发的共同约定。路径、字段和状态枚举一经组内确认，不要在单个模块中自行变更；需要调整时先更新本文并通知相关成员。
>
> 可导入 Apifox 的机器可读接口定义见[openapi.yaml](./openapi.yaml)。
>
> 前端调用与当前后端实现的差异、ID 类型基准及补齐优先级见[前后端接口差异与ID统一.md](./前后端接口差异与ID统一.md)。
>
> 五人职责边界、主流程配对和开发顺序见[分工边界与任务图.md](./分工边界与任务图.md)。

## 1. 总体约定

- API 前缀：`/api/v1`。
- 使用 JSON 请求和响应；字段名采用 `camelCase`。
- ID 由后端生成，在 JSON 中统一按字符串传递。服务端内部任务 ID 使用 UUID 字符串（数据库 `VARCHAR(36)`）；用户 ID 使用 `Long/BIGINT`，API 中仍序列化为字符串。取消申请、申诉和账务流水等独立资源可使用 UUID。
- 时间使用 ISO 8601 UTC 格式，例如 `2026-10-17T13:30:00Z`。
- 虚拟币使用整数。任务报酬是单一金额，不拆分跑腿费和小费。
- 当前用户身份从登录会话读取；请求体不接受前端传入的操作者 `userId` 或 `role`。
- 服务端必须检查登录状态、角色、任务参与关系和当前业务状态，不能依赖前端隐藏按钮来实现权限控制。
- 列表接口统一分页参数：`page` 从 `0` 开始，`size` 只接受 `10`、`20`、`50`，默认 `page=0&size=10`。
- 空列表是成功响应，返回空 `items`，不是错误。

## 2. 登录态与安全

本项目先采用 Spring Security 服务端 Session，避免首版同时引入 JWT、刷新令牌等复杂度。

- 登录成功后，服务端设置 Session Cookie；前端不得把会话凭证保存在业务数据或日志中。
- 注册只创建普通用户，自动建立登录会话，并在同一数据库事务中创建钱包、发放 100 虚拟币初始余额及写入初始入账流水；客户端不得指定角色或初始余额。
- 管理接口仅允许 `ADMIN` 角色访问。前端路由的角色判断只负责用户体验，后端必须再次鉴权；未登录返回 `401`，已登录但角色不足返回 `403`。
- 本地开发由 Vite 将 `/api` 代理到后端，减少跨域和 Cookie 配置问题。
- 写操作按 Spring Security 的 CSRF 保护要求携带 CSRF Token。
- 除 localhost 开发外，部署环境必须使用 HTTPS；生产 Cookie 应启用 `HttpOnly`、`Secure`。
- 密码不得明文保存或写入日志。

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/auth/register` | 注册 |
| `POST` | `/api/v1/auth/login` | 登录 |
| `POST` | `/api/v1/auth/logout` | 退出登录 |
| `GET` | `/api/v1/auth/me` | 获取当前登录用户及角色 |
| `GET` | `/api/v1/auth/csrf` | 获取写操作所需的 CSRF Token |

用户资料编辑接口尚未实现；当前登录用户信息统一从 `GET /api/v1/auth/me` 获取，不使用未实现的 `/api/v1/users/me`。

## 3. 统一响应与错误

成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "taskId": "generated-id"
  }
}
```

分页响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "items": [],
    "page": 0,
    "size": 10,
    "total": 0
  }
}
```

错误响应：

```json
{
  "code": "TASK_STATE_CONFLICT",
  "message": "任务当前状态不允许此操作",
  "data": null
}
```

| HTTP 状态码 | 使用场景 |
|---|---|
| `200` | 查询或操作成功 |
| `201` | 创建成功 |
| `400` | 请求格式或参数错误 |
| `401` | 未登录或登录态失效 |
| `403` | 已登录但无权限 |
| `404` | 资源不存在，或当前用户不可见 |
| `409` | 状态冲突、并发抢单失败、余额不足或重复业务操作 |
| `500` | 未预期的服务端错误 |

错误码建议采用稳定的大写名称，例如 `VALIDATION_ERROR`、`TASK_NOT_FOUND`、`TASK_STATE_CONFLICT`、`INSUFFICIENT_BALANCE`、`DUPLICATE_ACTION`。前端根据 HTTP 状态码和 `code` 展示提示，不解析服务端堆栈。

## 4. 主要接口目录

### 4.1 用户、任务与钱包

| 方法 | 路径 | 主要用途 |
|---|---|---|
| `POST` | `/api/v1/tasks` | 发布任务 |
| `GET` | `/api/v1/tasks` | 浏览、筛选和分页查询任务 |
| `GET` | `/api/v1/tasks/{taskId}` | 查询任务详情 |
| `GET` | `/api/v1/users/me/tasks?as=publisher` | 查询本人发布的任务 |
| `GET` | `/api/v1/users/me/tasks?as=runner` | 查询本人接取的任务 |
| `PATCH` | `/api/v1/tasks/{taskId}` | 修改仍处于待接单状态的本人任务 |
| `POST` | `/api/v1/tasks/{taskId}/withdrawal` | 撤回尚未被接取的本人任务 |
| `POST` | `/api/v1/tasks/{taskId}/acceptance` | 接取任务 |
| `POST` | `/api/v1/tasks/{taskId}/delivery` | 跑腿员标记送达 |
| `POST` | `/api/v1/tasks/{taskId}/completion` | 发布者确认完成并结算 |
| `POST` | `/api/v1/tasks/{taskId}/reviews` | 提交任务评价 |
| `GET` | `/api/v1/wallet` | 查询本人余额 |
| `GET` | `/api/v1/wallet/transactions` | 查询本人虚拟币流水 |

发布任务请求示例：

```json
{
  "title": "代取资料",
  "description": "到教学楼领取资料并送到宿舍区",
  "reward": 30,
  "deadline": "2026-10-18T09:00:00Z",
  "pickupLocation": "教学楼A座",
  "deliveryLocation": "宿舍区"
}
```

接单请求不传接单人 ID；服务端从登录态确定跑腿员。并发接单时必须保证最多一个请求成功，其他请求返回 `409`，不得出现重复接单或重复扣款。

### 4.2 取消申请与已完成任务申诉

管理员列表查询和裁决接口要求当前会话角色为 `ADMIN`；普通登录用户不得通过直接调用 API 绕过管理端页面限制。

| 方法 | 路径 | 主要用途 |
|---|---|---|
| `POST` | `/api/v1/tasks/{taskId}/cancellation-requests` | 对进行中或待验收任务发起取消申请 |
| `POST` | `/api/v1/cancellation-requests/{requestId}/response` | 被申请方同意或拒绝取消 |
| `GET` | `/api/v1/admin/cancellation-requests?status=PENDING_ADMIN` | 管理员查询待裁决取消申请 |
| `POST` | `/api/v1/admin/cancellation-requests/{requestId}/arbitration` | 管理员裁决取消争议 |
| `POST` | `/api/v1/tasks/{taskId}/appeals` | 任务参与者对已完成任务提交申诉 |
| `GET` | `/api/v1/admin/appeals?status=PENDING` | 管理员查询待处理申诉 |
| `POST` | `/api/v1/admin/appeals/{appealId}/decision` | 管理员裁决申诉 |

取消申请应答示例：

```json
{
  "decision": "REJECT",
  "note": "补充说明"
}
```

申诉提交示例：

```json
{
  "reason": "FULFILLMENT_ISSUE",
  "description": "实际送达物品与约定不符"
}
```

申诉裁决示例：

```json
{
  "result": "PARTIALLY_SUPPORTED",
  "transferAmount": 10,
  "decisionNote": "根据双方提交的说明，部分支持"
}
```

申诉裁决必须满足以下规则：

1. 只有任务参与者可以对已完成任务申诉；同一参与者对同一任务最多提交一次。
2. 申诉裁决不改变任务“已完成”状态，也不修改原始结算流水。
3. 支持或部分支持时，从被申诉方可用余额向申诉人划转裁决金额；驳回时金额为 `0` 且不变更余额。
4. 余额检查、双方余额更新、申诉状态更新和双方关联申诉的流水写入必须在同一数据库事务中完成；任何一步失败均整体回滚。
5. 重复提交或重复裁决不得导致重复划转或生成重复流水。

### 4.3 通知与管理端

本节是目标接口目录，不代表所有路由均已实现。当前已实现取消争议和申诉的管理员查询/裁决；通知、投诉、管理员用户、交易和操作日志接口仍待实现。具体状态以[前后端接口差异与ID统一.md](./前后端接口差异与ID统一.md)为准。

| 方法 | 路径 | 主要用途 |
|---|---|---|
| `GET` | `/api/v1/notifications` | 查询本人通知 |
| `POST` | `/api/v1/notifications/{notificationId}/read` | 标记通知已读 |
| `POST` | `/api/v1/complaints` | 提交投诉 |
| `GET` | `/api/v1/admin/complaints` | 管理员查询投诉 |
| `POST` | `/api/v1/admin/complaints/{complaintId}/actions` | 管理员处理投诉 |
| `GET` | `/api/v1/admin/users` | 管理员查询用户 |
| `GET` | `/api/v1/admin/transactions` | 管理员查询交易记录 |
| `GET` | `/api/v1/admin/operation-logs` | 管理员查询操作日志 |

## 5. 状态枚举

接口上传递稳定的大写英文枚举；中文仅用于文档和界面显示。

任务状态只能是以下五种：

| API 值 | 界面显示 |
|---|---|
| `WAITING_FOR_TAKER` | 待接单 |
| `IN_PROGRESS` | 进行中 |
| `WAITING_FOR_ACCEPTANCE` | 待验收 |
| `COMPLETED` | 已完成 |
| `CANCELLED` | 已取消 |

取消申请状态：

| API 值 | 界面显示 |
|---|---|
| `PENDING_RESPONSE` | 待应答 |
| `PENDING_ADMIN` | 待管理员处理 |
| `APPROVED` | 已同意 |
| `ARBITRATED` | 已裁决 |

已完成任务申诉状态：

| API 值 | 界面显示 |
|---|---|
| `PENDING` | 待处理 |
| `DECIDED` | 已裁决 |

不提供允许客户端任意修改任务状态的通用接口；只能调用接单、送达、验收、取消等明确的业务操作。

## 6. 账务、事务与并发约定

- 发布任务冻结报酬、完成任务结算、取消裁决和申诉划转时，余额变更与交易流水必须在同一数据库事务中提交或回滚。
- 不提供前端直接修改余额的接口；余额只由明确的业务操作变更。
- 服务端每次执行写操作时重新读取并校验最新状态，不信任页面中缓存的状态。
- 接单使用事务内条件更新或等效并发控制，保证同一任务只能被一人接取。
- 重复请求不得重复扣款、入账、退款或划转；对于可能因网络重试重复提交的操作，后端应通过状态约束和业务唯一性保证幂等。
- 错误要显式返回稳定错误码，不要吞掉异常或用成功响应包装失败。

## 7. 第一条端到端验收路径

按顺序使用两个普通账号完成以下流程，并检查任务状态、余额与流水：

1. 注册并登录。
2. 账号 A 发布任务并确认报酬被冻结。
3. 账号 B 接取任务。
4. 账号 B 标记送达。
5. 账号 A 确认完成。
6. 账号 B 确认收到结算，双方查看对应流水。

主流程稳定后，再验收取消申请/管理员裁决、已完成任务申诉/管理员裁决，以及并发接单、重复请求、余额不足和事务失败等情况。
