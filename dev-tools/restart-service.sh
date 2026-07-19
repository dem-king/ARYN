#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAVA_DIR="${ROOT_DIR}/aryn-mall-java"
XXL_DIR="${ROOT_DIR}/xxl-job-3.2.0"
COMPOSE=()
NO_BUILD=false

usage() {
  cat <<'EOF'
用法: ./dev-tools/restart-service.sh <服务名> [--no-build]

服务名:
  gateway auth upms user product order promotion pay xxl-job

选项:
  --no-build  不打包、不重建镜像，仅重启已有容器

示例:
  ./dev-tools/restart-service.sh order
  ./dev-tools/restart-service.sh gateway --no-build
EOF
}

log() {
  printf '[aryn] %s\n' "$*"
}

die() {
  printf '[aryn] 错误: %s\n' "$*" >&2
  exit 1
}

[[ $# -gt 0 ]] || { usage; exit 1; }
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  usage
  exit 0
fi
SERVICE_ALIAS="$1"
shift
while (($# > 0)); do
  case "$1" in
    --no-build) NO_BUILD=true ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1" ;;
  esac
  shift
done

case "${SERVICE_ALIAS}" in
  gateway)   COMPOSE_SERVICE=aryn-gateway;       MAVEN_MODULE=aryn-gateway;                         PORT=9999; HEALTH_PATH=/actuator/health ;;
  auth)      COMPOSE_SERVICE=aryn-auth;          MAVEN_MODULE=aryn-auth;                            PORT=5227; HEALTH_PATH=/actuator/health ;;
  upms)      COMPOSE_SERVICE=aryn-upms-biz;      MAVEN_MODULE=aryn-upms/aryn-upms-biz;              PORT=5327; HEALTH_PATH=/actuator/health ;;
  user)      COMPOSE_SERVICE=aryn-user-biz;      MAVEN_MODULE=aryn-user/aryn-user-biz;              PORT=7527; HEALTH_PATH=/actuator/health ;;
  product)   COMPOSE_SERVICE=aryn-product-biz;   MAVEN_MODULE=aryn-product/aryn-product-biz;        PORT=6300; HEALTH_PATH=/actuator/health ;;
  order)     COMPOSE_SERVICE=aryn-order-biz;     MAVEN_MODULE=aryn-order/aryn-order-biz;            PORT=6400; HEALTH_PATH=/actuator/health ;;
  promotion) COMPOSE_SERVICE=aryn-promotion-biz; MAVEN_MODULE=aryn-promotion/aryn-promotion-biz;    PORT=6500; HEALTH_PATH=/actuator/health ;;
  pay)       COMPOSE_SERVICE=aryn-pay-biz;       MAVEN_MODULE=aryn-pay/aryn-pay-biz;                PORT=6900; HEALTH_PATH=/actuator/health ;;
  xxl-job)   COMPOSE_SERVICE=aryn-xxl-job-admin; MAVEN_MODULE=xxl-job-admin;                        PORT=7002; HEALTH_PATH=/xxl-job-admin/actuator/health ;;
  *) die "不支持的服务名: ${SERVICE_ALIAS}（运行 --help 查看列表）" ;;
esac

command -v docker >/dev/null 2>&1 || die "未找到命令: docker"
docker info >/dev/null 2>&1 || die "Docker 未运行，请先执行 ./dev-tools/start-all.sh"
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose -f "${JAVA_DIR}/docker-compose.yml")
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose -f "${JAVA_DIR}/docker-compose.yml")
else
  die "未找到 Docker Compose（docker compose 或 docker-compose）"
fi

if [[ "${NO_BUILD}" == "true" ]]; then
  log "仅重启容器 ${COMPOSE_SERVICE}..."
  "${COMPOSE[@]}" restart "${COMPOSE_SERVICE}"
else
  command -v mvn >/dev/null 2>&1 || die "未找到命令: mvn"
  log "增量打包 ${SERVICE_ALIAS}（包含其 Maven 上游依赖，跳过测试）..."
  if [[ "${SERVICE_ALIAS}" == "xxl-job" ]]; then
    (cd "${XXL_DIR}" && mvn -DskipTests package -pl "${MAVEN_MODULE}")
  else
    (cd "${JAVA_DIR}" && mvn -Pcloud -DskipTests package -pl "${MAVEN_MODULE}" -am)
  fi

  log "重建镜像并只替换 ${COMPOSE_SERVICE} 容器..."
  "${COMPOSE[@]}" build "${COMPOSE_SERVICE}"
  "${COMPOSE[@]}" up -d --no-deps --force-recreate "${COMPOSE_SERVICE}"
fi

log "等待 ${SERVICE_ALIAS} 健康检查..."
for _ in $(seq 1 180); do
  if curl -fsS --max-time 2 "http://localhost:${PORT}${HEALTH_PATH}" >/dev/null 2>&1; then
    log "${SERVICE_ALIAS} 已恢复: http://localhost:${PORT}${HEALTH_PATH}"
    exit 0
  fi
  sleep 1
done

"${COMPOSE[@]}" logs --tail=80 "${COMPOSE_SERVICE}" || true
die "${SERVICE_ALIAS} 在等待时间内未恢复"
