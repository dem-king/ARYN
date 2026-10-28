#!/usr/bin/env bash
#
# 本地图片地址修复：把开发库里写死的 localhost 图片地址换成当前局域网 IP，
# 并清理首页装修等图片缓存。
#
# 注意：微信真机 <image> 只渲染「HTTPS + 受系统信任证书」的图片，http 一律空白
#       （开发者工具和 uni.previewImage 可以显示 http）。本脚本解决的是开发者工具、
#       预览、接口数据层的 localhost 问题；真机页面要看图片请先跑：
#       ./dev-tools/dev-image-tunnel.sh（把图片切到受信任 https 隧道）。
#
# 现象：微信开发者工具里图片正常，「真机调试」时接口能通但所有图片空白。
# 原因：本地文件存储的回源地址（sys_storage_config.domain）和种子数据把
#       http://localhost:9999 这个绝对地址直接写进了数据库 —— 开发者工具和后端
#       在同一台电脑上，localhost 可达；真机在局域网里，手机的 localhost 指向
#       手机自己，所以 image 组件的请求全部失败。
#
# 本脚本扫描 aryn_* 库中所有可能存放 URL 的文本/JSON 列，把开发机回源主机
# （localhost / 127.0.0.1 / 10.x / 192.168.x / 172.16-31.x）统一替换为当前
# 局域网 IP，同步 sys_storage_config.domain（之后上传的新图同样生效），并删除
# Redis 里缓存了旧图片地址的 page_design_cache / order_config_cache 等键。
# 只影响本地开发库，不触碰生产域名。
#
# 用法:
#   ./dev-tools/fix-dev-image-urls.sh                  # 探测本机局域网 IP 并批量替换
#   ./dev-tools/fix-dev-image-urls.sh --verify         # 只读校验，列出仍有开发机地址的列
#   ./dev-tools/fix-dev-image-urls.sh --dry-run        # 只打印统计与将要执行的 SQL
#   ./dev-tools/fix-dev-image-urls.sh --revert         # 还原成 http://localhost:9999
#   ./dev-tools/fix-dev-image-urls.sh --keep-cache     # 不清理 Redis 图片缓存
#   ./dev-tools/fix-dev-image-urls.sh --host 10.0.0.8  # 手动指定回源主机（默认自动探测）
#   ./dev-tools/fix-dev-image-urls.sh --port 9999      # 后端端口，默认 9999
#
# 环境变量:
#   MYSQL_CTN   MySQL 容器名，默认自动探测 aryn-boot-mysql / aryn-mysql
#   MYSQL_USER  MySQL 用户，默认 root
#   MYSQL_PASS  MySQL 密码，默认取容器的 MYSQL_ROOT_PASSWORD，回退 123456
#   REDIS_CTN   Redis 容器名，默认自动探测 aryn-boot-redis / aryn-redis
#   REDIS_PASS  Redis 密码，默认取容器 --requirepass，回退 redis
#
# 注意:
#   - 幂等，可重复执行；电脑 IP 变化后再跑一次即可（会匹配上一台机器的私有网段）。
#   - 重跑 db/boot 或 db/cloud 下的图片种子脚本会把地址写回 localhost:9999，
#     之后需要再执行本脚本（见 docs/10-开发指南/真机调试与局域网联调.md）。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
UNIAPP_ENV="${ROOT_DIR}/aryn-mall-uniapp/.env.development"

MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
TARGET_HOST=""
TARGET_PORT="9999"
ACTION="apply"
CLEAR_CACHE=true

log() { printf '[image-url] %s\n' "$*"; }
warn() { printf '[image-url] 警告: %s\n' "$*" >&2; }
die() { printf '[image-url] 错误: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '2,44p' "$0"; }

while (($# > 0)); do
  case "$1" in
    --host) TARGET_HOST="${2:-}"; shift 2 ;;
    --port) TARGET_PORT="${2:-}"; shift 2 ;;
    --verify) ACTION="verify"; shift ;;
    --dry-run) ACTION="dry-run"; shift ;;
    --revert) ACTION="revert"; shift ;;
    --keep-cache) CLEAR_CACHE=false; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（--help 查看用法）" ;;
  esac
done

[[ "$TARGET_PORT" =~ ^[0-9]+$ ]] || die "端口不合法: ${TARGET_PORT}"

# ---------- 1. 探测回源主机 / Docker / MySQL 连接 ----------

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

if [ -z "$TARGET_HOST" ]; then
  TARGET_HOST="$(detect_lan_ip)"
  [ -n "$TARGET_HOST" ] || die "无法自动探测局域网 IP，请用 --host 指定，例如 --host 192.168.3.14"
fi

command -v docker >/dev/null 2>&1 || die "未找到 docker 命令"
docker info >/dev/null 2>&1 || die "Docker 未运行，请先启动 Docker / OrbStack"

detect_container() { # $1=候选容器名（空格分隔）  $2=环境变量值
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

MYSQL_CTN="$(detect_container 'aryn-boot-mysql aryn-mysql mysql-aryn aryn-mall-mysql' "${MYSQL_CTN:-}" mysql)"
[ -n "${MYSQL_CTN}" ] || die "未找到运行中的 MySQL 容器，可用 MYSQL_CTN=容器名 指定"
docker inspect "$MYSQL_CTN" >/dev/null 2>&1 || die "容器不存在: ${MYSQL_CTN}"

if [ -z "$MYSQL_PASS" ]; then
  MYSQL_PASS=$(docker inspect "$MYSQL_CTN" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | sed -n 's/^MYSQL_ROOT_PASSWORD=//p' | head -1)
fi
MYSQL_PASS="${MYSQL_PASS:-123456}"

mysql_q() {
  # -r（raw）必须加：否则 -B 会把值里的制表符转义成字面量 \t，列名拆不开
  docker exec -i "$MYSQL_CTN" mysql --default-character-set=utf8mb4 -N -B -r \
    -u"$MYSQL_USER" -p"$MYSQL_PASS" -e "$1" 2>/dev/null
}

mysql_run_db() {
  docker exec -i "$MYSQL_CTN" mysql --default-character-set=utf8mb4 \
    -u"$MYSQL_USER" -p"$MYSQL_PASS" "$1" 2>&1 | grep -v 'Using a password' || true
}

[ -n "$(mysql_q 'SELECT 1;')" ] || die "无法连接 MySQL（容器 ${MYSQL_CTN}，用户 ${MYSQL_USER}），可用 MYSQL_USER / MYSQL_PASS 覆盖"

DBS=$(mysql_q 'SHOW DATABASES;' | grep -E '^aryn_' || true)
[ -n "$DBS" ] || die "未发现 aryn_* 数据库（容器 ${MYSQL_CTN}）"

# ---------- 2. 匹配规则 ----------

TARGET_BASE="http://${TARGET_HOST}:${TARGET_PORT}"
TARGET_HOST_RE=$(printf '%s' "$TARGET_HOST" | sed 's/[.]/[.]/g')
TARGET_RE="http://${TARGET_HOST_RE}:${TARGET_PORT}"

# 需要修复的「开发机回源地址」：localhost / 回环 / 三类私有网段，但排除已经是目标
# 局域网 IP 的地址（否则替换后再统计会把自己算成残留）。用 [.] [0-9] 书写，避免
# MySQL 字符串字面量的反斜杠转义问题；负向前瞻用于排除目标主机。
DEV_HOST_RE="http://(?!${TARGET_HOST_RE}:${TARGET_PORT})(localhost|127[.]0[.]0[.]1|10[.][0-9]{1,3}[.][0-9]{1,3}[.][0-9]{1,3}|192[.]168[.][0-9]{1,3}[.][0-9]{1,3}|172[.](1[6-9]|2[0-9]|3[01])[.][0-9]{1,3}[.][0-9]{1,3}):${TARGET_PORT}"

# 可能存放 URL 的列：列名像 url/pic/image/…，或装载装修 JSON、消息 payload、快照的文本列。
# 用 (^|_)…(s|_|$) 卡边界，避免 picked / picked_quantity 这类无关列被卷进来。
COLUMN_SQL="SELECT CONCAT(table_name, CHAR(9), column_name)
FROM information_schema.columns
WHERE table_schema = '%s'
  AND data_type IN ('char', 'varchar', 'text', 'mediumtext', 'longtext', 'json')
  AND column_name REGEXP '(^|_)(url|uri|pic|image|img|avatar|logo|icon|domain|content|payload|snapshot|backup|voucher)(s|_|\$)'
ORDER BY table_name, ordinal_position;"

sql_quote() { printf "'%s'" "$1"; }

list_columns() { # $1=db
  mysql_q "$(printf "$COLUMN_SQL" "$1")"
}

# 每列一条 COUNT，产出 "库.表.列\t命中行数"。
gen_scan_sql() { # $1=db  $2=正则
  local db="$1" re="$2" first=1 t c
  while IFS=$'\t' read -r t c; do
    [ -n "$t" ] || continue
    [ "$first" -eq 1 ] || printf ' UNION ALL '
    first=0
    printf "SELECT %s, COUNT(*) FROM \`%s\`.\`%s\` WHERE \`%s\` REGEXP %s" \
      "$(sql_quote "${db}.${t}.${c}")" "$db" "$t" "$c" "$(sql_quote "$re")"
  done <<< "$(list_columns "$db")"
  [ "$first" -eq 0 ] && printf ';\n'
}

scan_matches() { # $1=正则  → 输出 "库.表.列\t行数"
  local db out
  for db in $DBS; do
    out=$(mysql_q "$(gen_scan_sql "$db" "$1")")
    [ -n "$out" ] || continue
    printf '%s\n' "$out" | awk -F'\t' '$2 > 0'
  done
}

gen_update_sql() { # $1=db  $2=匹配正则  $3=替换值（REGEXP_REPLACE）
  local db="$1" re="$2" repl="$3" t c
  while IFS=$'\t' read -r t c; do
    [ -n "$t" ] || continue
    printf 'UPDATE `%s`.`%s` SET `%s` = REGEXP_REPLACE(`%s`, %s, %s) WHERE `%s` REGEXP %s;\n' \
      "$db" "$t" "$c" "$c" "$(sql_quote "$re")" "$(sql_quote "$repl")" "$c" "$(sql_quote "$re")"
  done <<< "$(list_columns "$db")"
}

gen_revert_sql() { # $1=db → 把当前回源地址还原成 localhost:9999
  local db="$1" t c from="http://${TARGET_HOST}:${TARGET_PORT}" to="http://localhost:${TARGET_PORT}"
  while IFS=$'\t' read -r t c; do
    [ -n "$t" ] || continue
    printf 'UPDATE `%s`.`%s` SET `%s` = REPLACE(`%s`, %s, %s) WHERE `%s` LIKE %s;\n' \
      "$db" "$t" "$c" "$c" "$(sql_quote "$from")" "$(sql_quote "$to")" "$c" "$(sql_quote "%${from}%")"
  done <<< "$(list_columns "$db")"
}

# ---------- 3. 统计与执行 ----------

count_matches() { # $1=正则 → 总命中行数
  scan_matches "$1" | awk -F'\t' '{s += $2} END {print s + 0}'
}

print_matches() { # $1=正则
  local out
  out=$(scan_matches "$1")
  if [ -z "$out" ]; then
    log "没有需要修复的开发机回源地址"
    return 0
  fi
  printf '%s\n' "$out" | while IFS=$'\t' read -r key n; do
    printf '  %-58s %s 行\n' "$key" "$n"
  done
}

verify_sample_image() {
  local db sample code
  for db in $DBS; do
    [ "$(mysql_q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${db}' AND table_name = 'goods_spu';")" = "1" ] || continue
    sample=$(mysql_q "SELECT spu_urls FROM \`${db}\`.\`goods_spu\` WHERE spu_urls <> '' AND del_flag = '0' LIMIT 1;")
    [ -n "$sample" ] || continue
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$sample" 2>/dev/null || printf '000')
    if [ "${sample#"$TARGET_BASE"}" != "$sample" ]; then
      log "样例图片（${TARGET_BASE}）HTTP ${code}：${sample}"
      if [ "$code" = "200" ]; then
        log "该地址已能从局域网访问，手机连同一 Wi-Fi 即可加载"
      else
        warn "后端文件回源失败（HTTP ${code}），检查文件是否已铺进存储目录、后端是否在跑"
      fi
    else
      warn "样例图片仍是开发机本地地址，真机不可达：${sample}"
      log "本机回源 HTTP ${code}（只能证明后端文件存在，不代表手机可达）"
    fi
    return 0
  done
}

check_api_env() {
  [ -f "$UNIAPP_ENV" ] || return 0
  local api host
  api=$(sed -n 's/^[[:space:]]*VITE_API_BASE_URL[[:space:]]*=[[:space:]]*//p' "$UNIAPP_ENV" | head -1 | tr -d '[:space:]')
  [ -n "$api" ] || return 0
  host=${api#*://}
  host=${host%%/*}
  host=${host%%:*}
  if [ "$host" != "$TARGET_HOST" ]; then
    warn "接口地址与图片回源主机不一致：VITE_API_BASE_URL=${api}，图片回源=${TARGET_BASE}"
    warn "请把 aryn-mall-uniapp/.env.development 的 VITE_API_BASE_URL 改为 ${TARGET_BASE}，否则真机接口也会失败"
  else
    log "接口地址校验通过：VITE_API_BASE_URL=${api}"
  fi
}

# ---------- 4. Redis 图片缓存清理 ----------

REDIS_CTN="$(detect_container 'aryn-boot-redis aryn-redis redis-aryn aryn-mall-redis' "${REDIS_CTN:-}" redis)"
REDIS_PASS="${REDIS_PASS:-}"
if [ -n "$REDIS_CTN" ] && [ -z "$REDIS_PASS" ]; then
  REDIS_PASS=$(docker inspect "$REDIS_CTN" --format '{{json .Config.Cmd}}' 2>/dev/null \
    | sed -n 's/.*"--requirepass","\([^"]*\)".*/\1/p')
fi
REDIS_PASS="${REDIS_PASS:-redis}"

redis_q() { # $1=db number, 其余为 redis-cli 参数
  local db="$1"
  shift
  docker exec -i "$REDIS_CTN" redis-cli -a "$REDIS_PASS" -n "$db" "$@" 2>/dev/null | grep -v 'Using a password' || true
}

clear_image_caches() {
  # 首页装修按版本缓存（TTL 数小时），不清缓存时改了库手机依然看到旧地址。
  local patterns="page_design_cache:* home_page_design_cache:* order_config_cache:*"
  local db pattern keys key deleted=0
  if [ -z "$REDIS_CTN" ]; then
    warn "未找到运行中的 Redis 容器，跳过图片缓存清理；必要时手动删除 page_design_cache:*"
    return 0
  fi
  if ! redis_q 0 PING >/dev/null 2>&1; then
    warn "Redis 连接失败（容器 ${REDIS_CTN}），跳过缓存清理，可用 --keep-cache 静默跳过"
    return 0
  fi
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
  log "已清理 Redis 图片相关缓存键 ${deleted} 个（page_design_cache / home_page_design_cache / order_config_cache）"
}

print_wechat_tips() {
  cat <<'EOF'
[image-url] 真机验证步骤：
  1. 微信开发者工具「详情 → 本地设置」勾选「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」；
  2. 手机与电脑连同一个 Wi-Fi，真机调试的调试模式保持开启（手机端右上角 ... → 开发调试 可见 vConsole）；
  3. 重新编译并点「真机调试」，图片应从 http://<局域网IP>:9999 正常回源。
EOF
}

if [ "$ACTION" = "verify" ]; then
  log "只读校验：库=${DBS//$'\n'/, }  目标=${TARGET_BASE}"
  PENDING="$(count_matches "$DEV_HOST_RE")"
  print_matches "$DEV_HOST_RE"
  log "待修复 ${PENDING} 行；已指向 ${TARGET_BASE} 的行数：$(count_matches "$TARGET_RE")"
  check_api_env
  verify_sample_image
  [ "$PENDING" = "0" ] || exit 1
  exit 0
fi

if [ "$ACTION" = "dry-run" ]; then
  log "预演（不写库）：目标=${TARGET_BASE}  库=${DBS//$'\n'/, }"
  print_matches "$DEV_HOST_RE"
  log "将要执行的 SQL 预览（每库前 5 条）："
  for db in $DBS; do
    printf '%s\n' "$(gen_update_sql "$db" "$DEV_HOST_RE" "$TARGET_BASE" | head -5)" | sed '/^$/d' | sed 's/^/  /'
  done
  exit 0
fi

log "目标回源地址：${TARGET_BASE}"

if [ "$ACTION" = "revert" ]; then
  BEFORE="$(count_matches "$TARGET_RE")"
  log "待还原（指向 ${TARGET_BASE}）行数：${BEFORE}"
  if [ "$BEFORE" = "0" ]; then
    log "数据库里没有指向 ${TARGET_BASE} 的地址，无需还原"
  else
    log "执行还原：${TARGET_BASE} → http://localhost:${TARGET_PORT}"
    for db in $DBS; do
      sql=$(gen_revert_sql "$db")
      [ -n "$sql" ] || continue
      printf '%s\n' "$sql" | mysql_run_db "$db" >/dev/null
    done
  fi
else
  BEFORE="$(count_matches "$DEV_HOST_RE")"
  log "待修复行数：${BEFORE}"
  if [ "$BEFORE" = "0" ]; then
    log "数据库里没有开发机回源地址，无需替换"
  else
    log "执行替换：开发机地址 → ${TARGET_BASE}（含 sys_storage_config.domain，后续上传同样生效）"
    for db in $DBS; do
      sql=$(gen_update_sql "$db" "$DEV_HOST_RE" "$TARGET_BASE")
      [ -n "$sql" ] || continue
      printf '%s\n' "$sql" | mysql_run_db "$db" >/dev/null
    done
  fi
fi

# 即使库里已没有待替换地址也清一次缓存：上次可能只改了库没清缓存，手机仍会看到旧地址。
[ "$CLEAR_CACHE" = "true" ] && clear_image_caches

if [ "$ACTION" = "revert" ]; then
  log "还原后仍指向 ${TARGET_BASE} 的行数：$(count_matches "$TARGET_RE")（期望 0）"
  exit 0
fi

AFTER="$(count_matches "$DEV_HOST_RE")"
log "替换后残留待修复地址：${AFTER} 行（期望 0）；已指向 ${TARGET_BASE} 的行数：$(count_matches "$TARGET_RE")"
[ "$AFTER" = "0" ] || warn "仍有残留，请检查列名是否命中 COLUMN_SQL 的正则（可加 --dry-run 查看）"
check_api_env
verify_sample_image
print_wechat_tips
