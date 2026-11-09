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
