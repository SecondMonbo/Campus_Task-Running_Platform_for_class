# 开发环境

项目采用 Java 21、Spring Boot 4.1.1、Maven Wrapper、Vue 3、Vite、PostgreSQL 18.6 和 Flyway。后端默认端口为 `8080`，前端为 `5173`；Vite 会把 `/api` 和 `/actuator` 请求代理到后端。

以下命令使用 Windows PowerShell，并假设当前目录是仓库根目录。

## 前置条件

- Java 21
- Node.js `^20.19.0` 或 `>=22.12.0`、npm
- Docker Desktop（推荐）或本机 PostgreSQL 18

## 1. 启动 PostgreSQL

推荐使用项目提供的 Docker Compose。在仓库根目录运行：

```powershell
Set-Location code
Copy-Item .env.example .env
```

编辑 `code\.env`，为 `POSTGRES_PASSWORD` 设置本机开发密码，然后启动数据库：

```powershell
docker compose up -d
```

Compose 会创建 `campus_task_platform` 数据库和 `campus_dev` 用户，并仅将数据库端口绑定到本机。不要把 `.env` 或真实密码提交到 Git。

如果使用本机 PostgreSQL，请先启动 PostgreSQL 服务，并在 pgAdmin 中创建 `campus_task_platform` 数据库及 `campus_dev` 用户，授予该用户数据库所有权。后续 `DB_PASSWORD` 必须填写该用户的密码。

## 2. 启动后端

在仓库根目录打开 PowerShell 窗口 A：

```powershell
Set-Location code\backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/campus_task_platform"
$env:DB_USERNAME = "campus_dev"
$env:DB_PASSWORD = "替换为本机 campus_dev 密码"
.\mvnw.cmd spring-boot:run
```

请将占位文字替换为本机数据库密码；不要把密码写入项目文件或提交到 Git。首次启动时，Flyway 会自动创建表。看到应用启动成功后，可在另一个 PowerShell 窗口检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

预期状态为 `UP`。

## 3. 启动前端并注册测试账号

打开 PowerShell 窗口 B，在仓库根目录运行：

```powershell
Set-Location code\frontend
npm ci
npm run dev
```

访问 Vite 输出的地址（通常为 `http://localhost:5173`），选择注册并创建测试账号。注册成功会自动登录，并在数据库事务中创建用户、钱包和 100 虚拟币的初始入账流水。

## 4. 确认注册数据已写入 PostgreSQL

在 pgAdmin 的 `campus_task_platform` 数据库查询工具中运行以下只读 SQL，将用户名替换为刚注册的账号：

```sql
SELECT u.user_id, u.username, u.display_name, u.role,
       w.available_balance, w.frozen_balance,
       t.type AS initial_transaction_type,
       t.amount AS initial_transaction_amount
FROM app_user u
JOIN wallet_account w ON w.user_id = u.user_id
LEFT JOIN wallet_transaction t
       ON t.user_id = u.user_id AND t.type = 'INCOME'
WHERE u.username = '替换为注册用户名'
ORDER BY t.created_at;
```

预期能查到用户记录、钱包余额 `100`，以及金额为 `100` 的初始 `INCOME` 流水。密码只保存为 BCrypt 哈希，不要复制或分享 `password_hash`。

## 本地管理员测试账号

先通过注册页面创建一个专用测试账号，再由本地数据库管理员在 pgAdmin 中将**该账号**提升为管理员；不要直接插入用户记录，也不要提供公开注册时选择管理员角色的能力。用户表密码必须由应用以 BCrypt 方式生成，注册流程还会同时创建钱包及初始流水。

在 pgAdmin 查询工具中执行下面的 SQL，并替换用户名：

```sql
UPDATE app_user
SET role = 'ADMIN'
WHERE username = '替换为专用测试管理员用户名'
RETURNING user_id, username, role;
```

确认只更新一行，然后退出该账号并重新登录，使新的角色加载到会话中。此方法仅用于受控的本地课程演示数据库；不要在共享或生产数据库中随意授予管理员权限。普通用户注册仍只能获得 `USER` 角色。

## 构建与测试

```powershell
Set-Location code\backend
.\mvnw.cmd test
```

```powershell
Set-Location code\frontend
npm test
npm run build
```

后端自动化测试使用 H2；它们不替代本地 PostgreSQL 数据库迁移和浏览器注册登录验证。数据库表结构由 `code\backend\src\main\resources\db\migration` 下的 Flyway 脚本管理，Hibernate 使用 `ddl-auto=validate`。
