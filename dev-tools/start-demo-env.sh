#!/usr/bin/env bash
#
# 一键启动/修复本地演示环境：
#   后端 Docker（aryn-boot 等） + H5 静态演示服务(8890) + 管理端 dev server(5777)+
#   图片/后端隧道(9999) + 管理端隧道(5777) + H5 隧道(8890)
#
# 背景：Cloudflare Quick Tunnel 的域名绑定在 cloudflared 进程上，进程一停域名立刻失效；
#       Mac 休眠/网络切换、终端或会话关闭、系统清理后台进程都会导致链接打不开。
#       演示前或链接失效时，跑一次本脚本即可恢复，并打印新的链接。
#
# 用法（仓库根目录，建议在你自己的终端里运行，别在 agent 会话里）：
#   ./dev-tools/start-demo-env.sh              # 启动/修复并打印链接（幂等，已活着的不重启）
#   ./dev-tools/start-demo-env.sh --status     # 只查看状态，不做任何修改
#   ./dev-tools/start-demo-env.sh --build      # 先重新构建 H5 与管理端静态包再启动
#   ./dev-tools/start-demo-env.sh --no-admin   # 不启动管理端
#   ./dev-tools/start-demo-env.sh --no-h5      # 不启动 H5
#   ./dev-tools/start-demo-env.sh --stop       # 停止隧道/前端服务（若装了 launchd 自启会一并暂停）
#
# 建议演示期间：caffeinate -dis ./dev-tools/start-demo-env.sh  # 防止 Mac 休眠
#
# 如果已部署 Cloudflare Pages：页面链接不会变，但要把两个 Pages 项目的 BACKEND_URL
# 改成这里打印的「后端/图片」域名，然后 Retry deployment。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_PUBLIC="${TMPDIR:-/tmp}/aryn-public-tunnel"
AUTO_PAGES="${AUTO_PAGES:-false}"
TUNNELS_CHANGED=0
RUN_IMAGE="${TMPDIR:-/tmp}/aryn-image-tunnel"
MOBILE_DIR="${ROOT_DIR}/aryn-mall-uniapp"
UI_DIR="${ROOT_DIR}/aryn-mall-ui"

ACTION="start"
DO_BUILD=false
WITH_ADMIN=true
WITH_H5=true

log() { printf '[demo-env] %s\n' "$*"; }
warn() { printf '[demo-env] 警告: %s\n' "$*" >&2; }
die() { printf '[demo-env] 错误: %s\n' "$*" >&2; exit 1; }

while (($# > 0)); do
  case "$1" in
    --status) ACTION="status"; shift ;;
    --build) DO_BUILD=true; shift ;;
    --no-admin) WITH_ADMIN=false; shift ;;
    --stop) ACTION="stop"; shift ;;
    --no-h5) WITH_H5=false; shift ;;
    -h|--help) sed -n '2,28p' "$0"; exit 0 ;;
    *) die "未知参数: $1" ;;
  esac
done

curl_code() { curl -s -o /dev/null -w '%{http_code}' --max-time "${2:-5}" "$1" 2>/dev/null || printf '000'; }

# ---------- 状态读取 ----------

state_pid_alive() { # $1=state file
  local pid
  [ -f "$1" ] || return 1
  pid=$(sed -n 's/^CF_PID=//p' "$1" 2>/dev/null | head -1)
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

state_value() { # $1=state file  $2=key
  sed -n "s/^$2=//p" "$1" 2>/dev/null | head -1
}

public_url() { # $1=port
  local f="${RUN_PUBLIC}/port-$1.env" host
  host=$(state_value "$f" CF_HOST)
  printf '%s' "$host"
}

image_url() {
  local host
  host=$(state_value "${RUN_IMAGE}/state.env" CF_HOST)
  [ -n "$host" ] && printf 'https://%s' "$host" || printf ''
}

backend_port_ok() { [ "$(curl_code 'http://127.0.0.1:9999/boot/' 5)" = "200" ] || [ "$(curl_code 'http://127.0.0.1:9999/' 5)" != "000" ]; }

# ---------- 状态汇总 ----------

print_status() {
  echo '──────────────────────────────────────────────'
  log '服务状态'
  local code
  code=$(curl_code 'http://127.0.0.1:9999/')
  printf '  后端 9999      : %s\n' "$([ "$code" != "000" ] && echo "✅ HTTP $code" || echo "❌ 未启动")"
  code=$(curl_code 'http://127.0.0.1:8890/')
  printf '  H5 静态服务 8890: %s\n' "$([ "$code" = "200" ] && echo "✅ HTTP $code" || echo "❌ 未启动")"
  code=$(curl_code 'http://127.0.0.1:5777/')
  printf '  管理端 5777    : %s\n' "$([ "$code" = "200" ] && echo "✅ HTTP $code" || echo "❌ 未启动")"
  echo '──────────────────────────────────────────────'
  log '隧道状态 + 链接'
  local iurl aurl hurl
  iurl=$(image_url)
  if state_pid_alive "${RUN_IMAGE}/state.env"; then log "后端/图片(9999) : ${iurl:-?}"; else warn '后端/图片(9999) : 隧道未运行'; fi
  if $WITH_ADMIN; then
    aurl=$(public_url 5777)
    if state_pid_alive "${RUN_PUBLIC}/port-5777.env"; then log "管理端(5777)    : ${aurl:-?}"; else warn '管理端(5777)    : 隧道未运行'; fi
  fi
  if $WITH_H5; then
    hurl=$(public_url 8890)
    if state_pid_alive "${RUN_PUBLIC}/port-8890.env"; then log "H5(8890)        : ${hurl:-?}"; else warn 'H5(8890)        : 隧道未运行'; fi
  fi
  echo '──────────────────────────────────────────────'
  log '提示：Cloudflare Pages 已部署时，把项目环境变量 BACKEND_URL 改成上面的「后端/图片」域名后 Retry deployment'
}

if [ "$ACTION" = "status" ]; then
  print_status
  exit 0
fi

# 运行锁：避免 launchd 两次执行重叠导致隧道互相覆盖（--status/--stop 不加锁）
LOCK_DIR="${TMPDIR:-/tmp}/aryn-demo-env.lock"
if [ "$ACTION" = "start" ]; then
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    OLD_PID=$(cat "$LOCK_DIR/pid" 2>/dev/null || true)
    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
      log "另一个 start-demo-env.sh 正在运行（pid=${OLD_PID}），本次跳过"
      exit 0
    fi
    rm -rf "$LOCK_DIR"
    mkdir "$LOCK_DIR" 2>/dev/null || die '无法创建运行锁'
  fi
  printf '%s' "$$" > "$LOCK_DIR/pid"
  trap 'rm -rf "$LOCK_DIR" 2>/dev/null || true' EXIT INT TERM
fi

if [ "$ACTION" = "stop" ]; then
  log '停止演示环境…'
  LABEL="com.aryn.demo-env"
  if launchctl print "gui/$(id -u)/$LABEL" >/dev/null 2>&1; then
    launchctl bootout "gui/$(id -u)/$LABEL" >/dev/null 2>&1 || true
    log '已暂停 launchd 自启（需要恢复：./dev-tools/install-demo-launchd.sh install）'
  fi
  "${SCRIPT_DIR}/dev-image-tunnel.sh" --stop >/dev/null 2>&1 || true
  "${SCRIPT_DIR}/dev-public-tunnel.sh" 5777 --stop >/dev/null 2>&1 || true
  "${SCRIPT_DIR}/dev-public-tunnel.sh" 8890 --stop >/dev/null 2>&1 || true
  for port in 8890 5777; do
    pids=$(lsof -ti "tcp:${port}" 2>/dev/null || true)
    [ -n "$pids" ] && kill $pids 2>/dev/null || true
  done
  log '已停止：三条隧道、H5 静态服务(8890)、管理端 dev server(5777)'
  log '后端 Docker 容器未停止（需要时 docker stop aryn-boot）'
  exit 0
fi

# ---------- 0) 可选构建 ----------

if $DO_BUILD; then
  log '构建 H5（demo 模式，API 同源 /api）…'
  ( cd "$MOBILE_DIR" && pnpm build:h5:demo ) || die 'H5 构建失败'
  log '构建管理端静态包…'
  ( cd "$UI_DIR" && pnpm build:ele ) || die '管理端构建失败'
fi

# ---------- 1) 后端 Docker ----------

command -v docker >/dev/null 2>&1 || die '未找到 docker 命令'
docker info >/dev/null 2>&1 || die 'Docker 未运行，请先启动 Docker / OrbStack'

if [ "$(curl_code 'http://127.0.0.1:9999/')" = "000" ]; then
  log '后端未响应，尝试启动后端容器…'
  for c in aryn-boot-mysql aryn-boot-redis aryn-boot-rocketmq-namesrv aryn-boot-rocketmq-broker aryn-boot aryn-boot-xxl-job-admin; do
    if docker inspect "$c" >/dev/null 2>&1; then
      running=$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)
      if [ "$running" != "true" ]; then
        docker start "$c" >/dev/null 2>&1 && log "已启动容器 $c"
      fi
    fi
  done
  local_i=0
  while [ $local_i -lt 60 ]; do
    [ "$(curl_code 'http://127.0.0.1:9999/')" != "000" ] && break
    sleep 2; local_i=$((local_i + 1))
  done
fi
if [ "$(curl_code 'http://127.0.0.1:9999/')" = "000" ]; then
  warn '后端 9999 仍不可用；如容器不存在请先在 aryn-mall-java 下执行 docker compose -f docker-compose-boot.yml up -d'
fi

# ---------- 2) H5 静态演示服务 ----------

if $WITH_H5; then
  [ -f "${MOBILE_DIR}/dist/build/h5/index.html" ] || die '缺少 H5 产物，请先执行：cd aryn-mall-uniapp && pnpm build:h5:demo（或加 --build）'
  if [ "$(curl_code 'http://127.0.0.1:8890/')" != "200" ]; then
    log '启动 H5 静态演示服务 (8890)…'
    nohup node "${SCRIPT_DIR}/h5-demo-server.mjs" --port 8890 >/tmp/h5-demo.log 2>&1 &
    sleep 2
  fi
  [ "$(curl_code 'http://127.0.0.1:8890/')" = "200" ] || warn 'H5 静态服务启动失败，见 /tmp/h5-demo.log'
fi

# ---------- 3) 管理端 dev server ----------

if $WITH_ADMIN; then
  if [ "$(curl_code 'http://127.0.0.1:5777/')" != "200" ]; then
    log '启动管理端 dev server (5777)，首次启动需要 30~90 秒…'
    ( cd "${UI_DIR}" && nohup env BROWSER=none pnpm dev:ele >/tmp/admin-dev.log 2>&1 & )
    local_i=0
    while [ $local_i -lt 90 ]; do
      [ "$(curl_code 'http://127.0.0.1:5777/' 3)" = "200" ] && break
      sleep 2; local_i=$((local_i + 1))
    done
  fi
  [ "$(curl_code 'http://127.0.0.1:5777/')" = "200" ] || warn '管理端 5777 未就绪，见 /tmp/admin-dev.log'
fi

# ---------- 4) 隧道 ----------

if ! state_pid_alive "${RUN_IMAGE}/state.env"; then
  log '启动后端/图片隧道 (9999)，并把数据库图片地址切到新域名…'
  "${SCRIPT_DIR}/dev-image-tunnel.sh" --detach >/tmp/image-tunnel-restart.log 2>&1 \
    || warn '后端/图片隧道启动失败，见 /tmp/image-tunnel-restart.log'
  TUNNELS_CHANGED=1
else
  log '后端/图片隧道已在运行，沿用现有域名'
fi

ensure_public_tunnel() { # $1=port  $2=额外参数
  local port="$1" extra="${2:-}" f="${RUN_PUBLIC}/port-$1.env"
  if state_pid_alive "$f"; then
    log "端口 ${port} 隧道已在运行，沿用现有域名"
    return 0
  fi
  log "启动端口 ${port} 隧道…"
  # shellcheck disable=SC2086
  "${SCRIPT_DIR}/dev-public-tunnel.sh" "$port" --detach $extra >"/tmp/tunnel-${port}-restart.log" 2>&1 \
    || warn "端口 ${port} 隧道启动失败，见 /tmp/tunnel-${port}-restart.log"
  TUNNELS_CHANGED=1
}

if $WITH_ADMIN; then ensure_public_tunnel 5777; fi
if $WITH_H5; then ensure_public_tunnel 8890 "--no-host-rewrite"; fi

# ---------- 5) 汇总 ----------

echo
print_status
# 隧道域名变了且开启了自动发布：更新 Cloudflare Pages 的 /api 代理目标（不重建前端）
if [ "$TUNNELS_CHANGED" = "1" ] && [ "$AUTO_PAGES" = "true" ]; then
  if npx --yes wrangler@latest whoami 2>/dev/null | grep -q 'Account Name'; then
    log '检测到隧道域名变化，自动更新 Cloudflare Pages 代理目标…'
    "${SCRIPT_DIR}/deploy-pages.sh" --skip-build >>"${HOME}/Library/Logs/aryn-demo/pages-deploy.log" 2>&1 \
      && log '已自动更新 Pages 代理目标' \
      || warn '自动更新 Pages 失败，请手动执行 ./dev-tools/deploy-pages.sh --skip-build'
  else
    warn '隧道域名已变化，但未登录 Cloudflare，Pages 代理未更新；请执行 npx wrangler login 后跑 ./dev-tools/deploy-pages.sh --skip-build'
    warn '若还没部署过 Pages，可忽略本提示'
  fi
fi

echo
log '说明：'
log '  1. 以上隧道链接由 Cloudflare Quick Tunnel 生成，进程重启/网络变化后域名会变，重跑本脚本即可；'
log '  2. 进程随终端/会话结束可能被清理，演示期间请保持终端与 Mac 网络在线（可配合 caffeinate -dis）；'
log '  3. H5/管理端已部署 Cloudflare Pages（aryn-h5.pages.dev / aryn-admin.pages.dev）；后端隧道换了域名后执行：./dev-tools/deploy-pages.sh --skip-build'
log '  4. 想彻底固定后端链接：换固定域名的服务器或 Named Tunnel。'
