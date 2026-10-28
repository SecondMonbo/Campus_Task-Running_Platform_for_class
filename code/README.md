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
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

编辑仓库 `code` 子目录中的 `.env`，为 `POSTGRES_PASSWORD` 设置本机开发密码，然后启动数据库：

```powershell
docker compose up -d
```

Compose 会创建 `campus_task_platform` 数据库和 `campus_dev` 用户，并仅将数据库端口绑定到本机。不要把 `.env` 或真实密码提交到 Git。

如果使用本机 PostgreSQL，请先启动 PostgreSQL 服务。用 pgAdmin 连接本机服务器并打开 Query Tool，以数据库管理员（通常是安装时设置的 `postgres` 用户）执行以下 SQL。请将密码占位符替换为自己本机的开发密码。以下两条 SQL **分开执行**，先创建登录角色：

```sql
CREATE ROLE campus_dev LOGIN PASSWORD '替换为本机开发密码';
```

再单独创建数据库：

```sql
CREATE DATABASE campus_task_platform OWNER campus_dev;
```

如果角色或数据库已经存在，不要重复创建；可在 pgAdmin 的 **Login/Group Roles** 和 **Databases** 节点检查。应用连接时 `DB_PASSWORD` 必须与 `campus_dev` 的密码一致。不要把密码写入 SQL 文件、项目配置或提交到 Git。

> Docker Compose 方式会自动创建同名数据库和用户；不要再对同一个 Compose 数据库执行上面的创建 SQL。

## 2. 启动后端

在仓库根目录打开 PowerShell 窗口 A：

```powershell
Set-Location code\backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/campus_task_platform"
$env:DB_USERNAME = "campus_dev"
$env:DB_PASSWORD = "替换为本机 campus_dev 密码"
.\mvnw.cmd spring-boot:run
```

请将占位文字替换为本机数据库密码；不要把密码写入项目文件或提交到 Git。等待控制台显示 Spring Boot 启动成功；首次启动时，Flyway 会自动创建表，并输出迁移日志。可在另一个 PowerShell 窗口检查：

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

## 5. 本地管理员测试账号

每位组员只在自己的本地课程数据库中准备管理员账号。先通过注册页面创建一个专用账号（例如 `local_admin`），确认注册成功；注册服务会生成 BCrypt 密码，并同时创建钱包和初始流水。**不要直接插入管理员用户行，也不要把密码哈希手工写进数据库。**

然后用 pgAdmin 连接自己的 `campus_task_platform` 数据库，打开 Query Tool，先确认将要提升的账号确实只有一条：

```sql
SELECT user_id, username, role
FROM app_user
WHERE username = '替换为专用测试管理员用户名';
```

确认返回的是自己刚注册的专用测试账号后，按以下顺序分步操作。先单独执行：

```sql
BEGIN;
```

然后执行更新：

```sql
UPDATE app_user
SET role = 'ADMIN'
WHERE username = '替换为专用测试管理员用户名'
RETURNING user_id, username, role;
```

确认返回一行且角色为 `ADMIN` 后，单独执行：

```sql
COMMIT;
```

如果没有更新到账号或结果不符合预期，在仍处于事务时执行 `ROLLBACK;`，再检查用户名和所连接的数据库。修改成功后，退出该账号并重新登录，让新的角色加载到会话中，再打开 `http://localhost:5173/admin` 验证管理员页面。

这只是本地演示的初始化办法，不要在共享或生产数据库中随意授予管理员权限。普通用户注册仍只能获得 `USER` 角色。

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
