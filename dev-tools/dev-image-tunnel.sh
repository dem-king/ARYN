#!/usr/bin/env bash
#
# 真机调试图片隧道：把本地 http 图片源映射成受信任的 https 域名。
#
# 背景：微信小程序真机（<image> 组件）只渲染「HTTPS + 受系统信任证书」的图片，
#       本地 http://192.168.x.x:9999 的图片在开发者工具正常、真机全部空白；
#       uni.previewImage 走原生下载通道能看到图，所以很容易误判成数据问题。
#       实测：公网 http、局域网 http、域名指向局域网的 http 全部空白；
#       自签名 https 也不显示；公网受信任 https 正常。
#
# 本脚本用 Cloudflare 免费临时隧道（无需注册）把本机后端映射为
# https://<随机>.trycloudflare.com，并批量把数据库里的图片地址切到该域名，
# 同时清理装修缓存。按 Ctrl+C 或执行 --stop 会把数据库还原成局域网 http 地址。
#
# 用法:
#   ./dev-tools/dev-image-tunnel.sh              # 前台运行：起隧道+改库+清缓存；Ctrl+C 自动还原
#   ./dev-tools/dev-image-tunnel.sh --detach     # 后台运行：起隧道+改库后退出，用 --stop 停止
#   ./dev-tools/dev-image-tunnel.sh --stop       # 停止隧道并把图片地址还原为局域网 http
#   ./dev-tools/dev-image-tunnel.sh --verify     # 只读：查看当前图片主机/隧道状态
#   ./dev-tools/dev-image-tunnel.sh --port 9999  # 后端端口，默认 9999
#
# 环境变量:
#   MYSQL_CTN / MYSQL_USER / MYSQL_PASS   MySQL 容器与账号（默认自动探测）
#   REDIS_CTN / REDIS_PASS                Redis 容器与密码（默认自动探测）
#   ARYN_CLOUDFLARED                      cloudflared 可执行文件路径（默认自动下载到 ~/.cache/aryn/bin）
#
# 注意:
#   - 隧道运行期间本机后端会临时暴露在随机公网域名上，仅用于本地开发；
#   - 临时隧道域名每次启动都会变，重启后重新执行本脚本即可（脚本会替换旧隧道域名）；
#   - 不想用隧道时，图片在开发者工具/预览里仍可用局域网 http（先执行 fix-dev-image-urls.sh）；
#   - 完整说明见 docs/10-开发指南/真机调试与局域网联调.md。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
UNIAPP_ENV="${ROOT_DIR}/aryn-mall-uniapp/.env.development"
RUN_DIR="${TMPDIR:-/tmp}/aryn-image-tunnel"
STATE_FILE="${RUN_DIR}/state.env"
CF_LOG="${RUN_DIR}/cloudflared.log"
PORT="9999"
ACTION="foreground"

CF_PID=""
CF_HOST=""
LAN_IP=""
CLEANED=0

log() { printf '[image-tunnel] %s\n' "$*"; }
warn() { printf '[image-tunnel] 警告: %s\n' "$*" >&2; }
die() { printf '[image-tunnel] 错误: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '2,42p' "$0"; }

while (($# > 0)); do
  case "$1" in
    --port) PORT="${2:-}"; shift 2 ;;
    --detach) ACTION="detach"; shift ;;
    --stop|--revert) ACTION="stop"; shift ;;
    --verify) ACTION="verify"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（--help 查看用法）" ;;
  esac
done

[[ "$PORT" =~ ^[0-9]+$ ]] || die "端口不合法: ${PORT}"

# ---------- 基础探测 ----------

detect_lan_ip() {
  local ip="" iface=""
  case "$(uname -s)" in
    Darwin)
      for iface in en0 en1 en2; do
        ip=$(ipconfig getifaddr "$iface" 2>/dev/null || true)
        [ -n "$ip" ] && { printf '%s' "$ip"; return 0; }
      done
      iface=$(route -n get default 2>/dev/null | awk '/interface:/{print $2; exit}')
      [ -n "$iface" ] && ip=$(ipconfig getifaddr "$iface" 2>/dev/null || true)
      ;;
    *)
      ip=$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") {print $(i + 1); exit}}')
      [ -z "$ip" ] && ip=$(hostname -I 2>/dev/null | awk '{print $1}')
      ;;
  esac
  printf '%s' "$ip"
}

detect_container() { # $1=候选容器名（空格分隔）  $2=环境变量值  $3=模糊匹配关键字
  local candidates="$1" override="$2" names cand
  if [ -n "$override" ]; then
    printf '%s' "$override"
    return 0
  fi
  names=$(docker ps --format '{{.Names}}' 2>/dev/null || true)
  for cand in $candidates; do
    if printf '%s\n' "$names" | grep -qx "$cand"; then
      printf '%s' "$cand"
      return 0
    fi
  done
  printf '%s\n' "$names" | grep -i -- "${3:-}" | head -1
}

command -v docker >/dev/null 2>&1 || die "未找到 docker 命令"
docker info >/dev/null 2>&1 || die "Docker 未运行，请先启动 Docker / OrbStack"

MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
MYSQL_CTN="$(detect_container 'aryn-boot-mysql aryn-mysql mysql-aryn aryn-mall-mysql' "${MYSQL_CTN:-}" mysql)"
[ -n "${MYSQL_CTN}" ] || die "未找到运行中的 MySQL 容器，可用 MYSQL_CTN=容器名 指定"
docker inspect "$MYSQL_CTN" >/dev/null 2>&1 || die "容器不存在: ${MYSQL_CTN}"
if [ -z "$MYSQL_PASS" ]; then
  MYSQL_PASS=$(docker inspect "$MYSQL_CTN" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | sed -n 's/^MYSQL_ROOT_PASSWORD=//p' | head -1)
fi
MYSQL_PASS="${MYSQL_PASS:-123456}"

mysql_q() {
  docker exec -i "$MYSQL_CTN" mysql --default-character-set=utf8mb4 -N -B -r \
    -u"$MYSQL_USER" -p"$MYSQL_PASS" -e "$1" 2>/dev/null
}
mysql_run_db() {
  docker exec -i "$MYSQL_CTN" mysql --default-character-set=utf8mb4 \
    -u"$MYSQL_USER" -p"$MYSQL_PASS" "$1" 2>&1 | grep -v 'Using a password' || true
}

[ -n "$(mysql_q 'SELECT 1;')" ] || die "无法连接 MySQL（容器 ${MYSQL_CTN}，用户 ${MYSQL_USER}）"
DBS=$(mysql_q 'SHOW DATABASES;' | grep -E '^aryn_' || true)
[ -n "$DBS" ] || die "未发现 aryn_* 数据库（容器 ${MYSQL_CTN}）"

LAN_IP="$(detect_lan_ip)"
[ -n "$LAN_IP" ] || die "无法自动探测局域网 IP，请检查网络"
LAN_HTTP="http://${LAN_IP}:${PORT}"

# 图片列：与 fix-dev-image-urls.sh 保持同一口径（装修 JSON / payload / 快照等文本列都要覆盖）
COLUMN_SQL="SELECT CONCAT(table_name, CHAR(9), column_name)
FROM information_schema.columns
WHERE table_schema = '%s'
  AND data_type IN ('char', 'varchar', 'text', 'mediumtext', 'longtext', 'json')
  AND column_name REGEXP '(^|_)(url|uri|pic|image|img|avatar|logo|icon|domain|content|payload|snapshot|backup|voucher)(s|_|\$)'
ORDER BY table_name, ordinal_position;"

HTTP_DEV_RE="http://(localhost|127[.]0[.]0[.]1|10[.][0-9]{1,3}[.][0-9]{1,3}[.][0-9]{1,3}|192[.]168[.][0-9]{1,3}[.][0-9]{1,3}|172[.](1[6-9]|2[0-9]|3[01])[.][0-9]{1,3}[.][0-9]{1,3}):${PORT}"
TUNNEL_RE="https://[a-zA-Z0-9-]+[.]trycloudflare[.]com"

sql_quote() { printf "'%s'" "$1"; }

list_columns() { # $1=db
  mysql_q "$(printf "$COLUMN_SQL" "$1")"
}

gen_apply_sql() { # $1=db  $2=隧道地址
  local db="$1" target="$2" t c
  while IFS=$'\t' read -r t c; do
    [ -n "$t" ] || continue
    printf 'UPDATE `%s`.`%s` SET `%s` = REGEXP_REPLACE(`%s`, %s, %s) WHERE `%s` REGEXP %s;\n' \
      "$db" "$t" "$c" "$c" "$(sql_quote "$HTTP_DEV_RE")" "$(sql_quote "$target")" "$c" "$(sql_quote "$HTTP_DEV_RE")"
    printf 'UPDATE `%s`.`%s` SET `%s` = REGEXP_REPLACE(`%s`, %s, %s) WHERE `%s` REGEXP %s;\n' \
      "$db" "$t" "$c" "$c" "$(sql_quote "$TUNNEL_RE")" "$(sql_quote "$target")" "$c" "$(sql_quote "$TUNNEL_RE")"
  done <<< "$(list_columns "$db")"
}

gen_revert_sql() { # $1=db
  local db="$1" t c
  while IFS=$'\t' read -r t c; do
    [ -n "$t" ] || continue
    printf 'UPDATE `%s`.`%s` SET `%s` = REGEXP_REPLACE(`%s`, %s, %s) WHERE `%s` REGEXP %s;\n' \
      "$db" "$t" "$c" "$c" "$(sql_quote "$TUNNEL_RE")" "$(sql_quote "$LAN_HTTP")" "$c" "$(sql_quote "$TUNNEL_RE")"
  done <<< "$(list_columns "$db")"
}

apply_hosts() { # $1=隧道地址
  local target="$1" db sql
  for db in $DBS; do
    sql=$(gen_apply_sql "$db" "$target")
    [ -n "$sql" ] || continue
    printf '%s\n' "$sql" | mysql_run_db "$db" >/dev/null
  done
}

revert_hosts() {
  local db sql
  for db in $DBS; do
    sql=$(gen_revert_sql "$db")
    [ -n "$sql" ] || continue
    printf '%s\n' "$sql" | mysql_run_db "$db" >/dev/null
  done
}

# ---------- Redis 图片缓存 ----------

REDIS_CTN="$(detect_container 'aryn-boot-redis aryn-redis redis-aryn aryn-mall-redis' "${REDIS_CTN:-}" redis)"
REDIS_PASS="${REDIS_PASS:-}"
if [ -n "$REDIS_CTN" ] && [ -z "$REDIS_PASS" ]; then
  REDIS_PASS=$(docker inspect "$REDIS_CTN" --format '{{json .Config.Cmd}}' 2>/dev/null \
    | sed -n 's/.*"--requirepass","\([^"]*\)".*/\1/p')
fi
REDIS_PASS="${REDIS_PASS:-redis}"

redis_q() {
  local db="$1"
  shift
  docker exec -i "$REDIS_CTN" redis-cli -a "$REDIS_PASS" -n "$db" "$@" 2>/dev/null | grep -v 'Using a password' || true
}

clear_image_caches() {
  local patterns="page_design_cache:* home_page_design_cache:* order_config_cache:*"
  local db pattern keys key deleted=0
  [ -n "$REDIS_CTN" ] || { warn "未找到 Redis 容器，跳过图片缓存清理"; return 0; }
  for db in $(seq 0 15); do
    for pattern in $patterns; do
      keys=$(redis_q "$db" --scan --pattern "$pattern")
      [ -n "$keys" ] || continue
      while IFS= read -r key; do
        [ -n "$key" ] || continue
        redis_q "$db" DEL "$key" >/dev/null
        deleted=$((deleted + 1))
      done <<< "$keys"
    done
  done
  log "已清理 Redis 图片缓存键 ${deleted} 个"
}

# 清理 Redis 里仍指向「旧隧道域名」的图片缓存（切换隧道后常见，否则接口会返回死链图片）
purge_stale_tunnel_cache() { # $1=当前隧道地址
  local current="$1" db k v cur_host
  [ -n "$REDIS_CTN" ] || return 0
  cur_host=$(printf '%s' "$current" | sed -E 's#^https?://##')
  for db in $(seq 0 15); do
    for k in $(redis_q "$db" --scan --pattern '*design*') $(redis_q "$db" --scan --pattern '*config_cache*'); do
      [ -n "$k" ] || continue
      v=$(redis_q "$db" GET "$k")
      printf '%s' "$v" | grep -q 'trycloudflare[.]com' || continue
      printf '%s' "$v" | grep -q "$cur_host" && continue
      redis_q "$db" DEL "$k" >/dev/null
      log "清理残留旧隧道缓存：$k"
    done
  done
}

# ---------- cloudflared ----------

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

  # 缓存目录按可写性挑：优先 ~/.cache/aryn/bin（常规），失败则退回临时目录
  local dir candidates
  candidates="${ARYN_BIN_DIR:-} $HOME/.cache/aryn/bin ${TMPDIR:-/tmp}/aryn/bin"
  for dir in $candidates; do
    [ -n "$dir" ] || continue
    if mkdir -p "$dir" 2>/dev/null && [ -w "$dir" ]; then
      CF_BIN="$dir/cloudflared"
      break
    fi
  done
  [ -n "${CF_BIN:-}" ] || die "找不到可写的 cloudflared 缓存目录（可用 ARYN_BIN_DIR 指定）"
  [ -x "$CF_BIN" ] && return 0

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
  curl -fL --retry 2 --connect-timeout 15 -o "$tmpdir/download" "$url" || die "cloudflared 下载失败（可 brew install cloudflared 或设置 ARYN_CLOUDFLARED）"
  case "$file" in
    *.tgz) tar -xzf "$tmpdir/download" -C "$tmpdir" && mv "$tmpdir/cloudflared" "$CF_BIN" ;;
    *) mv "$tmpdir/download" "$CF_BIN" ;;
  esac
  rm -rf "$tmpdir"
  chmod +x "$CF_BIN" || die "无法写入 cloudflared: $CF_BIN"
  log "cloudflared 已就绪：${CF_BIN}"
}

start_tunnel() {
  ensure_cloudflared
  mkdir -p "$RUN_DIR"
  : > "$CF_LOG"
  nohup "$CF_BIN" tunnel --url "http://127.0.0.1:${PORT}" --no-autoupdate >"$CF_LOG" 2>&1 &
  CF_PID=$!

  local i
  for i in $(seq 1 40); do
    # 失败日志里会出现 https://api.trycloudflare.com/tunnel，必须排除；
    # 快速隧道域名一定带连字符，api.trycloudflare.com 没有连字符。
    CF_HOST=$(grep -oE "https://[a-zA-Z0-9-]+[.]trycloudflare[.]com" "$CF_LOG" 2>/dev/null \
      | grep -v "/api[.]trycloudflare[.]com" | grep -E -- "-" | head -1)
    [ -n "$CF_HOST" ] && break
    if grep -q "failed to request quick Tunnel" "$CF_LOG" 2>/dev/null; then
      kill "$CF_PID" 2>/dev/null || true
      die "隧道创建失败（Cloudflare API 超时，稍后重试），日志见 ${CF_LOG}"
    fi
    kill -0 "$CF_PID" 2>/dev/null || die "cloudflared 启动失败，日志见 ${CF_LOG}"
    sleep 1
  done
  if [ -z "$CF_HOST" ]; then
    kill "$CF_PID" 2>/dev/null || true
    die "等待隧道地址超时，日志见 ${CF_LOG}"
  fi
  TUNNEL_BASE="$CF_HOST"
  log "隧道已就绪：${TUNNEL_BASE}"
  # 临时隧道刚创建时 Cloudflare 边缘路由需要几秒生效，稍等再改库/自检
  sleep 3
}

# ---------- 校验与提示 ----------

verify_sample_image() {
  local sample code host doh_ip
  # 优先找已经指向隧道域名的商品图，确认这条 https 链路真的能回源
  sample=$(mysql_q "SELECT spu_urls FROM \`aryn_boot\`.\`goods_spu\` WHERE spu_urls LIKE '%trycloudflare.com%' AND del_flag = '0' LIMIT 1;" 2>/dev/null)
  if [ -z "$sample" ]; then
    sample=$(mysql_q "SELECT spu_urls FROM \`aryn_boot\`.\`goods_spu\` WHERE spu_urls <> '' AND del_flag = '0' LIMIT 1;" 2>/dev/null)
  fi
  [ -n "$sample" ] || return 0
  sample=$(printf '%s' "$sample" | cut -d',' -f1)
  host=$(printf '%s' "$sample" | sed -E 's#^https?://([^/]+).*#\1#')
  local attempt
  code=000
  for attempt in 1 2 3; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$sample" 2>/dev/null)
    [ -n "$code" ] || code=000
    [ "$code" = "200" ] && break
    # 本机系统代理/DNS 可能拦截刚创建的新域名（mock-IP），用 DoH 取真实 IP 再测
    doh_ip=$(curl -s --max-time 5 -H 'accept: application/dns-json' \
      "https://1.1.1.1/dns-query?name=${host}&type=A" 2>/dev/null \
      | sed -n 's/.*"data":"\([0-9.]*\)".*/\1/p' | head -1)
    if [ -n "$doh_ip" ]; then
      code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 --resolve "${host}:443:${doh_ip}" "$sample" 2>/dev/null)
      [ -n "$code" ] || code=000
      [ "$code" = "200" ] && break
    fi
    sleep 4
  done
  if [ "$code" = "200" ]; then
    log "样例图片回源 HTTP 200：${sample}"
  else
    warn "样例图片本机自检 HTTP ${code}（若本机开了代理，可能只是代理拦截新域名；以手机实测为准）：${sample}"
  fi
}

check_api_env() {
  [ -f "$UNIAPP_ENV" ] || return 0
  local api
  api=$(sed -n 's/^[[:space:]]*VITE_API_BASE_URL[[:space:]]*=[[:space:]]*//p' "$UNIAPP_ENV" | head -1 | tr -d '[:space:]')
  [ -n "$api" ] || return 0
  log "接口地址（保持局域网 http 即可）：${api}"
}

current_image_hosts() {
  local spu domain design tunnel_count lan_count
  spu=$(mysql_q "SELECT spu_urls FROM \`aryn_boot\`.\`goods_spu\` WHERE spu_urls <> '' AND del_flag = '0' LIMIT 1;" 2>/dev/null | cut -d',' -f1)
  domain=$(mysql_q "SELECT domain FROM \`aryn_boot\`.\`sys_storage_config\` WHERE domain IS NOT NULL AND domain <> '' LIMIT 1;" 2>/dev/null)
  design=$(mysql_q "SELECT page_content FROM \`aryn_boot\`.\`page_design_release\` ORDER BY update_time DESC LIMIT 1;" 2>/dev/null | grep -oE "https?://[^\"\\\\ ]+" | head -1)
  tunnel_count=$(mysql_q "SELECT COUNT(*) FROM \`aryn_boot\`.\`goods_spu\` WHERE spu_urls LIKE '%trycloudflare.com%' AND del_flag = '0';" 2>/dev/null)
  lan_count=$(mysql_q "SELECT COUNT(*) FROM \`aryn_boot\`.\`goods_spu\` WHERE spu_urls LIKE '%192.168.%' AND del_flag = '0';" 2>/dev/null)
  printf '  商品图样例: %s\n' "${spu:-<空>}"
  printf '  装修图样例: %s\n' "${design:-<空>}"
  printf '  存储 domain: %s\n' "${domain:-<空>}"
  printf '  商品图统计: 隧道 %s 行 / 局域网 %s 行\n' "${tunnel_count:-?}" "${lan_count:-?}"
}

print_next_steps() {
  cat <<EOF
[image-tunnel] 真机验证步骤：
  1. 手机与电脑保持同一 Wi-Fi；
  2. 杀掉小程序 / 重新点「真机调试」，让页面重新拉取接口数据；
  3. 图片现在来自受信任的 https 隧道，真机应能正常显示。
[image-tunnel] 停止方式：
  前台模式按 Ctrl+C；后台模式执行 ./dev-tools/dev-image-tunnel.sh --stop
EOF
}

# ---------- 还原 ----------

cleanup_and_revert() {
  [ "$CLEANED" = "1" ] && return 0
  CLEANED=1
  revert_hosts
  clear_image_caches
  if [ -n "$CF_PID" ] && kill -0 "$CF_PID" 2>/dev/null; then
    kill "$CF_PID" 2>/dev/null || true
  fi
  rm -rf "$RUN_DIR"
  log "已还原图片地址为 ${LAN_HTTP}，隧道已关闭"
}

stop_existing() {
  if [ -f "$STATE_FILE" ]; then
    local old_pid
    old_pid=$(sed -n 's/^CF_PID=//p' "$STATE_FILE" 2>/dev/null | head -1)
    if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
      kill "$old_pid" 2>/dev/null || true
      log "已停止上次的后台隧道（pid=${old_pid}）"
    fi
    rm -rf "$RUN_DIR"
  fi
}

# ---------- 主流程 ----------

case "$ACTION" in
  verify)
    log "当前状态（只读）：LAN=${LAN_HTTP}  库=${DBS//$'\n'/, }"
    current_image_hosts
    if [ -f "$STATE_FILE" ]; then
      STATE_PID=$(sed -n 's/^CF_PID=//p' "$STATE_FILE" 2>/dev/null | head -1)
      STATE_HOST=$(sed -n 's/^CF_HOST=//p' "$STATE_FILE" 2>/dev/null | head -1)
      log "后台隧道记录：host=${STATE_HOST:-<无>} pid=${STATE_PID:-<无>}"
      if [ -n "$STATE_PID" ] && kill -0 "$STATE_PID" 2>/dev/null; then
        log "隧道进程存活"
      else
        warn "隧道进程已不存在，执行 --stop 可还原图片地址"
      fi
    else
      log "未发现后台隧道记录（当前可能是局域网 http 状态）"
    fi
    check_api_env
    verify_sample_image
    exit 0
    ;;
  stop)
    log "停止隧道并还原图片地址…"
    stop_existing
    cleanup_and_revert
    current_image_hosts
    exit 0
    ;;
esac

# 启动前先清掉遗留的后台隧道，避免同一台机器上多个 cloudflared 争用
stop_existing
CF_PID=""

start_tunnel
apply_hosts "$TUNNEL_BASE"
# 等 2s 让切换瞬间在途的请求落完，再清缓存，避免旧域名被重新写回
sleep 2
clear_image_caches
purge_stale_tunnel_cache "$TUNNEL_BASE"
log "已把图片地址切换到 ${TUNNEL_BASE}（含 sys_storage_config.domain）"
check_api_env
verify_sample_image

if [ "$ACTION" = "detach" ]; then
  mkdir -p "$RUN_DIR"
  {
    printf 'CF_PID=%s\n' "$CF_PID"
    printf 'CF_HOST=%s\n' "${CF_HOST#https://}"
    printf 'TUNNEL_BASE=%s\n' "$TUNNEL_BASE"
    printf 'LAN_HTTP=%s\n' "$LAN_HTTP"
    printf 'PORT=%s\n' "$PORT"
  } > "$STATE_FILE"
  disown "$CF_PID" 2>/dev/null || true
  CLEANED=1
  print_next_steps
  log "后台隧道已启动（pid=${CF_PID}），停止请执行：./dev-tools/dev-image-tunnel.sh --stop"
  exit 0
fi

trap 'cleanup_and_revert; exit 0' INT TERM
trap 'cleanup_and_revert' EXIT
print_next_steps
log "前台模式运行中，按 Ctrl+C 停止并自动还原图片地址"
wait "$CF_PID"
