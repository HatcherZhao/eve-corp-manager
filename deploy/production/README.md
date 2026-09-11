# 生产部署

本目录部署到 vm-app 的 `/home/eve-corp-manager`。Compose 仅运行本项目 API 与 Web 容器；MySQL 和 Redis 复用 vm-common，分别为 `eve_corp_manager` 与 Redis `db14`。

## 发布镜像

在本机先构建当前版本的后端 JAR 和前端静态文件，再以 `linux/amd64` 构建镜像。vm-app 的镜像加速器可能无法拉取基础镜像，因此生产机只加载已构建镜像，不在服务器上构建。

```bash
cd backend
mvn -B -ntp -Pfat_jar -pl continew-server -am -Dspotless.apply.skip=true package
cd ../frontend
nvm use && corepack enable && pnpm install --frozen-lockfile && pnpm build
cd ..
docker buildx build --platform linux/amd64 --load -f deploy/production/Dockerfile.api -t eve-corp-manager-api:20260911-1 .
docker buildx build --platform linux/amd64 --load -f deploy/production/Dockerfile.web -t eve-corp-manager-web:20260911-1 .
docker save eve-corp-manager-api:20260911-1 eve-corp-manager-web:20260911-1 | gzip > /tmp/eve-corp-manager-20260911-1.tar.gz
```

## vm-app 发布

将压缩镜像、`compose.yaml` 与填写后的 `.env` 上传到 `/home/eve-corp-manager/deploy/production/`，并执行：

```bash
cd /home/eve-corp-manager/deploy/production
./scripts/01-prepare-image-cache-directory.sh
gzip -dc /home/eve-corp-manager/eve-corp-manager-20260911-1.tar.gz | docker load
docker compose config
docker compose up -d --no-build
docker compose ps
docker compose logs --tail 200 api web
```

持久化目录为 `/home/eve-corp-manager/data/eve-images`，保存角色、军团、建筑、舰船和物品图片缓存。发布前必须运行 `scripts/01-prepare-image-cache-directory.sh`，使 API 容器的 `appuser` 可以自动下载和更新图片；不要删除此目录。`APP_BIND_IP=10.10.0.62` 仅将 Web 入口暴露给蓝队云内网反向代理。

发布验证通过后，立即删除本项目的旧版本 API 与 Web 镜像标签和镜像；本项目不保留镜像回滚副本。删除前应确认运行中的容器已经使用本次发布的镜像标签。

## 蓝队云网关

将 `gateway/eve.codeagent.cc.conf` 安装为 `/etc/nginx/conf.d/business/eve.codeagent.cc.conf`，然后执行：

```bash
nginx -t && systemctl reload nginx
curl --resolve eve.codeagent.cc:9443:127.0.0.1 https://eve.codeagent.cc:9443/
```

网关复用现有 `codeagent.cc` 证书，并将请求转发到 vm-app 私网地址 `10.10.0.62:18080`。
