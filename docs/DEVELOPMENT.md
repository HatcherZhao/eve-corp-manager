# 本地开发指南

本文档描述从空白克隆到本地可运行的最小流程。开发环境使用独立的本地 MySQL、Redis 和随机生成的密钥；不需要导入线上数据库，也不会包含军团、角色、授权、资产、邮件或图片缓存等私有数据。

## 1. 前置条件

- JDK 17、Maven 3.9+、Python 3.10+、OpenSSL
- Node.js 22、Corepack、pnpm 9.15.9
- Docker Desktop 或 Colima（Docker Compose v2）

项目目录：后端在 `backend/`，前端在 `frontend/`，Liquibase 变更在 `backend/continew-server/src/main/resources/db/changelog/`。推荐分别用 IDE 打开 `backend/pom.xml` 与 `frontend/`。

## 2. 首次初始化

在仓库根目录执行：

```bash
python3 scripts/init_local.py
docker compose up -d --wait
```

初始化脚本会创建仅本机可读的 `.env` 和 `frontend/.env.local`，其中含 MySQL/Redis 随机密码、站内管理员初始密码及 RSA/AES/JWT 密钥。它不会覆盖已有文件。Compose 只启动 MySQL 8 与 Redis 7，端口仅绑定 `127.0.0.1`，数据保存在 `mysql-data`、`redis-data` 命名卷。

首次启动后端时，Liquibase 会自动创建框架表、EVE 表和公开基础数据。无需执行 SQL dump；`main_table.sql`、`main_data.sql` 和 EVE changeset 是干净的初始化来源。

> 不要提交 `.env`、`frontend/.env.local`，也不要把 `FIELD_RSA_PRIVATE_KEY`、`EVE refresh_token` 或真实业务数据放入前端环境变量。

## 3. 启动服务

后端终端：

```bash
cd backend
mvn -B -ntp -Pfat_jar -pl continew-server -am -Dspotless.apply.skip=true package
cd ..
python3 scripts/dev_backend.py
```

后端地址为 <http://127.0.0.1:8000>，脚本读取根目录 `.env` 并启用 `dev,local`。`fat_jar` 是必需 profile；IDE 启动时也需导入相同环境变量和 profile。

前端终端：

```bash
cd frontend
nvm use
corepack enable
pnpm install --frozen-lockfile
pnpm dev --host 127.0.0.1
```

访问 <http://127.0.0.1:5173>。Vite 会把 `/dev-api` 转发到后端。登录管理员为 `admin`，初始密码在根 `.env` 的 `BOOTSTRAP_ADMIN_PASSWORD`；已初始化的密码不会被后续启动覆盖。

端口被占用时，先用 `lsof -nP -iTCP:<端口> -sTCP:LISTEN` 确认归属。仅结束确认属于本项目的遗留进程；不要通过改端口绕开冲突。

## 4. 日常验证

```bash
# frontend/
pnpm lint
pnpm typecheck
pnpm build

# backend/
mvn -B -ntp -Pfat_jar -pl continew-server -am -Dspotless.apply.skip=true test
```

真实 MySQL/Redis 集成测试使用 `scripts/g008/01-run-integration-tests.sh`，执行前按脚本说明提供 `G008_IT_*` 变量。新增数据库结构应创建新的 EVE Liquibase changeset 并登记到 master changelog；已在任意环境执行过的 changeset 不可修改。

## 5. 重置与清理

停止中间件但保留数据：`docker compose down`。若确实要删除本地所有数据库和 Redis 数据，再执行 `docker compose down -v`，然后删除 `.env`、`frontend/.env.local` 并重新执行初始化。该操作不可恢复，不得用于共享或生产环境。

## 6. 生产镜像打包

生产 Docker 交付规则、服务器发布步骤与网关配置见 [生产部署指南](../deploy/production/README.md)。使用 `scripts/g010/01-package-production-images.sh <YYYYMMDD-N>` 生成可上传的 `linux/amd64` 镜像包和 SHA-256 校验文件；脚本不会上传、部署或删除任何镜像。
