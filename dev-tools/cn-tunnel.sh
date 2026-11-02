#!/usr/bin/env bash
#
# 国内内网穿透客户端启停（默认按 ChmlFrp 免费隧道写，实际与厂商无关）。
#
# 为什么单独抽一个脚本：
#   国内免费穿透（ChmlFrp / natapp / cpolar）的客户端和配置各不相同，
#   但都是「一条命令常驻 + 一个公网域名」。这里只负责把命令托管成后台进程，
#   并做存活检查，避免和 Cloudflare 隧道脚本耦合。
#
# 用法（仓库根目录）：
#   cp dev-tools/cn-tunnel.env.example dev-tools/cn-tunnel.env  # 首次：填入命令和域名
#   ./dev-tools/cn-tunnel.sh              # 启动（幂等，已活着就复用）
#   ./dev-tools/cn-tunnel.sh --status     # 查看进程和公网探活
#   ./dev-tools/cn-tunnel.sh --stop       # 停止
#   ./dev-tools/cn-tunnel.sh --restart    # 重启
#   ./dev-tools/cn-tunnel.sh --config /path/to/env
#
# 配置项（dev-tools/cn-tunnel.env）：
#   CN_TUNNEL_CMD   启动穿透客户端的命令，例如：
#                     "/path/to/frpc -c /path/to/frpc.ini"
#                     "/path/to/chmlfrp-launcher --token xxxx"
#   CN_PUBLIC_URL   公网地址（含协议），例如 https://demo.chmlfrp.cn
#                   用于探活和打印链接；留空则只启动进程。
#
# 注意：本文件不提交真实 token；cn-tunnel.env 已在 .gitignore 中忽略。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="${CN_TUNNEL_CONFIG:-${SCRIPT_DIR}/cn-tunnel.env}"
RUN_DIR="${TMPDIR:-/tmp}/aryn-cn-tunnel"
STATE_FILE="${RUN_DIR}/state.env"
TUNNEL_LOG="${RUN_DIR}/tunnel.log"
ACTION="start"

log() { printf '[cn-tunnel] %s\n' "$*"; }
warn() { printf '[cn-tunnel] 警告: %s\n' "$*" >&2; }
die() { printf '[cn-tunnel] 错误: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '2,29p' "$0"; }

while (($# > 0)); do
  case "$1" in
    start) ACTION="start"; shift ;;
    --status) ACTION="status"; shift ;;
    --stop) ACTION="stop"; shift ;;
    --restart) ACTION="restart"; shift ;;
    --config) CONFIG="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（--help 查看用法）" ;;
  esac
done

[ -f "$CONFIG" ] || die "缺少配置文件 ${CONFIG}；先执行：cp dev-tools/cn-tunnel.env.example dev-tools/cn-tunnel.env，并按注释填写"
# shellcheck disable=SC1090
source "$CONFIG"
CN_TUNNEL_CMD="${CN_TUNNEL_CMD:-}"
CN_PUBLIC_URL="${CN_PUBLIC_URL:-}"

state_value() { sed -n "s/^$1=//p" "$STATE_FILE" 2>/dev/null | head -1; }
state_pid() { state_value PID; }
pid_alive() { local pid="${1:-}"; [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; }

public_code() {
  [ -n "$CN_PUBLIC_URL" ] || { printf ''; return 0; }
  curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${CN_PUBLIC_URL}/__health" 2>/dev/null || true
}

print_links() {
  [ -n "$CN_PUBLIC_URL" ] || return 0
  printf '  H5      : %s/\n' "$CN_PUBLIC_URL"
  printf '  管理端  : %s/admin/\n' "$CN_PUBLIC_URL"
}

stop_tunnel() {
  local pid
  pid="$(state_pid)"
  if pid_alive "$pid"; then
    pkill -P "$pid" 2>/dev/null || true
    kill "$pid" 2>/dev/null || true
    sleep 1
    if pid_alive "$pid"; then
      kill -9 "$pid" 2>/dev/null || true
    fi
    log "已停止穿透客户端（pid=${pid}）"
  else
    log '没有正在运行的穿透客户端'
  fi
  rm -f "$STATE_FILE"
}

start_tunnel() {
  local pid code i
  pid="$(state_pid)"
  if pid_alive "$pid"; then
    log "穿透客户端已在运行（pid=${pid}），沿用现有进程"
    print_links
    return 0
  fi

  [ -n "$CN_TUNNEL_CMD" ] || die 'CN_TUNNEL_CMD 为空，请在 cn-tunnel.env 里填写启动命令'
  mkdir -p "$RUN_DIR"
  : > "$TUNNEL_LOG"

  log "启动穿透客户端：${CN_TUNNEL_CMD}"
  # 登录 shell 保证能找到用户 PATH 下的 frpc/启动器
  nohup bash -lc "$CN_TUNNEL_CMD" >>"$TUNNEL_LOG" 2>&1 &
  pid=$!
  {
    printf 'PID=%s\n' "$pid"
    printf 'URL=%s\n' "$CN_PUBLIC_URL"
    printf 'LOG=%s\n' "$TUNNEL_LOG"
  } > "$STATE_FILE"
  disown "$pid" 2>/dev/null || true

  if [ -n "$CN_PUBLIC_URL" ]; then
    log "等待公网地址生效：${CN_PUBLIC_URL}"
    code=000
    for i in $(seq 1 60); do
      if ! pid_alive "$pid"; then
        warn "穿透客户端已退出，最近日志："
        tail -n 20 "$TUNNEL_LOG" >&2 || true
        die '穿透客户端启动失败'
      fi
      code="$(public_code)"
      [ -n "$code" ] || code=000
      [ "$code" != "000" ] && break
      sleep 1
    done
    if [ "$code" = "000" ]; then
      warn "60 秒内仍未探通 ${CN_PUBLIC_URL}；进程可能还在连接，稍后用 --status 复查"
      warn "日志：${TUNNEL_LOG}"
    else
      log "公网探活 HTTP ${code}：${CN_PUBLIC_URL}"
    fi
  fi

  print_links
  log "日志：${TUNNEL_LOG}"
  log '停止：./dev-tools/cn-tunnel.sh --stop'
}

case "$ACTION" in
  status)
    pid="$(state_pid)"
    printf '  配置    : %s\n' "$CONFIG"
    printf '  命令    : %s\n' "${CN_TUNNEL_CMD:-<未配置>}"
    printf '  公网地址: %s\n' "${CN_PUBLIC_URL:-<未配置>}"
    if pid_alive "$pid"; then
      printf '  进程    : ✅ 运行中（pid=%s）\n' "$pid"
    else
      printf '  进程    : ❌ 未运行\n'
    fi
    code="$(public_code)"
    if [ -n "$CN_PUBLIC_URL" ]; then
      printf '  公网探活: %s\n' "$([ -n "$code" ] && [ "$code" != "000" ] && echo "✅ HTTP ${code}" || echo "❌ 不可达")"
    fi
    print_links
    exit 0
    ;;
  stop)
    stop_tunnel
    exit 0
    ;;
  restart)
    stop_tunnel
    ACTION="start"
    ;;
esac

start_tunnel
