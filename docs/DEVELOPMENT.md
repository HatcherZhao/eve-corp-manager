# 本地开发

## 环境

JDK 17、Maven 3.9.11、Node.js 22、pnpm 9.15.9、Python 3.10+、OpenSSL、Docker Compose v2。Windows 可在 WSL2 中执行下述命令；macOS/Linux 直接在终端执行。IDE 导入 backend/pom.xml，前端打开 frontend/。

## 首次初始化（项目根目录）

```bash
python3 scripts/init_local.py
docker compose up -d --wait
```

生成 .env（数据库/Redis/JWT/加密密钥）和 frontend/.env.local（仅 RSA 公钥）。二者都不入库。初始化脚本遇到任一已有文件会停止，不覆盖密钥。数据库保存在命名卷；不要在保留数据库时随意重生成密钥或密码。

Compose 只启动 MySQL 和 Redis，端口只绑定 127.0.0.1。首次后端启动由 Liquibase 自动建表、填充框架初始化数据及 EVE 菜单，不需要手工导入 SQL。若项目端口被占用，先用 `lsof -nP -iTCP:<端口> -sTCP:LISTEN` 识别进程；确认是本项目遗留的开发或测试进程后结束该进程，再按项目原端口启动。不要通过修改 `.env`、Vite 或启动脚本端口来绕过占用；若占用者不是本项目进程且停止可能影响其他服务，先处理冲突归属再继续。

## 启动后端

```bash
cd backend
mvn -B -ntp -Pfat_jar -pl continew-server -am -Dspotless.skip=true package
cd ..
python3 scripts/dev_backend.py
```

地址 http://localhost:8000 。脚本读取根目录 .env，启用 dev,local，并运行 continew-server/target/continew-admin.jar。IDE 启动也需导入同一组环境变量并启用 dev,local；Spring 不会自动读取根 .env。该 local profile 仅用于本地开发。

构建必须使用 -Pfat_jar；上游默认是拆分依赖的 slim_jar。首次下载依赖较多，上游配置华为/阿里 Maven 镜像；如网络不能访问，在自己的 Maven settings.xml 配置可访问的镜像。不要把代理密码提交到仓库。

## 启动前端（另一个终端）

```bash
cd frontend
corepack enable
corepack prepare pnpm@9.15.9 --activate
pnpm install --frozen-lockfile
pnpm dev --host 127.0.0.1
```

打开 http://localhost:5173 。没有 Corepack 时可用 npm install -g pnpm@9.15.9。前端 /dev-api 请求由 Vite 转发到 localhost:8000。

登录页仅提供本站账号密码登录，以及“通过 EVE 注册”和“使用 EVE 找回密码”入口；不提供手机号、邮箱或社交账号登录。本地管理员为 admin，初始密码由初始化脚本随机生成，保存在根 .env 的 BOOTSTRAP_ADMIN_PASSWORD 中；请自行在本地查看，不要粘贴到聊天或仓库。首次后端启动仅替换未初始化管理员的密码，不覆盖已有密码；上游其他演示账号已禁用。这是本站管理账号，不是网易账号。该骨架尚未提供生产部署配置闭环，不能将开发服务直接开放到公网。

登录后选择「EVE 军团 → 军团工作台」。页面展示当前绑定角色、军团、授权健康状态、最近权限检查时间和五类能力的可用性原因；普通角色仍需具备对应站内菜单和权限。由于网易没有公开独立应用注册入口，本地环境使用国服 Swagger UI 公共客户端：用户在网易页面授权后，将最终停留的完整回调地址粘贴回本站。系统不会生成模拟授权成功或游戏业务数据。

## 开发与构建

```bash
# 在 frontend/ 内
pnpm typecheck
pnpm build
```

前端生产配置使用同源 /api；deploy/nginx.conf 是反向代理示例，backend 服务名需在自己的部署环境解析。WebSocket 根据页面协议和域名生成 ws/wss 地址；代理不记录携带本站 token 的 websocket URL。构建时需提供与后端配套的 VITE_RSA_PUBLIC_KEY 才能实际使用账号密码登录（本地初始化会生成）。

EVE 授权接口已禁止记录请求/响应正文及完整回调网址，游戏令牌不会作为本站会话凭据。不要把 FIELD_RSA_PRIVATE_KEY 或 EVE refresh_token 放入 VITE_ 环境变量。

## 代码入口

| 目录 | 用途 |
|---|---|
| backend/continew-server | 启动类、Controller、运行配置、Liquibase |
| backend/continew-eve | EVE 业务服务与模型 |
| backend/continew-system / continew-common | 上游系统功能、公共能力 |
| backend/continew-plugin | 保留上游插件；独立调度服务未纳入骨架 |
| frontend/src/views/eve | EVE 页面 |
| frontend/src/apis/eve | EVE 后端接口类型与请求 |
| docs/design/login-flow.md | 国服授权交互设计 |

新增 SQL 写在 db/changelog/mysql/eve/，并追加到 master changelog；本次导入的初始化 SQL 已移除演示密码；从本项目首次运行起，不要改已执行的 changeset。EVE 相关代码使用字符串传递外部 int64 ID，不把成员追踪数据当实时在线记录。

## 验证范围

构建结果和环境限制见 [VALIDATION.md](VALIDATION.md)。GitHub Actions 执行编译/类型检查/打包，不等于数据库、登录或 ESI 的运行联调。

## 可选运行集成测试

MySQL/Redis 启动、环境变量导入后，在 backend/ 执行 `mvn -Pfat_jar,integration-tests -pl continew-server -am -Dspotless.skip=true verify`。该命令会真正连接本地数据库；普通 package 仅运行不依赖数据库的单元测试。
