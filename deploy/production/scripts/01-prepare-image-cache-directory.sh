#!/usr/bin/env bash
set -euo pipefail

# API 容器以 uid 10001 的 appuser 运行；宿主机挂载目录必须由该用户可写，才能按需下载并持久化国服图片。
script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cache_directory="${EVE_IMAGE_CACHE_HOST_DIR:-${script_directory}/../data/eve-images}"

install -d -o 10001 -g 0 -m 0775 "$cache_directory"
