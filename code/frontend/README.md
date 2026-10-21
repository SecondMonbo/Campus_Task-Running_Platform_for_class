# 前端 A 第 1 周演示

本周交付只有登录/注册页和任务列表页。任务列表用本地 Mock 展示 OpenAPI 中定义的五种任务状态。

## 本地运行

需要 Node.js `20.19+` 或 `22.12+`。在 PowerShell 中进入 `code\frontend` 后运行：

```powershell
npm.cmd ci
npm.cmd run dev:demo
```

打开终端显示的 `Local` 地址，通常是 `http://localhost:5173/login`。先注册演示账号即可查看任务列表。不需要启动后端或数据库。

Mock 账号和任务保存在当前服务进程内；停止或重启服务后会重置。此模式用于页面演示，不保存真实业务数据。

## OpenAPI 字段

字段与状态定义见 `../development_doc/openapi.yaml`。本地 Mock 可通过 `npm.cmd run dev:demo` 独立运行；连接后端时运行 `npm.cmd run dev`，默认代理到 `http://localhost:8080`。
