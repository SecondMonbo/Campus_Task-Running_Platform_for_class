# 开发环境

项目按已选技术栈搭建为单体后端和独立前端开发服务器：

- Java 21、Spring Boot 4.1.1、Maven Wrapper 3.9.16。
- Vue 3、Vite、Vue Router。
- PostgreSQL 18.6、Flyway 数据库迁移。
- 后端默认端口 `8080`，前端默认端口 `5173`；Vite 将 `/api` 与 `/actuator` 代理到后端。

## 准备数据库

### 方式 A：Docker Compose

安装 Docker Desktop 后，在 `code` 目录复制 `.env.example` 为 `.env`，将 `POSTGRES_PASSWORD` 改成本地开发密码，然后启动数据库：

```powershell
Copy-Item .env.example .env
docker compose up -d
```

Compose 只将 PostgreSQL 端口绑定到本机回环地址。数据库默认名为 `campus_task_platform`，开发用户名为 `campus_dev`。

### 方式 B：本机 PostgreSQL

使用 PostgreSQL 18.6（或课程环境已安装的 PostgreSQL 18），启动 PostgreSQL 服务，并在 pgAdmin 中创建：

- 数据库：`campus_task_platform`
- 用户：`campus_dev`
- 为该用户设置本地开发密码，并授予该数据库所有权

如果 PostgreSQL 安装在 Windows 服务中，可在“服务”管理器启动相应服务。不要把真实密码提交到 Git。

## 启动后端

在 PowerShell 中运行：

```powershell
cd code\backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/campus_task_platform"
$env:DB_USERNAME = "campus_dev"
$env:DB_PASSWORD = "123"
.\mvnw.cmd spring-boot:run
```

后端健康检查地址为 `http://localhost:8080/actuator/health`。默认数据库配置采用 `ddl-auto=validate`，正式业务表应通过 `src/main/resources/db/migration` 下的 Flyway 迁移脚本建立，不要依赖 Hibernate 自动建表。

## 启动前端

另开一个 PowerShell 窗口：

```powershell
cd code\frontend
npm run dev
```

打开 Vite 输出的本地地址（通常为 `http://localhost:5173`）。首页会检查后端健康状态。

## 构建与测试

```powershell
cd code\backend
.\mvnw.cmd test
```

```powershell
cd code\frontend
npm run build
```

后端上下文测试使用 H2 内存数据库，不依赖运行中的 PostgreSQL；本地实际业务运行仍使用 PostgreSQL。

## 当前骨架边界

- 后端已配置 PostgreSQL、Flyway、Session/CSRF 安全基础和 Actuator 健康检查；业务登录、用户、任务、账务等 API 尚未实现。为避免 Spring Security 输出临时明文开发密码，默认内存用户自动配置已关闭，登录模块实现后需接入正式的用户认证服务。
- 除健康检查和 CSRF Token 获取接口外，后端请求要求已认证会话；后续实现登录时继续遵守 `development_doc/API约定.md`。
- 当前尚未创建业务表；数据结构确定后，新增 Flyway 迁移并编写对应测试。
- 除 localhost 开发外，部署环境应启用 HTTPS，并设置 `SESSION_COOKIE_SECURE=true`。
