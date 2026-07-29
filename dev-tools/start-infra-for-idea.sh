#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAVA_DIR="${ROOT_DIR}/aryn-mall-java"
XXL_DIR="${ROOT_DIR}/xxl-job-3.2.0"
NACOS_MIGRATION="${JAVA_DIR}/db/cloud/5idea_host_config.sql"
COMPOSE=(
  docker compose
  -f "${JAVA_DIR}/docker-compose.yml"
  -f "${JAVA_DIR}/docker-compose-idea.yml"
)

JAVA_SERVICES=(
  aryn-gateway
  aryn-auth
  aryn-upms-biz
  aryn-user-biz
  aryn-message-biz
  aryn-product-biz
  aryn-order-biz
  aryn-promotion-biz
  aryn-pay-biz
)

INFRA_SERVICES=(
  aryn-mysql
  aryn-redis
  aryn-nacos
  aryn-rocketmq-namesrv
  aryn-rocketmq-broker
  aryn-seata
  aryn-xxl-job-admin
)

log() {
  printf '[aryn-infra] %s\n' "$*"
}

die() {
  printf '[aryn-infra] 错误: %s\n' "$*" >&2
  exit 1
}

wait_for_container_health() {
  local container_name="$1"
  local attempts="${2:-90}"
  local status
  for _ in $(seq 1 "${attempts}"); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_name}" 2>/dev/null || true)"
    if [[ "${status}" == "healthy" || "${status}" == "running" ]]; then
      return 0
    fi
    [[ "${status}" == "unhealthy" || "${status}" == "exited" ]] && break
    sleep 1
  done
  docker logs --tail 80 "${container_name}" 2>/dev/null || true
  die "容器 ${container_name} 未就绪"
}

wait_for_tcp() {
  local name="$1"
  local port="$2"
  local attempts="${3:-90}"
  for _ in $(seq 1 "${attempts}"); do
    if nc -z 127.0.0.1 "${port}" >/dev/null 2>&1; then
      log "${name} 已就绪: localhost:${port}"
      return 0
    fi
    sleep 1
  done
  die "等待 ${name} 端口 ${port} 超时"
}

for command_name in docker curl nc; do
  command -v "${command_name}" >/dev/null 2>&1 || die "未找到命令: ${command_name}"
done
docker info >/dev/null 2>&1 || die "Docker 未运行，请先启动 OrbStack 或 Docker Desktop"
docker compose version >/dev/null 2>&1 || die "未找到 Docker Compose"

if ! docker image inspect aryn/rocketmq:5.4.0-amd64 >/dev/null 2>&1; then
  log "准备 Apple Silicon 兼容的 RocketMQ 镜像..."
  docker pull --platform linux/amd64 apache/rocketmq:5.4.0
  docker build --platform linux/amd64 --pull=false -t aryn/rocketmq:5.4.0-amd64 - <<'EOF'
FROM apache/rocketmq:5.4.0
EOF
fi

if ! docker image inspect aryn-mall-java-aryn-xxl-job-admin >/dev/null 2>&1; then
  if [[ ! -f "${XXL_DIR}/xxl-job-admin/target/xxl-job-admin-3.2.0.jar" ]]; then
    command -v mvn >/dev/null 2>&1 || die "XXL-JOB 缺少构建产物，且未找到 Maven"
    log "打包 XXL-JOB Admin..."
    (cd "${XXL_DIR}" && mvn -DskipTests package -pl xxl-job-admin)
  fi
  log "构建 XXL-JOB Admin 镜像..."
  "${COMPOSE[@]}" build aryn-xxl-job-admin
fi

log "停止 Docker 中的 Java 业务服务，释放 IDEA 运行端口..."
"${COMPOSE[@]}" stop "${JAVA_SERVICES[@]}" >/dev/null

log "启动 MySQL、Redis、Nacos、RocketMQ、Seata 和 XXL-JOB..."
"${COMPOSE[@]}" up -d "${INFRA_SERVICES[@]}"

wait_for_container_health aryn-mysql 120
wait_for_container_health aryn-redis 60

log "同步适用于宿主机 IDEA 的 Nacos 配置默认值..."
docker exec -i aryn-mysql mysql -uroot -p123456 <"${NACOS_MIGRATION}"
"${COMPOSE[@]}" restart aryn-nacos >/dev/null

wait_for_tcp "MySQL" 3306
wait_for_tcp "Redis" 6379
wait_for_tcp "Nacos" 8848 120
wait_for_tcp "RocketMQ NameServer" 9876 120
wait_for_tcp "RocketMQ Broker" 10911 120
wait_for_tcp "Seata" 8091 120

for _ in $(seq 1 120); do
  if curl -fsS --max-time 2 http://localhost:7002/xxl-job-admin/actuator/health >/dev/null 2>&1; then
    log "XXL-JOB 已就绪: http://localhost:7002/xxl-job-admin"
    break
  fi
  sleep 1
done
curl -fsS --max-time 2 http://localhost:7002/xxl-job-admin/actuator/health >/dev/null \
  || die "XXL-JOB 未就绪"

docker exec aryn-redis redis-cli -a redis ping 2>/dev/null | grep -q PONG \
  || die "Redis 认证检查失败"
docker exec aryn-mysql mysql -uroot -p123456 -Nse 'SELECT 1' 2>/dev/null | grep -q 1 \
  || die "MySQL 连接检查失败"

printf '\n基础设施已就绪，IDEA 请启用 Maven 的 cloud Profile 后启动各服务。\n'
printf 'Nacos:   http://localhost:8080/\n'
printf 'XXL-JOB: http://localhost:7002/xxl-job-admin\n'
