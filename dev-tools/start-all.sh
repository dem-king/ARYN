#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAVA_DIR="${ROOT_DIR}/aryn-mall-java"
UI_DIR="${ROOT_DIR}/aryn-mall-ui"
MOBILE_DIR="${ROOT_DIR}/aryn-mall-uniapp"
XXL_DIR="${ROOT_DIR}/xxl-job-3.2.0"
RUNTIME_DIR="${TMPDIR:-/tmp}/aryn-mall-dev"
COMPOSE=()
REBUILD=false

usage() {
  cat <<'EOF'
用法: ./dev-tools/start-all.sh [--rebuild]

  --rebuild  强制重新打包全部 Cloud 后端和 XXL-JOB，并重建 Docker 镜像
  -h, --help 显示帮助
EOF
}

log() {
  printf '[aryn] %s\n' "$*"
}

die() {
  printf '[aryn] 错误: %s\n' "$*" >&2
  exit 1
}

while (($# > 0)); do
  case "$1" in
    --rebuild) REBUILD=true ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1" ;;
  esac
  shift
done

for command_name in docker mvn pnpm curl unzip; do
  command -v "${command_name}" >/dev/null 2>&1 || die "未找到命令: ${command_name}"
done

ensure_docker() {
  if docker info >/dev/null 2>&1; then
    return
  fi

  if [[ "$(uname -s)" == "Darwin" && -d /Applications/OrbStack.app ]]; then
    log "Docker 未运行，正在启动 OrbStack..."
    open -gja OrbStack
  elif [[ "$(uname -s)" == "Darwin" && -d /Applications/Docker.app ]]; then
    log "Docker 未运行，正在启动 Docker Desktop..."
    open -gja Docker
  else
    die "Docker 未运行，请先启动 Docker 服务"
  fi

  for _ in $(seq 1 90); do
    docker info >/dev/null 2>&1 && return
    sleep 1
  done
  die "等待 Docker 启动超时"
}

init_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose -f "${JAVA_DIR}/docker-compose.yml")
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose -f "${JAVA_DIR}/docker-compose.yml")
  else
    die "未找到 Docker Compose（docker compose 或 docker-compose）"
  fi
}

ensure_rocketmq_image() {
  local local_image="aryn/rocketmq:5.4.0-amd64"
  local upstream_image="apache/rocketmq:5.4.0"
  if docker image inspect "${local_image}" >/dev/null 2>&1; then
    return
  fi
  if ! docker image inspect --platform linux/amd64 "${upstream_image}" >/dev/null 2>&1; then
    log "拉取兼容 Apple Silicon 的 RocketMQ amd64 镜像..."
    docker pull --platform linux/amd64 "${upstream_image}"
  fi
  docker build --platform linux/amd64 --pull=false -t "${local_image}" - <<EOF
FROM ${upstream_image}
EOF
}

backend_artifacts_exist() {
  local artifact
  for artifact in \
    aryn-gateway/target/aryn-gateway.jar \
    aryn-auth/target/aryn-auth.jar \
    aryn-upms/aryn-upms-biz/target/aryn-upms-biz.jar \
    aryn-user/aryn-user-biz/target/aryn-user-biz.jar \
    aryn-message/aryn-message-biz/target/aryn-message-biz.jar \
    aryn-product/aryn-product-biz/target/aryn-product-biz.jar \
    aryn-order/aryn-order-biz/target/aryn-order-biz.jar \
    aryn-promotion/aryn-promotion-biz/target/aryn-promotion-biz.jar \
    aryn-pay/aryn-pay-biz/target/aryn-pay-biz.jar; do
    [[ -f "${JAVA_DIR}/${artifact}" ]] || return 1
    unzip -p "${JAVA_DIR}/${artifact}" META-INF/MANIFEST.MF 2>/dev/null | grep -q '^Start-Class:' || return 1
  done
}

build_backend() {
  log "打包 Cloud 后端（跳过测试）..."
  (
    cd "${JAVA_DIR}"
    mvn -Pcloud -DskipTests package \
      -pl aryn-gateway,aryn-auth,aryn-upms/aryn-upms-biz,aryn-user/aryn-user-biz,aryn-message/aryn-message-biz,aryn-product/aryn-product-biz,aryn-order/aryn-order-biz,aryn-promotion/aryn-promotion-biz,aryn-pay/aryn-pay-biz \
      -am
  )
}

build_xxl_job() {
  log "打包 XXL-JOB Admin（跳过测试）..."
  (cd "${XXL_DIR}" && mvn -DskipTests package -pl xxl-job-admin)
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local attempts="${3:-120}"
  local i
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS --max-time 2 "${url}" >/dev/null 2>&1; then
      log "${name} 已就绪: ${url}"
      return 0
    fi
    sleep 1
  done
  log "警告: ${name} 在等待时间内未就绪，请检查日志"
  return 1
}

wait_for_tcp() {
  local name="$1"
  local port="$2"
  local attempts="${3:-120}"
  local i
  for ((i = 1; i <= attempts; i++)); do
    if (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null; then
      log "${name} 已就绪: localhost:${port}"
      return 0
    fi
    sleep 1
  done
  log "警告: ${name} 在等待时间内未就绪，请检查日志"
  return 1
}

start_frontend() {
  local name="$1"
  local work_dir="$2"
  local port="$3"
  shift 3
  local executable="$1"
  shift
  if [[ "${executable}" != /* ]]; then
    executable="$(command -v "${executable}")"
  fi
  local pid_file="${RUNTIME_DIR}/${name}.pid"
  local log_file="${RUNTIME_DIR}/${name}.log"

  if curl -fsS --max-time 2 "http://localhost:${port}" >/dev/null 2>&1; then
    log "${name} 已在端口 ${port} 运行"
    return
  fi

  if [[ ! -d "${work_dir}/node_modules" ]]; then
    log "${name} 缺少依赖，执行 pnpm install..."
    (cd "${work_dir}" && pnpm install)
  fi

  if [[ -f "${pid_file}" ]]; then
    local old_pid
    old_pid="$(cat "${pid_file}" 2>/dev/null || true)"
    if [[ -n "${old_pid}" ]] && kill -0 "${old_pid}" 2>/dev/null; then
      kill "${old_pid}" 2>/dev/null || true
    fi
  fi

  log "启动 ${name}，日志: ${log_file}"
  if [[ "$(uname -s)" == "Darwin" ]]; then
    local launch_label="com.aryn.mall.${name}"
    launchctl remove "${launch_label}" >/dev/null 2>&1 || true
    launchctl submit -l "${launch_label}" -o "${log_file}" -e "${log_file}" -- \
      /bin/bash -c 'cd "$1" && runtime_path="$2" && shift 2 && exec env PATH="${runtime_path}" VITE_OPEN_BOOT=false "$@"' \
      _ "${work_dir}" "${PATH}" "${executable}" "$@"
    for _ in $(seq 1 20); do
      local launch_pid
      launch_pid="$(launchctl print "gui/$(id -u)/${launch_label}" 2>/dev/null | awk '/pid =/ {print $3; exit}')"
      if [[ -n "${launch_pid}" ]]; then
        echo "${launch_pid}" >"${pid_file}"
        break
      fi
      sleep 0.1
    done
  else
    (
      cd "${work_dir}"
      nohup env VITE_OPEN_BOOT=false "${executable}" "$@" >"${log_file}" 2>&1 &
      echo $! >"${pid_file}"
    )
  fi
}

mkdir -p "${RUNTIME_DIR}"
ensure_docker
init_compose
ensure_rocketmq_image

if [[ "${REBUILD}" == "true" ]] || ! backend_artifacts_exist; then
  build_backend
fi
if [[ "${REBUILD}" == "true" ]] || [[ ! -f "${XXL_DIR}/xxl-job-admin/target/xxl-job-admin-3.2.0.jar" ]]; then
  build_xxl_job
fi

log "启动基础设施、Cloud 后端和 XXL-JOB..."
if [[ "${REBUILD}" == "true" ]]; then
  "${COMPOSE[@]}" up -d --build
else
  "${COMPOSE[@]}" up -d
fi

start_frontend "admin-ui" "${UI_DIR}" 5777 pnpm dev:ele
start_frontend "mobile-h5" "${MOBILE_DIR}" 8888 pnpm dev:h5

backend_ready=true
wait_for_tcp "RocketMQ Broker" 10911 180 || backend_ready=false
wait_for_url "Auth" "http://localhost:5227/actuator/health" 180 || backend_ready=false
wait_for_url "UPMS" "http://localhost:5327/actuator/health" 180 || backend_ready=false
wait_for_url "User" "http://localhost:7527/actuator/health" 180 || backend_ready=false
wait_for_url "Product" "http://localhost:6300/actuator/health" 180 || backend_ready=false
wait_for_url "Order" "http://localhost:6400/actuator/health" 180 || backend_ready=false
wait_for_url "Promotion" "http://localhost:6500/actuator/health" 180 || backend_ready=false
wait_for_url "Pay" "http://localhost:6900/actuator/health" 180 || backend_ready=false
wait_for_url "Gateway" "http://localhost:9999/actuator/health" 180 || backend_ready=false
wait_for_url "XXL-JOB" "http://localhost:7002/xxl-job-admin/actuator/health" 180 || backend_ready=false
wait_for_url "管理端" "http://localhost:5777" 120 || backend_ready=false
wait_for_url "移动端 H5" "http://localhost:8888" 120 || backend_ready=false

printf '\n启动结果:\n'
printf '  管理端:   http://localhost:5777\n'
printf '  移动端:   http://localhost:8888\n'
printf '  Gateway: http://localhost:9999\n'
printf '  XXL-JOB: http://localhost:7002/xxl-job-admin\n'
printf '  前端日志: %s\n' "${RUNTIME_DIR}"

[[ "${backend_ready}" == "true" ]] || exit 1
