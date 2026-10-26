# 前端 A · 第 1 周交付

## 交付范围

- 登录与注册页面：`/login`、`/register`。
- 任务列表页面：`/tasks`，展示任务信息、OpenAPI 状态，支持状态筛选、关键字搜索和分页。
- 本地 Mock：注册/登录成功后进入任务列表，预置 OpenAPI 定义的五种任务状态。

## OpenAPI 对齐

接口以 `code/development_doc/openapi.yaml` 为准，不新增或修改公共字段。

| 页面行为 | 接口 | 字段/参数 |
| --- | --- | --- |
| 注册 | `POST /api/v1/auth/register` | `username`、`password`、`displayName`、可选 `phone` |
| 登录 | `POST /api/v1/auth/login` | `username`、`password` |
| 查询当前用户 | `GET /api/v1/auth/me` | `userId`、`username`、`displayName`、`role` |
| 获取任务列表 | `GET /api/v1/tasks` | `page`、`size`、可选 `status`、`keyword` |
| 展示任务 | `Task` | `taskId`、`title`、`description`、`reward`、`deadline`、`pickupLocation`、`deliveryLocation`、`status`、`publisherId`、`runnerId`、`createdAt` |

任务状态使用 OpenAPI 的五个枚举：`WAITING_FOR_TAKER`（待接单）、`IN_PROGRESS`（进行中）、`WAITING_FOR_ACCEPTANCE`（待验收）、`COMPLETED`（已完成）、`CANCELLED`（已取消）。

## Mock 成功与失败路径

运行 `npm.cmd run dev:demo` 后，Mock 账号和任务只保存在当前进程中，服务停止或重启后重置。

- 成功：注册返回 `201`，自动登录并进入任务列表；列表展示五种状态各一条任务。
- 失败：重复用户名返回 `409 USERNAME_TAKEN`；错误凭据返回 `401 INVALID_CREDENTIALS`；任务列表请求失败时显示错误信息和重试入口。

CSRF Token 按 OpenAPI 作为直接 JSON 对象读取；其余业务成功响应按统一 `ApiResponse` 解包。此 Mock 用于本周前端页面演示。

## 构建

```powershell
npm.cmd ci
npm.cmd run build
```

2026-10-21 在精简后的 A 专用包中执行：`npm.cmd run build` 成功（Vite 8.3.4，转换 39 个模块）。按验收要求运行了构建，未运行测试套件。

## PR 验收信息

- 文件范围：登录/注册页、任务列表页、本地 Mock、路由与共享布局、样式和运行说明。
- 验收命令：`npm.cmd run build`。
- 构建结果：PASS，Vite 8.3.4，39 modules transformed。
