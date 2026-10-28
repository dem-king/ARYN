#!/usr/bin/env bash
#
# 本地服务临时公网访问：把本机任意端口映射成受信任 https 域名（Cloudflare 免费临时隧道）。
#
# 典型用途：让不在同一局域网的另一台电脑 / 同事临时打开本机 dev server，
# 例如管理端 Vite dev server（默认 5777）：./dev-tools/dev-public-tunnel.sh 5777
#
# 注意：
#   - Vite 6 默认会校验 Host 头，隧道域名会返回 403 Blocked request；
#     本脚本默认给回源请求带 `--http-host-header 127.0.0.1:<port>`，绕过该校验；
#   - 临时隧道域名每次启动都会变，且运行期间本机端口会暴露在随机公网域名上，
#     仅本地开发/临时演示使用，用完 --stop；
#   - 需要 cloudflared（脚本会复用 dev-image-tunnel.sh 下载的缓存 / 自动下载）。
#
# 用法:
#   ./dev-tools/dev-public-tunnel.sh <端口>              # 前台运行，Ctrl+C 关闭隧道
#   ./dev-tools/dev-public-tunnel.sh <端口> --detach     # 后台运行，用 --stop 关闭
#   ./dev-tools/dev-public-tunnel.sh <端口> --stop       # 停止该端口对应的后台隧道
#   ./dev-tools/dev-public-tunnel.sh <端口> --no-host-rewrite  # 不重写 Host 头（非 Vite 服务可加）
#
# 环境变量:
#   ARYN_CLOUDFLARED  指定 cloudflared 可执行文件；ARYN_BIN_DIR 指定下载缓存目录

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PFX="[public-tunnel]"

log() { printf '%s %s\n' "$PFX" "$*"; }
warn() { printf '%s 警告: %s\n' "$PFX" "$*" >&2; }
die() { printf '%s 错误: %s\n' "$PFX" "$*" >&2; exit 1; }
usage() { sed -n '2,26p' "$0"; }

PORT=""
ACTION="foreground"
HOST_REWRITE=true

while (($# > 0)); do
  case "$1" in
    --detach) ACTION="detach"; shift ;;
    --stop) ACTION="stop"; shift ;;
    --no-host-rewrite) HOST_REWRITE=false; shift ;;
    -h|--help) usage; exit 0 ;;
    -[0-9]*) die "未知参数: $1" ;;
    *)
      if [ -z "$PORT" ]; then PORT="$1"; else die "多余参数: $1"; fi
      shift
      ;;
  esac
done

[ -n "$PORT" ] || die "用法: $0 <端口> [--detach|--stop|--no-host-rewrite]"
[[ "$PORT" =~ ^[0-9]+$ ]] || die "端口不合法: ${PORT}"

command -v docker >/dev/null 2>&1 || true
RUN_DIR="${TMPDIR:-/tmp}/aryn-public-tunnel"
STATE_FILE="${RUN_DIR}/port-${PORT}.env"
CF_LOG="${RUN_DIR}/port-${PORT}.log"

ensure_cloudflared() {
  if [ -n "${ARYN_CLOUDFLARED:-}" ]; then
    CF_BIN="$ARYN_CLOUDFLARED"
    [ -x "$CF_BIN" ] || die "ARYN_CLOUDFLARED 不可执行: $CF_BIN"
    return 0
  fi
  if command -v cloudflared >/dev/null 2>&1; then
    CF_BIN=$(command -v cloudflared)
    return 0
  fi
  local dir candidates
  candidates="${ARYN_BIN_DIR:-} $HOME/.cache/aryn/bin ${TMPDIR:-/tmp}/aryn/bin"
  for dir in $candidates; do
    [ -n "$dir" ] || continue
    if [ -x "$dir/cloudflared" ]; then
      CF_BIN="$dir/cloudflared"
      return 0
    fi
    if mkdir -p "$dir" 2>/dev/null && [ -w "$dir" ]; then
      CF_BIN="$dir/cloudflared"
      break
    fi
  done
  [ -n "${CF_BIN:-}" ] || die "找不到可写的 cloudflared 缓存目录（可用 ARYN_BIN_DIR 指定）"

  local os arch file url tmpdir
  os=$(uname -s)
  arch=$(uname -m)
  case "$os/$arch" in
    Darwin/arm64) file="cloudflared-darwin-arm64.tgz" ;;
    Darwin/x86_64) file="cloudflared-darwin-amd64.tgz" ;;
    Linux/x86_64) file="cloudflared-linux-amd64" ;;
    Linux/aarch64|Linux/arm64) file="cloudflared-linux-arm64" ;;
    *) die "暂不支持自动下载 cloudflared（$os/$arch），请手动安装后用 ARYN_CLOUDFLARED 指定" ;;
  esac
  url="https://github.com/cloudflare/cloudflared/releases/latest/download/${file}"
  tmpdir=$(mktemp -d) || die "无法创建临时目录"
  log "首次运行，下载 cloudflared（约 20MB）：${url}"
  curl -fL --retry 2 --connect-timeout 15 -o "$tmpdir/download" "$url" || die "cloudflared 下载失败（可 brew install cloudflared）"
  case "$file" in
    *.tgz) tar -xzf "$tmpdir/download" -C "$tmpdir" && mv "$tmpdir/cloudflared" "$CF_BIN" ;;
    *) mv "$tmpdir/download" "$CF_BIN" ;;
  esac
  rm -rf "$tmpdir"
  chmod +x "$CF_BIN" || die "无法写入 cloudflared: $CF_BIN"
}

stop_port() {
  if [ -f "$STATE_FILE" ]; then
    local pid
    pid=$(sed -n 's/^CF_PID=//p' "$STATE_FILE" 2>/dev/null | head -1)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      log "已停止端口 ${PORT} 的隧道（pid=${pid}）"
    else
      log "端口 ${PORT} 的隧道进程已不存在"
    fi
    rm -f "$STATE_FILE" "$CF_LOG"
  else
    log "端口 ${PORT} 没有后台隧道记录"
  fi
}

# 从 cloudflared 日志里提取快速隧道域名。
# 注意：失败日志里会出现 https://api.trycloudflare.com/tunnel，必须排除；
# 快速隧道域名一定带连字符（3~4 个单词拼接），api.trycloudflare.com 没有连字符。
extract_tunnel_host() {
  grep -oE "https://[a-zA-Z0-9-]+[.]trycloudflare[.]com" "$CF_LOG" 2>/dev/null \
    | grep -v "/api[.]trycloudflare[.]com" \
    | grep -E -- "-" \
    | head -1
}

start_tunnel_once() {
  local args=(tunnel --url "http://127.0.0.1:${PORT}" --no-autoupdate)
  if [ "$HOST_REWRITE" = "true" ]; then
    # Vite 6 校验 Host 头：回源时改写成 127.0.0.1:<port>，避免 403 Blocked request
    args+=(--http-host-header "127.0.0.1:${PORT}")
  fi
  : > "$CF_LOG"
  nohup "$CF_BIN" "${args[@]}" >"$CF_LOG" 2>&1 &
  CF_PID=$!

  local i
  for i in $(seq 1 35); do
    CF_HOST=$(extract_tunnel_host)
    [ -n "$CF_HOST" ] && return 0
    if grep -q "failed to request quick Tunnel" "$CF_LOG" 2>/dev/null; then
      kill "$CF_PID" 2>/dev/null || true
      return 1
    fi
    kill -0 "$CF_PID" 2>/dev/null || return 1
    sleep 1
  done
  kill "$CF_PID" 2>/dev/null || true
  return 1
}

start_tunnel() {
  ensure_cloudflared
  mkdir -p "$RUN_DIR"
  local attempt
  for attempt in 1 2 3; do
    if start_tunnel_once; then
      sleep 3 # 等边缘路由生效
      log "隧道已就绪：${CF_HOST}  ->  http://127.0.0.1:${PORT}"
      return 0
    fi
    warn "隧道创建失败（第 ${attempt}/3 次，可能网络抖动），重试…"
    sleep 3
  done
  die "隧道创建失败，日志见 ${CF_LOG}（可检查网络/代理后重试）"
}

check_local() {
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:${PORT}/" 2>/dev/null)
  [ -n "$code" ] || code=000
  log "本机端口 ${PORT} 自检 HTTP ${code}"
}

check_tunnel() {
  local code host doh_ip
  host="${CF_HOST#https://}"
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$CF_HOST/" 2>/dev/null)
  [ -n "$code" ] || code=000
  if [ "$code" != "200" ]; then
    doh_ip=$(curl -s --max-time 5 -H 'accept: application/dns-json' \
      "https://1.1.1.1/dns-query?name=${host}&type=A" 2>/dev/null \
      | sed -n 's/.*"data":"\([0-9.]*\)".*/\1/p' | head -1)
    if [ -n "$doh_ip" ]; then
      code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 --resolve "${host}:443:${doh_ip}" "$CF_HOST/" 2>/dev/null)
      [ -n "$code" ] || code=000
    fi
  fi
  if [ "$code" = "200" ]; then
    log "隧道自检 HTTP 200"
  else
    warn "隧道本机自检 HTTP ${code}（本机代理可能拦截新域名，以目标机器实测为准）"
  fi
}

case "$ACTION" in
  stop) stop_port; exit 0 ;;
esac

stop_port
check_local
start_tunnel
check_tunnel

if [ "$ACTION" = "detach" ]; then
  mkdir -p "$RUN_DIR"
  {
    printf 'CF_PID=%s\n' "$CF_PID"
    printf 'CF_HOST=%s\n' "$CF_HOST"
    printf 'PORT=%s\n' "$PORT"
  } > "$STATE_FILE"
  disown "$CF_PID" 2>/dev/null || true
  log "后台隧道已启动；URL：${CF_HOST}"
  log "停止：./dev-tools/dev-public-tunnel.sh ${PORT} --stop"
  exit 0
fi

cleanup() {
  kill "$CF_PID" 2>/dev/null || true
  rm -f "$STATE_FILE" "$CF_LOG"
  log "隧道已关闭"
}
trap 'cleanup; exit 0' INT TERM
trap cleanup EXIT
log "前台运行中，按 Ctrl+C 关闭隧道"
wait "$CF_PID"
