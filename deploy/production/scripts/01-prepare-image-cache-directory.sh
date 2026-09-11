#!/usr/bin/env bash
set -euo pipefail

# API 容器以 uid 10001 的 appuser 运行；宿主机挂载目录必须由该用户可写，才能按需下载并持久化国服图片。
cache_directory=/home/eve-corp-manager/data/eve-images

install -d -o 10001 -g 0 -m 0775 "$cache_directory"
