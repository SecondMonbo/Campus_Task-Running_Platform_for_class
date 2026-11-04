# Part A 任务与通知功能 · 交付说明

## 一、任务完成情况（对照要求）

### 1. 后端：任务编辑 / 撤回 / 评价接口 + 通知功能
- **编辑、撤回、评价接口（复用已有，已联调通过）**
  - `PATCH /api/v1/tasks/{id}`：仅待接单、发布者本人可改，参数 title/description/reward/deadline/pickupLocation/deliveryLocation；修改报酬时按差额补冻结或解冻。
  - `POST /api/v1/tasks/{id}/withdrawal`：仅待接单、发布者本人，body `{reason}`，撤回后全额解冻。
  - `POST /api/v1/tasks/{id}/reviews`：仅发布者、仅已完成任务、每任务一次，body `{score 1-5 必填, content ≤200 选填}`，返回评价编号 reviewId。
- **新增通知模块（前后端配合补齐）**
  - 数据库：Flyway 迁移 `V2026110401__create_notifications.sql` 新建 `notification` 表（含 `user_id`、`read` 标记、`related_task_id`、`created_at` 与 `idx_notification_user(user_id, created_at DESC)` 索引）。
  - 后端包 `edu.campus.taskplatform.notification`：实体、仓储、`NotificationService`、`NotificationController`。
    - `GET /api/v1/notifications?page&size&unreadOnly`：分页列表（size 仅允许 10/20/50），支持只看未读。
    - `POST /api/v1/notifications/{id}/read`：标记已读，幂等，他人通知返回 404。
  - `TaskService` 接入 4 个通知触发点：接单→通知发布者"任务被接取"；送达→通知发布者"任务已送达，请及时验收"；完成→通知跑腿员"任务已完成，报酬已结算"；评价→通知跑腿员"收到新的评价，评分 X 星"。通知失败不回滚任务主流程（safeNotify）。

### 2. 前端：接入编辑 / 撤回 / 评价 + 通知中心
- `api/index.js` 新增 `updateTask`（PATCH）、`withdraw`、`review` 方法；`notifications` 列表与 `markNotificationRead` 已完整调用通知契约。
- `domain/tasks.js` 的 `taskPermissions` 新增 `edit / withdraw`（发布者 + 待接单）、`review`（发布者 + 已完成）。
- `TaskActions.vue` 新增三个操作：
  - **编辑任务**：弹窗表单（标题/说明/报酬/截止时间/取送地点），提交前 `validateTaskDraft` 校验，报酬差额走接口补冻结/解冻。
  - **撤回任务**：必填撤回原因，提交后列表刷新为"已取消"，报酬退回。
  - **评价跑腿员**：1-5 星点选 + 选填内容，提交成功回显"评价编号"。
  - 保留原有接单/送达/验收/取消/申诉/投诉全部操作；文案字典补齐。
- 通知中心（`NotificationsView.vue` + App.vue 顶栏）：
  - 列表展示通知标题、内容（含任务名）、时间、"未读"标签、"查看任务"、"标记已读"。
  - "全部通知 / 仅看未读"筛选；顶栏铃铛未读角标/圆点随未读数变化。
- `style.css` 新增 `.rating` / `.rating-star` 评分星样式。

## 二、验证证据
- 后端 `mvnw test`：**93 个测试全部通过**（0 失败 0 错误，BUILD SUCCESS）。
- 前端 `npm run build`：构建成功（vite，dist 已生成）。
- Flyway 迁移：`Successfully applied 1 migration ... now at version v2026110401`。
- API 端到端联调（HTTP 层）：
  - 编辑：发布任务 reward 30 → PATCH 改标题、reward 35，状态保持待接单、标题已更新。
  - 撤回：`POST withdrawal` → 状态变 CANCELLED，报酬退回。
  - 全状态机：接单 → 送达 → 完成 → 评价（score 5）全链路成功。
  - 通知：发布者收到 2 条（接取、送达）、跑腿员收到 2 条（完成、评价）；未读筛选 total=2；标记已读后未读 2→1。
- 浏览器端演示（真实登录 + 页面操作，均有截图）：
  - 待接单任务详情显示"编辑任务 / 撤回任务"按钮 → 编辑弹窗预填当前字段 → 修改报酬提交 → 提示"任务已更新"、报酬更新 → 撤回提交 → 提示"任务已撤回，报酬已退回"。
  - 已完成任务详情显示"评价跑腿员"按钮 → 选 5 星 + 填写内容 → 提交 → 提示"评价已提交"并回显评价编号。
  - 通知中心列表正确显示通知（含"未读"标签）→ 点"标记已读"后该条未读消失 → "仅看未读"只显示剩余未读，底部显示"共 N 条记录"。

## 三、GitHub
- 分支 `dev-Rrrr-Wang`，功能提交 `5889b7f`（已推送）。
- **PR #14（open，未合并 main）**：head=dev-Rrrr-Wang，base=main，URL：https://github.com/SecondMonbo/Campus_Task-Running_Platform_for_class/pull/14
- 说明：Part A 仅提交 PR 申请，不直接合并主分支，由组长 Review 后合入。

## 四、本地演示账号
- 发布者：publisher_demo / User123456
- 跑腿员：runner_demo / User123456
- 管理员：admin_demo / Admin12345

## 五、本地复现步骤
1. 启动 PostgreSQL 服务（postgresql-x64-18）。
2. 后端目录设置环境变量并启动：
   - `DB_USERNAME=campus_dev`
   - `DB_PASSWORD=campusdev`
   - `DB_URL=jdbc:postgresql://localhost:5432/campus_task_platform`
   - 执行 `mvnw spring-boot:run`（监听 8080，Flyway 自动建 notification 表）。
3. 前端目录执行 `npm run dev`（5173），浏览器用发布者账号登录。
   - 发布一个待接单任务 → 详情页点"编辑任务 / 撤回任务"演示修改与撤回。
   - 用跑腿员接单→送达，发布者验收完成后 → 详情页点"评价跑腿员"。
   - 进入"通知中心"查看通知列表、未读筛选、标记已读，顶栏铃铛角标随之变化。
