#!/usr/bin/env bash
# 复用已验证的 amd64 基底交付新鲜度修复，不拉取镜像或启动长期容器。
set -euo pipefail
cd "$(dirname "$0")/../.."
revision=$(git rev-parse HEAD)
tag="$(date +%Y%m%d)-${revision:0:7}"
destination="$PWD/output/g018/$tag"
test -f backend/continew-server/target/continew-admin.jar
test -f frontend/dist/index.html
test ! -e "$destination"
mkdir -p "$destination"
cp backend/continew-server/target/continew-admin.jar "$destination/app.jar"
cp -R frontend/dist "$destination/dist"
cp deploy/production/web-nginx.conf "$destination/web-nginx.conf"
for service in api web; do
  base="eve-corp-manager-$service:20261019-014b4fe"
  test "$(docker image inspect "$base" --format '{{.Os}}/{{.Architecture}}')" = linux/amd64
  docker build --platform linux/amd64 --pull=false \
    --build-arg "BASE_IMAGE=$base" --build-arg "REVISION=$revision" \
    -f "scripts/g016/0$([ "$service" = api ] && printf 2 || printf 3)-Dockerfile.$service" \
    -t "eve-corp-manager-$service:$tag" "$destination"
done
docker save "eve-corp-manager-api:$tag" "eve-corp-manager-web:$tag" \
  | gzip -1 > "$destination/release-$tag.tar.gz"
shasum -a 256 "$destination/app.jar" "$destination/dist/index.html" "$destination/release-$tag.tar.gz"
docker image inspect "eve-corp-manager-api:$tag" "eve-corp-manager-web:$tag" \
  --format '{{.RepoTags}} {{.Os}}/{{.Architecture}} {{.Size}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
