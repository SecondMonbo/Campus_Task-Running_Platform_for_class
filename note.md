# note.md · 本周 Part B（用户中心方向）关键记忆与核心点

> 用途：把这次改动的原因、契约、复用关系和踩过的坑留在这里，方便后续接手（含未来的自己）。
> 本周任务：① 用户中心（资料编辑 + 密码修改，前后端）；② 用户对非法任务的直接举报（接口复用原有 complaint 逻辑）；③ 个人主页预留评分展示接口位；④ 本文件。

## 一、先说结论

- 后端新增 `user` 包，提供 3 个"本人"接口，全部走登录会话、请求体不收 userId。
- 举报**没有新增接口**，复用队友已实现的 `POST /api/v1/complaints`：只放开"非任务参与者也可举报任务内容"这一层限制，并把前端补上独立入口。
- 信誉分没有新造字段：`creditScore` 早在 `openapi.yaml` 的 `User` schema 里就预留了（0-100），本次把它接进个人资料响应并保持 `null`，前端按"待接入"占位渲染。
- 没有新增数据库迁移：只用了已有的 `app_user`、`task_review`、`complaints` 表。

## 二、接口契约（本人维度）

| 方法 | 路径 | 作用 | 要点 |
| --- | --- | --- | --- |
| GET | `/api/v1/users/me/profile` | 查看个人资料 | 返回 username / displayName / phone / role / createdAt / rating / reviewCount / creditScore |
| PATCH | `/api/v1/users/me` | 编辑资料 | body `{displayName(必填,1-50), phone(选填,≤30,^[0-9+\-\s]*$)}`；username 与 role 不可改 |
| POST | `/api/v1/users/me/password` | 修改密码 | body `{currentPassword, newPassword(8-128)}`；当前密码错误或新旧相同均 400 `VALIDATION_ERROR` |

响应壳仍是 `ApiResponse{code,message,data}`；异常仍是 `TaskException(ErrorCode,message)`。
`SecurityConfiguration` 只放行 auth 三个接口与 health，`/users/me/**` 天然需要登录，无需改安全配置。

## 三、举报为什么能"复用原有逻辑"

原 `ComplaintService.create` 的规则是：带 `taskId` 时**必须是发布者或跑腿员**，否则 403；参与者会被自动把 target 设成对方；不能投诉自己。

本次只改一处判断：

- **参与者**：行为完全不变（自动关联对方、指定错误对象仍报"被投诉人必须是任务另一位参与者"）。
- **非参与者**：允许提交，但**不允许指定 `targetUserId`**（带了就 403 `FORBIDDEN`，提示"非任务参与者只能举报任务内容，不能指定被投诉用户"）。这类记录 `task_id` 有值、`target_user_id` 为空，语义就是"举报这条任务内容"。

所以前端 `TaskActions.vue` 的"举报任务"和"投诉"是**同一个 `api.complain`**，区别只在举报不传 `targetUserId`；权限上 `taskPermissions.report = Boolean(user)`，任意登录用户可见。

## 四、评分展示是怎么"预留"的

- 已真实计算：`rating`（作为跑腿员收到的评价均分，保留 1 位小数）、`reviewCount`（评价条数），数据来源 `task_review` 的两个聚合查询。
- 预留占位：`creditScore` 恒为 `null`（契约允许 nullable），前端个人中心固定显示"信誉分（0-100）：待接入"，并注明"接口已预留"。
- 以后接信誉分机制：只改 `UserCenterService.toProfile()` 里传 `null` 的那一处，前端不用动。

## 五、本地跑起来（含两个必踩的坑）

**坑 1：`git` 不在 PATH**，实际在 `D:\Git\cmd`，每条命令前先执行：

```powershell
$env:Path = "D:\Git\cmd;" + $env:Path
```

**坑 2：`mvnw.cmd` 需要系统 PowerShell**，沙箱 PATH 里没有 `System32` 会报 `'powershell' is not recognized ... Cannot start maven from wrapper`：

```powershell
$env:Path = "C:\Windows\System32;C:\Windows\System32\WindowsPowerShell\v1.0;C:\Windows;C:\Windows\System32\Wbem;" + $env:Path
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
```

**坑 3：`git fetch origin` 不会更新 `origin/main`**，因为 `remote.origin.fetch` 只配了 `dev-Rrrr-Wang` 一条 refspec。要同步 main：

```powershell
git fetch origin refs/heads/main:refs/remotes/origin/main
```

启动后端（先确保 `postgresql-x64-18` 服务在跑、5432 已监听）：

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/campus_task_platform"
$env:DB_USERNAME="campus_dev"; $env:DB_PASSWORD="campusdev"
cd code\backend; .\mvnw.cmd spring-boot:run   # 8080，Flyway 自动迁移
```

前端：

```powershell
cd code\frontend; npm install; npm run dev    # 5173
```

演示账号：`publisher_demo / User123456`（id=2）、`runner_demo / User123456`（3）、`admin_demo / Admin12345`（1，ADMIN）。
写接口都要 CSRF：先 `GET /api/v1/auth/csrf` 取响应体 `token`，再放进请求头 `X-XSRF-TOKEN`。

## 六、验证留痕

- 后端 `mvnw test`：**134 个测试全部通过**（0 失败 0 错误，BUILD SUCCESS），其中新增 `UserCenterIntegrationTest` 7 个用例。
- 前端 `npm run build`：构建成功，产物含 `ProfileView-*.js`。
- HTTP 端到端（`partB_usercnt_e2e.ps1` / `partB_usercnt_e2e_out.txt`）：资料读取与编辑、非法显示名 400、错误旧密码 400、新旧相同 400、改密成功、旧密码登录 401 / 新密码登录 200、改回成功；路人举报任务成功且 `targetUserId` 为空、路人带 `targetUserId` 被 403、参与者投诉自动指向跑腿员。
- 浏览器演示：个人中心页（`partB_profile.png`）、保存成功提示（`partB_profile_toast.png`）、任务详情"举报任务"提交后回显举报编号（`partB_report_submitted.png`）。
- 数据库核对（`complaints` 表）：`reporter_id=3` 的举报记录 `target_user_id` 为空、`task_id` 有值、`status=PENDING`；`reporter_id=2` 的投诉记录 `target_user_id=3`。两条链路互不干扰。

## 七、这次没做什么（避免误以为已实现）

- 没有做信誉分算法，也没有做评分维度的权限/展示分级；`creditScore` 只是占位。
- 没有新增"我的举报列表"页面：举报落库后由管理员在 `AdminView` 的投诉/举报待处理列表统一处理。
- 没有改动管理员处理流程（Part B 旧版已由队友在 main 完成）。
- 没有新增 Flyway 迁移（`V2026110501__create_complaints.sql` 已由队友提供，本次直接复用）。

## 八、Git

- 工作分支 `dev-Rrrr-Wang`，目标 `main`。
- 只提交 PR，**不合并 main**，等组长 Review。
- 提交信息建议：`feat(user-center): 用户中心资料/密码与非法任务举报入口`。

---

# 2026-11-18 新增 · 前后端 A 部分：信用分

> 本周任务：后端信用分数据表与迁移、初始 80 分、变更记录、记分服务，接入评价 / 按时完成 / 超时事件并保证幂等；前端个人中心展示分数、档位和变更记录，任务接取界面可查看他人主页（仅基本信息 + 信用档位）。评分规则来自 `code/development_doc/API约定.md` §4.4（main 的 `7f11396 docs: 更新信用分评分规则`）。

## 九、核心结论

- 分值单独建表，**没有**加到 `app_user`：`credit_score`（一人一行、0–100 约束）+ `credit_score_record`（追加式台账）。
- 迁移 `V2026111801__create_credit_scores.sql` 给存量用户统一补 80 分并写 `INITIALIZATION` 基线记录；新注册用户在 `AuthService.register` 里直接初始化；读取/记分时再做一次 lazy ensure 兜底。
- 记分统一走 `CreditScoreService.applyEvent(userId, eventType, sourceId, requestedDelta, reason)`：先查 `(event_type, source_id)` 是否已存在，存在直接返回；分值变更按 `0–100` 截断并计算实际变动值；分值与记录在同一事务写入。
- 幂等键：评价用 `reviewId`，按时/超时用 `taskId`；数据库再加 `uq_credit_event(event_type, source_id)` 唯一约束兜底。
- 他人主页只返回 `creditTier` / `creditTierLabel` / `completedTasks`，不返回分数与记录；本人走 `/users/me/credit` 看精确值与明细。

## 十、接口与数据

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/users/me/credit?page=&size=` | 本人：`score`、`tier`、`tierLabel`、`permissionHint`、`recoveryHint`、`records`（分页） |
| GET | `/api/v1/users/{userId}/profile` | 他人主页：`displayName`、`role`、`creditTier`、`creditTierLabel`、`completedTasks`、`memberSince` |
| GET | `/api/v1/users/me/profile` | 原有资料接口，`creditScore` 已由 `null` 改为真实分值 |

- `size` 仍只允许 `10/20/50`，`page>=0`；未登录 401；未知用户 404；非数字 userId 400 `VALIDATION_ERROR`。
- `/users/me/credit` 与 `/users/me/profile` 属于字面量路径，优先于 `/users/{userId}/profile` 模板匹配，已用 MockMvc 用例验证互不抢占。

表结构要点：

- `credit_score(user_id PK, score, updated_at)`，`CHECK (score BETWEEN 0 AND 100)`。
- `credit_score_record(record_id, user_id, event_type, source_id, reason, requested_delta, actual_delta, score_before NULL, score_after, admin_id NULL, created_at)`，`UNIQUE(event_type, source_id)`。
- 记录字段覆盖文档要求的“事件类型及 ID、原因、请求变动、实际变动、变更前后分值、时间”；`admin_id` 为后续管理员复核预留。
- 通知新增 `CREDIT` 类型，迁移 `V2026111802__add_credit_notification_type.sql` 重建 `ck_notification_type` 约束（`TASK/SYSTEM/CREDIT`）。

## 十一、事件口径与实现位置

| 事件 | 分值 | 触发位置 | 幂等键 |
| --- | ---: | --- | --- |
| 初始化 | 0（基线 80） | 迁移 / `AuthService.register` / `ensureAccount` | `INITIALIZATION` + `user:{id}` |
| 5 星评价 | +2 | `TaskService.createReview` → `applyReview` | `reviewId` |
| 3–4 星评价 | 0（留痕不通知） | 同上 | `reviewId` |
| 1–2 星评价 | -3（30 天首次），后续 0 | 同上 | `reviewId` |
| 按时完成 | +1（`deliveredAt <= deadline`） | `TaskService.completeTask` → `applyCompletionOutcome` | `taskId` |
| 超时送达 | -5（`deliveredAt > deadline + 24h`） | `TaskService.deliverTask` 先记；`completeTask` 再兜底一次 | `taskId` |

两个容易写错的点：

1. **30 天低星判定必须在保存本次评价之前统计**。`createReview` 先算 `countByReviewerIdAndRevieweeIdAndScoreLessThanEqualAndCreatedAtAfter(..., 2, now-30d) == 0`，再把 `firstLowStar` 传给记分服务；保存后再查会把本次算进去，导致永远不扣分。
2. **超时判定是“截止后 24 小时”而不是“晚于截止”**。截止后 0–24 小时不加不扣；`deliverTask` 与 `completeTask` 都会调用同一个幂等事件，重复不会二次扣分。

截断与通知：

- `after = clamp(before + requestedDelta, 0, 100)`，`actualDelta = after - before`；`actualDelta == 0` 仍写记录但不通知。
- 非零变动写 `NotificationType.CREDIT`，标题为“信用分提升 / 信用分变动”，内容含原因与 `before → after`；通知失败只记 warn，不影响记分。

## 十二、档位与文案

`CreditTier` 与文档边界完全一致：`90–100` 优秀、`75–89` 良好、`60–74` 预警、`0–59` 受限；恰为 60 不算受限，恰为 75 已解除限制。档位同时给出：

- `permissionHint`：优秀/良好可正常使用；预警提示履约风险；受限提示暂不可接普通任务、发布需复核。
- `recoveryHint`：连续完成 10 单且无负向事件恢复至 75 的说明。

前端 `domain/credit.js` 只做展示映射（中文档位名、`+2/-3/0` 格式化），真实档位以服务端返回为准。

## 十三、前端改动

- `ProfileView.vue`：信用分卡展示分数、档位徽标、权限/恢复说明；下方新增“信用分变更记录”分页列表（事件名、原因、`+/-` 变动、`before → after`、时间）。
- `TaskDetailView.vue`：任务信息区新增“查看发布者主页”，`AppModal` 内只展示昵称、身份、信用档位、累计完成单数、注册时间，并明确提示不显示精确分值。
- `api/index.js` 新增 `myCredit` / `publicProfile`；`domain/notifications.js` 增加 `CREDIT: '信用分通知'`。
- 样式补在 `style.css` 的“信用分（前后端 A 部分）”块：`.tier-badge`、`.credit-records`、`.credit-delta`、`.credit-pager` 等。

## 十四、设计取舍（避免被误读）

- **分值与记录同事务**：满足“账面分数变化和记录写入必须同一事务”的硬要求；没有引入 outbox / 独立消费者重试队列，因此如果信用分写入本身失败，会连同任务操作一起回滚。文档中的“待处理事件 + 独立重试”属于实现边界，本周未做。
- **低分限制只展示不执行**：`<60` 禁接单、发布进复核队列、恢复解除限制均未接入任务流程；只返回档位与说明文案，不假装已经拦截。
- **未接入**：投诉扣分 / 复核、取消责任方扣分、连续履约恢复、管理员复核调整、异常订单超时责任确认。
- 评价接口本身一条任务只能评价一次；信用分再按 `reviewId` 幂等，双保险。

## 十五、验证留痕（2026-11-18）

- 后端 `mvnw test`：**150 个测试全部通过**（0 失败 0 错误，BUILD SUCCESS），新增 `CreditScoreIntegrationTest` 8 个用例：初始化基线、评价规则与幂等、按时/超时与 24h 宽限、上下限截断、本人查询、他人主页隐藏分值、鉴权/分页/404、任务评价接口联动。
- 前端 `npm test`：**41 个测试通过**；`npm run build` 成功（产物含 `ProfileView-*.js`、`TaskDetailView-*.js`）。
- Flyway（真实 PostgreSQL 18.6）：`Successfully applied 3 migrations`，版本至 `2026111802`；`credit_score` 行为 `(1,80) (2,80) (3,83)`。
- HTTP 端到端（`credit_e2e.ps1` / `credit_e2e_out.txt`）：跑腿员初始 80 → 发布/接单/送达/完成/5 星评价后 **83**，记录 3 条（`INITIALIZATION 0 → 80`、`ON_TIME_COMPLETION +1 80 → 81`、`REVIEW_5_STAR +2 81 → 83`）；`/users/me/profile.creditScore=83`；他人主页只含 `creditTier=GOOD`、`completedTasks`，无 `score` / `records` 字段。
- 通知：`notification` 表出现 2 条 `type=CREDIT` 记录；`ck_notification_type` 已包含 `CREDIT`。
- 浏览器演示：个人中心信用分卡与记录（`credit_profile.png`、`credit_profile_records.png`）、任务详情发布者主页弹窗（`credit_public_profile.png`）。

## 十六、本周没做什么

- 没做低分限制的实际拦截与发布复核队列。
- 没做投诉/取消责任/连续履约/管理员复核记分，也没有管理端信用分查询页。
- 没做信用分事件异步重试与死信处理（见“设计取舍”）。
- 没有改动任务列表的预警档排序规则（文档中的排序约定尚未实现）。

## 十七、Git

- 分支 `dev-Rrrr-Wang`，基于 main `7f11396`；只提交 PR，**不合并 main**。
- 建议提交信息：`feat(credit): 信用分数据表、记分事件与个人中心/他人主页展示`。
