# Part B 管理员处理流程 · 交付说明

## 一、任务完成情况（对照要求）

### 1. 后端：管理员权限、状态校验、账务结果
- **管理员权限（双重防护）**
  - HTTP 层：`SecurityConfiguration` 对 `/api/v1/admin/**` 配置 `hasRole("ADMIN")`，普通用户在过滤器链即被拦截（403）。
  - 应用层：`AppealService` / `CancellationService` 裁决入口均先 `requireAdmin()`。
- **状态校验**
  - 申诉裁决：必须 PENDING 且关联任务 COMPLETED；已裁决再提交返回 409，不重复处理。
  - 取消裁决：必须 PENDING_ADMIN 且任务仍可取消；对申请行 `findByIdForUpdate` 加悲观锁，防止并发裁决。
- **账务结果**
  - 申诉：按裁决从被申诉方可用余额划转（`transferAvailable`），并校验流水条数；驳回时金额必须为 0。
  - 取消：按 ruling 结算冻结报酬（`settleFrozenReward`）并取消任务；全额取消退全款、继续履约两金额为 0、按比例取消两项之和等于冻结报酬。

### 2. 前端：管理工作台
- 可用模块：**取消裁决、任务申诉、任务查询**（任务查询走公共 GET /tasks）。
- 列表展示关联任务链接（可查看关联任务）、状态、提交时间；待处理记录提供“裁决”按钮。
- 裁决弹窗：填写裁决结果、金额与说明，提交后自动刷新列表并关闭弹窗。
- 投诉处理、用户查询、交易流水、操作日志四个无后端入口的标签页**禁用并标注“暂未开放”**，点击不切换。
- 路由 `/admin` 增加 ADMIN 守卫，非管理员访问被拦回 /my-tasks。

### 3. 数据库与管理员提升
- PostgreSQL 18，库 `campus_task_platform`，Flyway 6 个迁移全部成功，共 10 张表。
- 手动提升管理员：`UPDATE app_user SET role='ADMIN' WHERE username='admin_demo';`
- 管理员登录返回 role=ADMIN，前端据此跳转 /admin 管理后台。

## 二、验证证据
- 后端 `mvnw test`：**93 个测试全部通过**。
- 前端 `npm run build`：构建成功。
- 端到端联调：**30 项断言全部 PASS**。
  - 申诉：PENDING → 管理员 SUPPORTED 划转 20 → DECIDED；重复裁决 409，无二次划转。
  - 取消：PENDING_RESPONSE → 对方拒绝 → PENDING_ADMIN → 按比例裁决（退 25 / 跑腿员 15）→ ARBITRATED，任务 CANCELLED。
  - 普通用户访问/提交管理员接口均返回 **403**。
- 最终余额（可用/冻结）：admin_demo 100/0、publisher_demo 75/0、runner_demo 125/0。

## 三、GitHub
- 分支 `dev-Rrrr-Wang`，提交 `8c145e2`（已推送）。
- **PR #12 已合并进 main**，合并提交 `84a552e`，main 已包含全部后端与前端改动。

## 四、本地演示账号
- 管理员：admin_demo / Admin12345
- 普通用户：publisher_demo、runner_demo / User123456

## 五、本地复现步骤
1. 启动 PostgreSQL 服务（postgresql-x64-18）。
2. 后端目录设置环境变量并启动：
   - `DB_USERNAME=campus_dev`
   - `DB_PASSWORD=campusdev`
   - `DB_URL=jdbc:postgresql://localhost:5432/campus_task_platform`
   - 执行 `mvnw spring-boot:run`（监听 8080，Flyway 自动建表）。
3. 前端目录执行 `npm run dev`，浏览器用管理员账号登录，进入 /admin 演示裁决流程。
