# Docker 部署指南

本目录提供面向 GitHub 使用者的生产部署模板。它运行 API 与 Web 两个容器，要求部署者自行提供 MySQL 8、Redis 7、HTTPS 证书和反向代理。默认 Web 仅监听 `127.0.0.1:18080`，适合由同机 Nginx/Caddy 终止 HTTPS；如果网关在另一台主机，改为受限的私网地址，切勿直接暴露到公网。

## 1. 准备配置

在 Linux x86_64 Docker 主机创建发布目录，例如 `/srv/eve-corp-manager`。把 `compose.yaml`、`.env.example`、`scripts/` 放入该目录，再生成仅服务器可读的 `.env`：

```bash
cd /srv/eve-corp-manager
cp .env.example .env
./scripts/01-prepare-image-cache-directory.sh
docker compose config
```

编辑 `.env`：填写 `APP_URL`、MySQL/Redis 连接信息、独立的业务库账号、JWT/字段密钥和管理员初始密码。`REDIS_DB=14` 是项目默认逻辑库，可按自己的 Redis 规划调整。`EVE_IMAGE_CACHE_HOST_DIR` 是游戏头像与物品图标缓存目录，应使用持久化磁盘并保留读写权限。

`.env`、数据库数据、授权令牌和 `data/eve-images` 都不应提交到 Git 或打入镜像。首次 API 启动会通过 Liquibase 创建结构和公开基础数据；不要从其他环境导入用户、军团或授权数据。

## 2. 构建离线镜像包

在仓库根目录执行：

```bash
scripts/g010/01-package-production-images.sh 20260917-3
```

该脚本构建后端 fat JAR、前端静态文件及 `linux/amd64` 的 API/Web 镜像，在 `output/docker/` 生成镜像包与 SHA-256 校验文件。该目录已被 Git 忽略，适合通过受控文件传输交付：

```text
eve-corp-manager-20260917-3.tar.gz
eve-corp-manager-20260917-3.tar.gz.sha256
```

若 JAR 与前端 `dist` 已通过本地验证，可加 `--skip-build` 仅执行镜像打包。M4 等 ARM 主机若无法获取 Nginx amd64 基础镜像，可设置 `WEB_BASE_IMAGE=eve-corp-manager-web:已验证标签` 使用本机已有的同架构 Web 镜像作基底。

## 3. 导入与启动

把压缩包与校验文件上传到发布目录，校验、导入，并将 `.env` 的 `IMAGE_TAG` 改为相同版本：

```bash
cd /srv/eve-corp-manager
sha256sum -c eve-corp-manager-20260917-3.tar.gz.sha256
gzip -dc eve-corp-manager-20260917-3.tar.gz | docker load

# 确认 .env 内 IMAGE_TAG=20260917-3
docker compose config
docker compose up -d --no-build
docker compose ps
docker compose logs --tail 200 api web
curl -fsS http://127.0.0.1:18080/ >/dev/null
```

确认两个容器均为 `running`、API 已完成 Liquibase、站点可访问后，再删除上传包。清理旧镜像前先确认没有容器使用它；不要对宿主机执行广泛的 Docker 清理命令。

## 4. HTTPS 反向代理

[gateway/nginx.conf.example](gateway/nginx.conf.example) 是同机 Nginx 的最小示例。替换域名、证书路径和上游地址后执行 `nginx -t && systemctl reload nginx`。反向代理必须保留同源的 `/api/` 与 `/websocket` 请求，避免破坏登录、WebSocket 和 EVE 授权回调。

## 5. 常用排查

- `docker compose config`：检查缺失或拼写错误的环境变量。
- `docker compose logs -f api`：检查 MySQL、Redis、Liquibase 与授权续期。
- `docker compose logs -f web`：检查 Web 服务器与反代请求。
- 镜像打包失败：确认已安装 Docker Buildx、JDK 17、Node.js 22，并检查 `backend/continew-server/target/continew-admin.jar` 与 `frontend/dist/index.html` 是否存在。
