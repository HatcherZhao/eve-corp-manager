#!/usr/bin/env bash
# 构建并导出生产镜像包；不上传、不部署，也不清理现有镜像。
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
readonly OUTPUT_DIR="${PROJECT_ROOT}/output/docker"

usage() {
  cat <<'EOF'
用法：scripts/g010/01-package-production-images.sh [VERSION] [--skip-build]

构建 linux/amd64 API、Web 镜像，并输出 tar.gz 与 SHA-256 校验文件。
VERSION 默认读取仓库根目录 VERSION；显式传入时必须与该文件一致。
--skip-build 仅使用已存在的后端 JAR 和 frontend/dist 进行镜像打包。
可选环境变量 WEB_BASE_IMAGE 可指定已验证的 Web 基础镜像。
EOF
}

readonly VERSION_FILE="${PROJECT_ROOT}/VERSION"
if [[ ! -f "${VERSION_FILE}" ]]; then
  echo "缺少版本文件：${VERSION_FILE}" >&2
  exit 1
fi
repository_version="$(tr -d '[:space:]' < "${VERSION_FILE}")"

version=""
skip_build=false
for argument in "$@"; do
  case "${argument}" in
    --skip-build) skip_build=true ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "不支持的参数：${argument}" >&2; usage >&2; exit 2 ;;
    *)
      if [[ -n "${version}" ]]; then
        echo "只能提供一个版本号。" >&2
        usage >&2
        exit 2
      fi
      version="${argument}"
      ;;
  esac
done

if [[ -z "${version}" ]]; then
  version="${repository_version}"
fi
if [[ ! "${version}" =~ ^0\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "开发阶段版本必须为 0.次版本.修订号，例如 0.1.0。" >&2
  exit 2
fi
if [[ "${version}" != "${repository_version}" ]]; then
  echo "传入版本 ${version} 与 VERSION 中的 ${repository_version} 不一致。" >&2
  exit 2
fi

command -v docker >/dev/null || { echo "未找到 docker。" >&2; exit 1; }
docker buildx version >/dev/null || { echo "Docker Buildx 不可用。" >&2; exit 1; }
if ! grep -Fq "<revision>${version}</revision>" "${PROJECT_ROOT}/backend/pom.xml"; then
  echo "backend/pom.xml 的 revision 未同步为 ${version}。" >&2
  exit 1
fi
frontend_version="$(node -p "require('${PROJECT_ROOT}/frontend/package.json').version")"
if [[ "${frontend_version}" != "${version}" ]]; then
  echo "frontend/package.json 的 version 未同步为 ${version}。" >&2
  exit 1
fi

if [[ "${skip_build}" == false ]]; then
  (
    cd "${PROJECT_ROOT}/backend"
    mvn -B -ntp -Pfat_jar -pl continew-server -am -Dspotless.apply.skip=true package
  )
  (
    cd "${PROJECT_ROOT}/frontend"
    corepack enable
    pnpm install --frozen-lockfile
    pnpm build
  )
fi

readonly API_JAR="${PROJECT_ROOT}/backend/continew-server/target/continew-admin.jar"
readonly WEB_ENTRY="${PROJECT_ROOT}/frontend/dist/index.html"
if [[ ! -s "${API_JAR}" ]]; then
  echo "缺少后端构建产物：${API_JAR}" >&2
  exit 1
fi
if [[ ! -f "${WEB_ENTRY}" ]]; then
  echo "缺少前端构建产物：${WEB_ENTRY}" >&2
  exit 1
fi

readonly api_image="eve-corp-manager-api:${version}"
readonly web_image="eve-corp-manager-web:${version}"
readonly package_file="${OUTPUT_DIR}/eve-corp-manager-${version}.tar.gz"
readonly checksum_file="${package_file}.sha256"
if [[ -e "${package_file}" || -e "${checksum_file}" ]]; then
  echo "目标包已存在，为避免覆盖已停止：${package_file}" >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"
readonly web_base_image="${WEB_BASE_IMAGE:-nginx:1.27-alpine}"
package_completed=false

# 构建失败时移除不完整压缩包，避免后续误将其上传发布。
cleanup_partial_package() {
  if [[ "${package_completed}" == false ]]; then
    rm -f "${package_file}" "${checksum_file}"
  fi
}
trap cleanup_partial_package EXIT

docker buildx build --platform linux/amd64 --provenance=false --load \
  -f "${PROJECT_ROOT}/deploy/production/Dockerfile.api" \
  -t "${api_image}" "${PROJECT_ROOT}"
docker buildx build --platform linux/amd64 --provenance=false --load \
  --build-arg "WEB_BASE_IMAGE=${web_base_image}" \
  -f "${PROJECT_ROOT}/deploy/production/Dockerfile.web" \
  -t "${web_image}" "${PROJECT_ROOT}"

docker save "${api_image}" "${web_image}" | gzip -c > "${package_file}"
(
  cd "${OUTPUT_DIR}"
  shasum -a 256 "$(basename "${package_file}")" > "$(basename "${checksum_file}")"
)
package_completed=true

echo "镜像包：${package_file}"
echo "校验文件：${checksum_file}"
echo "已加载镜像：${api_image}、${web_image}"
