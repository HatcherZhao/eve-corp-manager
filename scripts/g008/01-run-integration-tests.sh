#!/usr/bin/env bash
set -euo pipefail

: "${G008_IT_DB_PWD:?请通过调用环境设置 G008_IT_DB_PWD，脚本不会读取或输出数据库密码}"

export G008_IT_DB_HOST="${G008_IT_DB_HOST:-127.0.0.1}"
export G008_IT_DB_PORT="${G008_IT_DB_PORT:-3306}"
export G008_IT_DB_NAME="${G008_IT_DB_NAME:-eve_corp_manager}"
export G008_IT_DB_USER="${G008_IT_DB_USER:-root}"
export G008_IT_REDIS_HOST="${G008_IT_REDIS_HOST:-127.0.0.1}"
export G008_IT_REDIS_PORT="${G008_IT_REDIS_PORT:-6379}"
export G008_IT_REDIS_DB="${G008_IT_REDIS_DB:-14}"
export G008_IT_REDIS_PWD="${G008_IT_REDIS_PWD:-}"

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${project_root}/backend"

exec mvn -B -ntp -Pfat_jar,integration-tests -pl continew-server -am \
  -Dspotless.apply.skip=true verify
