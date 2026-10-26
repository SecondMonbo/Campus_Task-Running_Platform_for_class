# 校园跑腿平台前端

Vue 3 + Vite + Vue Router。基于最新 main 复用前端 A 的登录/注册、退出登录、任务大厅和统一路由，整合前端 B 的任务执行、钱包、通知、取消与申诉、管理端页面。发布/修改/撤回页面仍由前端 A 负责；个人中心、评价、信用分展示和收益统计未纳入本次开发。

## 运行

需要 Node.js `^20.19.0 || >=22.12.0`。

```bash
npm ci
npm run dev:demo
```

`dev:demo` 直接使用前端 A 的 `vite.demo.js`。进入 `/register` 创建演示账号，然后可登录、退出和浏览 `/tasks`。账号、Session Cookie 和任务示例由当前进程维护，重启会重置。该服务只实现 A 的认证和任务列表，B 的钱包、通知、执行操作等接口仍需真实后端、Apifox 或浏览器固定场景。

连接 Apifox 云端 Mock：

```bash
npm run dev:apifox
```

此模式通过 `.env.apifox` 和 Vite 代理访问 `/api/v1`，不保存 Apifox 登录凭证，不向云端转发本机 Session Cookie / Authorization，也不接受云端 Cookie 覆盖本机会话。[Apifox 项目](https://app.apifox.com/project/8940624)当前使用随机响应；它可验证路径、字段和代理联通，不能保证身份、筛选、统计与跨请求状态一致。认证与状态流验证使用 A 的本地服务和独立浏览器场景，不会在接口失败时回退为假数据。

连接本机 Spring Boot：

```bash
npm run dev
```

默认 `/api`、`/actuator` 代理到 `http://localhost:8080`。参考 `.env.example` 配置被 Git 忽略的 `.env.development.local`；保留 `.env.apifox` 的专用目标。改环境变量后重启 Vite。

生产构建默认使用同源 `/api/v1`，部署时反向代理 API，并为前端路由配置 SPA fallback。仅在确需跨域部署、且服务端已配置带凭据 CORS 时设置 `VITE_API_BASE_URL`。真实认证接口还需组长后端接入；本次整合没有改动后端认证或业务契约。

## 共用路由

| 路由                  | 内容                                                                 |
| --------------------- | -------------------------------------------------------------------- |
| `/login`、`/register` | 复用 A 的 `AuthView.vue`，登录/注册后返回站内原目标                  |
| `/tasks`              | 复用 A 的任务大厅、搜索、筛选和分页，卡片详情进入 B 的执行页         |
| `/my-tasks`           | 我接取的 / 我发布的，状态筛选、0 起始分页、10/20/50 条切换、任务统计 |
| `/my-tasks/:taskId`   | 任务详情，按身份和状态接单/送达/验收，申请取消、申诉及投诉           |
| `/wallet`             | 可用/冻结余额，本人流水、类型筛选、关联任务与申诉编号                |
| `/notifications`      | 全部/未读通知、单条已读、关联任务跳转                                |
| `/disputes`           | 按申请编号应答取消，可用 `?requestId=...` 预填                       |
| `/admin`              | 取消/申诉裁决、投诉处理、用户/任务/流水/日志查询，仅管理员可访问     |

根路径进入 A 的 `/tasks`。所有业务路由共用 A 的认证守卫。我的任务中的接单入口进入任务大厅；详情沿用 `/my-tasks/:taskId`，兼容已有 B 链接。

## 与前端 A 的认证约定

- 登录请求为 `{ username, password }`；注册为 `{ username, password, displayName, phone? }`，不提交角色。业务成功响应统一按 `{ code, message, data }` 解包。用户字段为 `userId`、`username`、`displayName`、`role`，成功后调用 A 的 `setSession(user)`。
- `loadSession()` 共用一次正在进行的 `GET /api/v1/auth/me`；401 返回 `null`，其余故障继续作为错误处理。B 不再另设认证加载门禁，因此匿名用户可正常显示 A 的登录/注册页。
- 未登录时跳转 `/login?redirect=...`。登录/注册切换保留目标，认证成功后返回；外站地址与认证循环退回 `/tasks`。普通用户访问管理路由时回到我的任务。
- API 始终使用 `credentials: 'include'`。CSRF 接口直接返回 `{ headerName, parameterName, token }`，写操作携带 `X-XSRF-TOKEN`；注册、登录、退出成功后清空旧 CSRF 缓存，后续写操作重新获取。
- 业务接口返回 401（包括空正文）时复用 A 的 `campus:auth-expired` 事件，清空会话并带返回路径跳转登录。错误凭据与匿名 `/auth/me` 探测不误触发该事件。
- 旧会话请求的延迟返回不能覆盖或注销新登录用户；退出及账号切换销毁旧用户的业务页面与查询。身份仅依赖服务端 Session，不使用 JWT 或浏览器持久化身份。

## 共用接入点

- `src/composables/state.js` 保留 A 的 `session`、`setSession`、`clearSession`、`loadSession` 导出，B 的组件直接复用。会话并发逻辑在 `src/composables/session.js` 中测试。
- `src/router.js` 统一注册 A 路由与导出的 `frontendBRoutes`，安全返回路径由 `src/domain/auth.js` 共用。
- `src/components/TaskActions.vue` 接收契约 `Task`；`compact` 模式仅显示接单/送达/验收操作，`refresh` 事件用于重新读任务。
- `src/api/client.js` 与 `src/api/index.js` 统一处理认证与业务请求。ID 按字符串传递；不提交操作者 `userId` / `role` 或任意目标状态。服务端仍须校验所有权限、并发和账务规则。

## 尚需补齐的公共契约

本次没有自行添加 OpenAPI 路径或字段。以下为已有 B 范围中的接口缺口：

1. **本人取消申请/申诉的列表与详情**：当前仅有创建、应答及管理员查询。保留提交编号和按编号应答；未伪造个人历史查询。通知模型只有 `relatedTaskId`，尚不能自动定位取消申请。
2. **任务上的活动取消争议**：`Task` 未返回活动取消申请或冻结标记；重新打开页面时，前端无法精确禁用争议中的送达/验收，服务端必须重新校验。
3. **异常任务处理**：无异常队列、强制关闭/转派等业务接口，未添加通用改状态入口或模拟成功。
4. **管理端任务范围**：复用 `GET /tasks` 的状态/关键字参数；全量可见范围、发布者及时间查询仍须后端明确。
5. **投诉扩展**：图片上传、功能限制期限、第二管理员复核缺少完整契约；扣分依据记录在既有 `decisionNote` 中，未新增字段。
6. **时间筛选与全部已读**：任务历史日期、钱包/管理查询日期、通知全部已读等尚无完整参数/接口，不用单页数据伪造全局结果。
7. **详情补充信息**：参与者联系方式、完整状态日志、送达/完成时间需要后端进一步对齐。

## 验证

```bash
npm test
npm run build
```

Node 测试覆盖既有业务规则、认证响应与 Cookie 请求约定、CSRF 轮换、会话单请求复用、401 的 `null` 约定、登录/退出竞态和安全返回路径。

启动 `dev:demo` 后可用 Playwright CLI 执行以下场景。`browser-smoke.js` 拦截该浏览器的 `/api/v1` 验证 B 的固定成功/失败场景；`auth-browser-smoke.js` 使用 A 的真实本地认证接口与 Cookie，仅对 B 的业务接口提供固定响应。运行在测试浏览器中，不访问真实用户账务。

```bash
npx --yes --package @playwright/cli playwright-cli -s=frontend-b open http://127.0.0.1:5173/my-tasks
npx --yes --package @playwright/cli playwright-cli -s=frontend-b run-code --filename tests/browser-smoke.js
npx --yes --package @playwright/cli playwright-cli -s=frontend-b run-code --filename tests/auth-browser-smoke.js
```

可用 `--config` 指定已安装的浏览器。截图位于被 Git 忽略的 `output/playwright/`；最终仍须接真实后端验证 Session、事务、并发、幂等和权限。
