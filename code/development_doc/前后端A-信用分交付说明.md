# 前后端 A 部分 · 信用分功能交付说明

> 分支：`dev-Rrrr-Wang`（基于 main `7f11396 docs: 更新信用分评分规则`）
> 日期：2026-11-18
> 交付方式：**仅提交 PR，不合并 main**

## 一、交付范围

本周按 `code/development_doc/API约定.md` §4.4 与 `openapi.yaml` 的信用分约定，完成：

- 后端：信用分数据表与 Flyway 迁移、初始 80 分、变更记录、记分服务；接入评价、按时完成、超时送达三类任务事件，并按事件来源保证幂等。
- 前端：个人中心展示信用分、档位与变更记录；任务接取/详情界面可查看他人主页（仅基本信息 + 信用档位，不展示精确分值与明细）。
- 文档：`openapi.yaml` 补齐 2 个接口与 7 个 schema；`API约定.md` 更新实现状态；仓库根 `note.md` 追加本周关键记忆。

## 二、接口

| 方法 | 路径 | 权限 | 返回 |
| --- | --- | --- | --- |
| GET | `/api/v1/users/me/credit?page=0&size=10` | 登录本人 | `score`、`tier`、`tierLabel`、`permissionHint`、`recoveryHint`、`records{items,total,page,size}` |
| GET | `/api/v1/users/{userId}/profile` | 登录用户 | `displayName`、`role`、`creditTier`、`creditTierLabel`、`completedTasks`、`memberSince` |
| GET | `/api/v1/users/me/profile` | 登录本人 | 原有资料，`creditScore` 由 `null` 改为真实分值 |

校验：`size` 只接受 10/20/50，`page>=0`，非法值 400 `VALIDATION_ERROR`；未登录 401；未知用户 404；非数字 userId 400。

## 三、数据库与迁移

| 迁移 | 内容 |
| --- | --- |
| `V2026111801__create_credit_scores.sql` | 建 `credit_score`、`credit_score_record`、唯一约束与索引；为存量用户写入 80 分及 `INITIALIZATION` 基线记录 |
| `V2026111802__add_credit_notification_type.sql` | 重建 `ck_notification_type`，允许 `TASK/SYSTEM/CREDIT` |

- `credit_score(user_id PK, score 0–100, updated_at)`。
- `credit_score_record(record_id, user_id, event_type, source_id, reason, requested_delta, actual_delta, score_before, score_after, admin_id, created_at)`。
- `UNIQUE(event_type, source_id)`：同一评价 / 同一任务事件只能记一次，数据库层兜底幂等。

## 四、记分规则落地

| 事件 | 变动 | 触发位置 | 幂等键 |
| --- | ---: | --- | --- |
| 初始化 | 基线 80 | 迁移 / 注册 / lazy ensure | `user:{id}` |
| 5 星评价 | +2 | `TaskService.createReview` | `reviewId` |
| 3–4 星评价 | 0（留痕） | 同上 | `reviewId` |
| 1–2 星评价 | 首次 30 天内 -3，其后 0 | 同上 | `reviewId` |
| 按时完成 | +1 | `TaskService.completeTask` | `taskId` |
| 超时送达（截止后 >24h） | -5 | `TaskService.deliverTask`，`completeTask` 兜底 | `taskId` |

- 分值按 `0–100` 截断，`actualDelta` 记录实际变化；实际变动为 0 时仍留痕但不发通知。
- 非零变动写 `CREDIT` 通知（原因 + `before → after`），通知失败不影响记分。
- 档位：`90–100` 优秀、`75–89` 良好、`60–74` 预警、`0–59` 受限，边界与文档一致。

## 五、前端

- `ProfileView.vue`：信用分卡（分数、档位徽标、权限说明、恢复说明、评价均分）+ 信用分变更记录分页面板。
- `TaskDetailView.vue`：新增“查看发布者主页”弹窗，只展示昵称、身份、信用档位、累计完成、注册时间，并提示不展示精确分值。
- 新增 `domain/credit.js`（档位中文名、delta 格式化）、`api.myCredit` / `api.publicProfile`、`CREDIT` 通知标签与样式。

## 六、验证证据

- 后端：`mvnw.cmd test` → **150 tests, 0 failures, 0 errors, BUILD SUCCESS**（新增 `CreditScoreIntegrationTest` 8 例）。
- 前端：`npm test` → **41 pass, 0 fail**；`npm run build` 成功。
- Flyway（PostgreSQL 18.6）：`Successfully applied 3 migrations`，schema 至 `v2026111802`。
- HTTP E2E（`credit_e2e.ps1` / `credit_e2e_out.txt`）：
  - 跑腿员初始 `80 / GOOD`；
  - 发布 → 接单 → 送达 → 完成 → 5 星评价后 `83`；
  - 记录：`INITIALIZATION 0→80`、`ON_TIME_COMPLETION +1 80→81`、`REVIEW_5_STAR +2 81→83`；
  - `/users/me/profile.creditScore=83`；
  - 他人主页 `tier=GOOD`，无 `score` / `records` 字段；
  - 匿名 401、`size=30` 400、未知用户 404。
- 数据库核对：`credit_score` 为 `(1,80)(2,80)(3,83)`；2 条 `CREDIT` 通知；约束含 `CREDIT`。
- 浏览器：个人中心信用分与记录、发布者主页弹窗（`credit_profile.png`、`credit_profile_records.png`、`credit_public_profile.png`）。

## 七、实现边界（未做，避免误读）

- `<60` 禁止接普通任务、发布进复核队列：只返回档位与提示文案，未接入任务流程拦截。
- 投诉成立扣分 / 管理员复核调整 / 取消责任方扣分 / 连续 10 单恢复：未接入。
- 信用分事件异步重试与死信队列：未引入，分值与记录同事务写入。
- 任务列表预警档排序规则：未实现。

## 八、Review 建议入口

1. `code/backend/src/main/java/edu/campus/taskplatform/credit/`（新增包）。
2. `TaskService` 的 `createReview` / `deliverTask` / `completeTask` 三处调用。
3. 两个 Flyway 迁移。
4. `ProfileView.vue` 与 `TaskDetailView.vue` 的展示改动。
