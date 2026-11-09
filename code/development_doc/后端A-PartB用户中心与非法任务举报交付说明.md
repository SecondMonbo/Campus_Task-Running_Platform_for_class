# Part B（用户中心方向）· 交付说明

> 本周任务：用户中心（个人资料编辑 + 密码修改）、用户对非法任务的直接举报（接口复用原有逻辑）、个人主页预留评分展示接口位，并留一份 `note.md` 记录关键记忆。

## 一、任务完成情况（对照要求）

### 1. 用户中心：前后端都新增
**后端（新增 `edu.campus.taskplatform.user` 包）**
- `GET /api/v1/users/me/profile`：返回本人账号资料 `userId / username / displayName / phone / role / createdAt`，并附带评分展示字段 `rating`、`reviewCount`、`creditScore`。
  - `rating`：作为跑腿员收到的任务评价均分（`TaskReviewRepository` 新增 `averageScoreByRevieweeId` 聚合，保留一位小数）。
  - `reviewCount`：收到的评价条数（新增 `countByRevieweeId`）。
  - `creditScore`：**信誉分展示接口预留位**，契约字段（0-100，可空），机制上线前恒为 `null`。
- `PATCH /api/v1/users/me`：仅修改 `displayName`（必填 1-50）与 `phone`（选填 ≤30，`^[0-9+\-\s]*$`，空白会清空为 null）。`username`、`role` 不可改。
- `POST /api/v1/users/me/password`：校验当前密码（错误返回 400 `VALIDATION_ERROR`"当前密码不正确"）、校验新旧不能相同、新密码 8-128 位，通过后更新密码哈希。
- 三个接口一律通过 `CurrentUserService.requireCurrentUser()` 取身份，请求体不接受 `userId`，越权无从下手；`SecurityConfiguration` 无需改动（`/users/me/**` 本就需要登录）。
- 领域方法补在实体上：`AppUser.updateProfile(...)`、`AppUser.updatePasswordHash(...)`，避免服务层直接操作字段。

**前端（新增个人中心页面）**
- 新增 `views/ProfileView.vue`（路由 `/profile`）：资料卡（头像、显示名、@username、角色、手机号、注册时间）、评分卡、两个表单（个人资料 / 修改密码）。
- `api/index.js` 新增 `profile` / `updateProfile`（PATCH）/ `changePassword`。
- `domain/tasks.js` 新增 `validateProfileDraft`（显示名必填 ≤50、手机号 ≤30 且字符合法）与 `validatePasswordChange`（8-128、不能与当前密码相同、两次输入一致）。
- `App.vue` 侧边导航新增"个人中心"，顶栏头像区由 `div` 改为跳转 `/profile` 的链接；`AppIcon` 补 `user` 图标；`style.css` 补个人中心布局与响应式样式。

### 2. 非法任务举报：复用原有 complaint 逻辑，不新增接口
- **后端只改一处**：`ComplaintService.create` 中，
  - 参与者（发布者 / 跑腿员）行为保持不变：仍自动把被投诉人设为对方；指定错误对象仍报"被投诉人必须是任务另一位参与者"。
  - **非参与者现在可以举报任务内容**：允许提交、`taskId` 照常关联、`targetUserId` 留空；但若非参与者硬要指定 `targetUserId`，返回 403 `FORBIDDEN`"非任务参与者只能举报任务内容，不能指定被投诉用户"。
  - 这样"举报任务"与"投诉对方"共用 `POST /api/v1/complaints`，语义由是否指定被投诉人区分，没有开新口子、没有新表、没有新迁移。
- **前端新增独立入口**：`taskPermissions.report = Boolean(user)`（任意登录用户），`TaskActions.vue` 新增"举报任务"按钮 + 弹窗（举报类型：违规内容 / 虚假信息 / 违法违规 / 其他；举报说明 ≤2000 必填），提交时调用同一个 `api.complain`，但**不传 `targetUserId`**，成功后回显"举报编号"。

### 3. 个人主页预留评分展示接口位
- 契约口径统一：`openapi.yaml` 中 `User` schema 早已有 `creditScore`；本次新增 `UserProfile` schema，把 `rating`、`reviewCount`、`creditScore` 一并固化，`creditScore` 声明为可空。
- 页面口径：评分卡显示"信誉评分 X 分（基于 N 条任务评价统计）"，下方固定一行"信誉分（0-100）：待接入"，并注明"评分展示接口已预留；信誉分机制上线后将在此自动展示完整数据"。
- 后续接入成本：后端只改 `UserCenterService.toProfile()` 里 `null` 那一处，前端无需改动。

### 4. note.md
- 仓库根新增 `note.md`，记录：接口契约、举报复用语义、评分预留口径、三个环境坑（git 路径 / mvnw 需要系统 PowerShell / fetch 不更新 origin/main）、验证留痕、明确"没做什么"。

### 5. 契约同步
- `code/development_doc/openapi.yaml` 新增 3 个 path（`/users/me/profile`、`/users/me`、`/users/me/password`）与 4 个 schema（`UserProfile`、`UserProfileResponse`、`UpdateProfileRequest`、`ChangePasswordRequest`）。

## 二、验证证据

### 1. 自动化测试
- 后端 `mvnw test`：**Tests run: 134, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS**（约 42s）。
- 新增 `UserCenterIntegrationTest`（7 个用例，全部通过）：
  - 未登录访问资料接口 → 401；
  - 资料返回本人信息，无评价时 `rating` / `creditScore` 为空、`reviewCount=0`；
  - 编辑资料持久化（含首尾空白裁剪、手机号清空为 null）；
  - 空白显示名与非法手机号 → 400；
  - 修改密码校验当前密码并写入新哈希；
  - 路人带 `taskId` 举报任务（不带 target）→ 成功、`targetUserId` 为空、`status=PENDING`；路人带 `targetUserId` → 403；
  - 参与者投诉仍自动指向对方。
- 前端 `npm run build`：构建成功（vite），产物包含 `ProfileView-*.js`。

### 2. HTTP 端到端（真实 Postgres + 8080）
脚本 `partB_usercnt_e2e.ps1`，输出 `partB_usercnt_e2e_out.txt`，实测结果：

```
GET profile ok=True status=200  rating=(空) reviewCount=0 creditScore=(空)
PATCH profile ok=True displayName=发布者演示 phone=13800001111   （GET 复核一致）
PATCH blank displayName status=400 code=VALIDATION_ERROR
change pwd wrongCurrent status=400 code=VALIDATION_ERROR
change pwd sameAsOld status=400
change pwd ok=True status=200
login with old password status=401 / login with new password status=200
restore password ok=True / login with restored password status=200
report task as non-participant ok=True complaintId=… targetUserId=[] status=PENDING
report with targetUserId as non-participant status=403 code=FORBIDDEN
participant complaint targetUserId=3（= runner_demo）
admin pending complaints total=2（此时 E2E 累计 2 条）
```

数据库复核（`complaints` 表）：

```
 reporter_id | target_user_id | category | status  | has_task
-------------+----------------+----------+---------+----------
           3 |                | 违规内容 | PENDING | t     ← 前端"举报任务"落库，未指定被投诉人
           2 |              3 | 对方违约 | PENDING | t     ← 参与者投诉，自动指向跑腿员
           1 |                | 违规内容 | PENDING | t     ← 非参与者举报
```

### 3. 浏览器演示（真实登录 + 页面操作）
- `partB_profile.png`：个人中心页面，展示资料卡、评分卡（"信誉评分 — 分 / 基于 0 条任务评价统计 / 信誉分（0-100）待接入 / 接口已预留"）、资料表单与密码表单。
- `partB_profile_toast.png`：修改显示名与手机号后保存，顶栏头像与资料卡同步更新，页面提示"个人资料已更新。"。
- `partB_report_submitted.png`：跑腿员打开他人发布的任务详情，点"举报任务" → 选"违规内容" + 填写说明 → 提交，弹窗关闭并在页面回显"举报编号：93182c2d-0e73-45ba-a8ca-cbf96a936985。请保留编号以便查询和沟通。"

## 三、GitHub
- 分支：`dev-Rrrr-Wang`（基于最新 `main`，已先 `--ff-only` 快进到远端 main，提交为 fast-forward，无需 force push）。
- 只提交 PR，**不合并 main**，由组长 Review。

## 四、演示账号
- 发布者：`publisher_demo / User123456`（userId=2）
- 跑腿员：`runner_demo / User123456`（3）
- 管理员：`admin_demo / Admin12345`（1，ADMIN）

## 五、本地复现步骤
1. 启动 PostgreSQL（服务 `postgresql-x64-18`，5432）。
2. 后端：
   ```powershell
   $env:DB_URL="jdbc:postgresql://localhost:5432/campus_task_platform"
   $env:DB_USERNAME="campus_dev"; $env:DB_PASSWORD="campusdev"
   cd code\backend; .\mvnw.cmd spring-boot:run     # 8080，Flyway 自动迁移
   ```
3. 前端：`cd code\frontend; npm run dev`（5173）。
4. 演示路径：
   - 登录 `publisher_demo` → 点顶栏头像或导航"个人中心" → 改显示名/手机号 → 保存 → 提示"个人资料已更新"，顶栏同步。
   - 同页"修改密码"：可先试错误当前密码（提示校验失败），再用正确当前密码改密；改密后旧密码登录失败、新密码可登录。
   - 用 `runner_demo` 登录 → 任务大厅打开他人发布的任务 → 点"举报任务" → 选类型、写说明 → 提交 → 回显举报编号。
   - 用 `admin_demo` 登录 → 管理台"投诉/举报"待处理列表可看到上述举报（`target_user_id` 为空的是任务内容举报）。

## 六、风险与未开放项
- `creditScore` 目前恒为 `null`，属于"接口已预留、机制未实现"，页面已明确标注"待接入"，不构成虚假数据。
- 举报目前没有"我的举报记录"页面，也无举报频率限制/去重；恶意举报的约束仅停留在文案提示，后续若要做信誉分扣减需在管理员处理动作里挂钩。
- 非参与者举报被限制为"只能举报任务内容"，无法在任务详情里直接拉黑某个用户；这是复用原接口的刻意取舍，避免绕过"被投诉人必须是参与者"的既有风控。
- 任务详情页"举报任务"按钮对任意登录用户可见，包括任务发布者本人（发布者举报自己任务在语义上有点怪，但接口允许且不会指定被投诉人，暂未加限制）。
- 本次未新增 Flyway 迁移：`complaints` 表与迁移 `V2026110501__create_complaints.sql` 由队友在 main 提供，本功能完全复用。
- 本地演示产生了少量演示数据（任务 `PartB 举报测试任务` / `PartB 投诉回归任务`，以及 3 条 PENDING 举报记录），不影响代码交付，交付后可按需清理。
