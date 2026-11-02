#!/usr/bin/env bash
#
# 一键启动国内演示环境：本机统一网关 + 国内内网穿透 + 图片地址切换。
#
# 前置：
#   1. 已构建产物：./dev-tools/build-cn-bundles.sh（或首次启动加 --build）
#   2. 已填写穿透配置：cp dev-tools/cn-tunnel.env.example dev-tools/cn-tunnel.env
#   3. 本机后端 Docker（aryn-boot 等）在运行，9999 可访问。
#
# 用法（仓库根目录）：
#   ./dev-tools/start-cn-demo.sh                 # 启动网关和隧道，并把图片地址切到公网域名
#   ./dev-tools/start-cn-demo.sh --build         # 先重建 H5 与管理端
#   ./dev-tools/start-cn-demo.sh --no-tunnel     # 只起本机网关（调试页面用）
#   ./dev-tools/start-cn-demo.sh --no-switch-images  # 不改数据库图片地址
#   ./dev-tools/start-cn-demo.sh --status        # 查看网关/隧道/图片主机
#   ./dev-tools/start-cn-demo.sh --stop          # 停止网关和隧道
#
# 说明：
#   - 网关只监听 127.0.0.1:8890，公网入口由 ChmlFrp/natapp/cpolar 映射该端口；
#   - 图片地址切换是幂等的，域名没变时重复执行没有副作用；
#   - 停止后如需把图片切回局域网，执行 ./dev-tools/dev-image-tunnel.sh --stop
#     （会同时停掉 Cloudflare 图片隧道）。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_DIR="${TMPDIR:-/tmp}/aryn-cn-gateway"
STATE_FILE="${RUN_DIR}/state.env"
GATEWAY_LOG="${RUN_DIR}/gateway.log"
PORT="${CN_GATEWAY_PORT:-8890}"
HOST="${CN_GATEWAY_HOST:-127.0.0.1}"
TUNNEL_CONFIG="${CN_TUNNEL_CONFIG:-${SCRIPT_DIR}/cn-tunnel.env}"

ACTION="start"
DO_BUILD=false
WITH_TUNNEL=true
SWITCH_IMAGES=true

log() { printf '[cn-demo] %s\n' "$*"; }
warn() { printf '[cn-demo] 警告: %s\n' "$*" >&2; }
die() { printf '[cn-demo] 错误: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '2,27p' "$0"; }

while (($# > 0)); do
  case "$1" in
    --build) DO_BUILD=true; shift ;;
    --no-tunnel) WITH_TUNNEL=false; shift ;;
    --no-switch-images) SWITCH_IMAGES=false; shift ;;
    --status) ACTION="status"; shift ;;
    --stop) ACTION="stop"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（--help 查看用法）" ;;
  esac
done

state_value() { sed -n "s/^$1=//p" "$STATE_FILE" 2>/dev/null | head -1; }
state_pid() { state_value PID; }
pid_alive() { local pid="${1:-}"; [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; }

gateway_code() {
  curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://${HOST}:${PORT}/__health" 2>/dev/null || true
}

public_url() {
  [ -f "$TUNNEL_CONFIG" ] || { printf ''; return 0; }
  # shellcheck disable=SC1090
  ( source "$TUNNEL_CONFIG"; printf '%s' "${CN_PUBLIC_URL:-}" )
}

print_links() {
  local url="$1"
  printf '\n'
  log '本地入口：'
  printf '  H5      : http://%s:%s/\n' "$HOST" "$PORT"
  printf '  管理端  : http://%s:%s/admin/\n' "$HOST" "$PORT"
  if [ -n "$url" ]; then
    log '公网入口（国内）：'
    printf '  H5      : %s/\n' "$url"
    printf '  管理端  : %s/admin/\n' "$url"
  fi
}

stop_gateway() {
  local pid
  pid="$(state_pid)"
  if pid_alive "$pid"; then
    kill "$pid" 2>/dev/null || true
    sleep 1
    pid_alive "$pid" && kill -9 "$pid" 2>/dev/null || true
    log "已停止网关（pid=${pid}）"
  else
    log '没有正在运行的网关'
  fi
  rm -f "$STATE_FILE"
}

start_gateway() {
  local pid code i
  pid="$(state_pid)"
  if pid_alive "$pid" && [ "$(gateway_code)" != "000" ]; then
    log "网关已在运行（pid=${pid}），沿用现有进程"
    return 0
  fi

  code="$(gateway_code)"
  if [ -n "$code" ] && [ "$code" != "000" ]; then
    die "端口 ${PORT} 已被其它进程占用（/__health 返回 ${code}）；先停掉旧的 H5 静态服务或改 CN_GATEWAY_PORT"
  fi

  mkdir -p "$RUN_DIR"
  : > "$GATEWAY_LOG"
  log "启动网关 http://${HOST}:${PORT}/ …"
  nohup node "${SCRIPT_DIR}/demo-gateway.mjs" \
    --host "$HOST" --port "$PORT" \
    >>"$GATEWAY_LOG" 2>&1 &
  pid=$!
  {
    printf 'PID=%s\n' "$pid"
    printf 'PORT=%s\n' "$PORT"
    printf 'HOST=%s\n' "$HOST"
  } > "$STATE_FILE"
  disown "$pid" 2>/dev/null || true

  for i in $(seq 1 20); do
    if ! pid_alive "$pid"; then
      warn '网关启动失败，最近日志：'
      tail -n 30 "$GATEWAY_LOG" >&2 || true
      die '网关启动失败'
    fi
    code="$(gateway_code)"
    [ "$code" = "200" ] && break
    sleep 1
  done
  [ "$(gateway_code)" = "200" ] || {
    warn '网关 20 秒内未就绪，最近日志：'
    tail -n 30 "$GATEWAY_LOG" >&2 || true
    die "网关未就绪，请检查 ${GATEWAY_LOG}"
  }
  log "网关已就绪：http://${HOST}:${PORT}/"
}

switch_images() {
  local url="$1"
  [ -n "$url" ] || return 0
  log "把数据库图片地址切到 ${url} …"
  if "${SCRIPT_DIR}/dev-image-tunnel.sh" --set-host "$url"; then
    log '图片地址已切换'
  else
    warn "图片地址切换失败；不影响页面和接口，H5 商品图可能仍指向旧域名"
  fi
}

case "$ACTION" in
  status)
    pid="$(state_pid)"
    printf '  网关进程: %s\n' "$(pid_alive "$pid" && echo "✅ 运行中（pid=${pid}）" || echo '❌ 未运行')"
    printf '  网关探活: %s\n' "$([ "$(gateway_code)" = "200" ] && echo '✅ HTTP 200' || echo '❌ 不可达')"
    printf '  网关日志: %s\n' "$GATEWAY_LOG"
    if [ -f "$TUNNEL_CONFIG" ]; then
      "${SCRIPT_DIR}/cn-tunnel.sh" --status || true
    else
      printf '  穿透配置: <未创建 %s>\n' "$TUNNEL_CONFIG"
    fi
    print_links "$(public_url)"
    exit 0
    ;;
  stop)
    if [ -f "$TUNNEL_CONFIG" ]; then
      "${SCRIPT_DIR}/cn-tunnel.sh" --stop || true
    fi
    stop_gateway
    exit 0
    ;;
esac

if $DO_BUILD; then
  "${SCRIPT_DIR}/build-cn-bundles.sh" || die '前端构建失败'
fi

[ -f "${ROOT_DIR}/aryn-mall-uniapp/dist/build/h5/index.html" ] \
  || die '缺少 H5 产物，请执行 ./dev-tools/build-cn-bundles.sh --only h5'

start_gateway

URL="$(public_url)"
if $WITH_TUNNEL; then
  if [ -f "$TUNNEL_CONFIG" ]; then
    "${SCRIPT_DIR}/cn-tunnel.sh" start || warn '穿透客户端启动失败；可先用本机入口调试'
  else
    warn "未找到 ${TUNNEL_CONFIG}；跳过公网穿透"
    warn "首次使用：cp dev-tools/cn-tunnel.env.example dev-tools/cn-tunnel.env 并填写命令/域名"
  fi
fi

if $SWITCH_IMAGES; then
  switch_images "$URL"
fi

print_links "$URL"
log "查看状态：./dev-tools/start-cn-demo.sh --status"
log "停止：./dev-tools/start-cn-demo.sh --stop"
