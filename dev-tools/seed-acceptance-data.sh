#!/usr/bin/env bash
#
# 船供化验收种子数据一键铺设 / 清理
#
# 背景：端到端验收的 B（船供采购）、C（共享购物车）、D（船舶靠港切换）三条链路，
# 需要「船舶成员已绑定 + 船供商品已就绪」这两个前提。本脚本把前提一次性铺好。
#
# 铺完之后：
#   · 27 条上架船供商品（>20 条，可验证分页）
#   · 3 名用户绑定到悦航1号（发起人 / 采购确认人 / 普通船员）
#   · 1 条 sale_scope=2 仅船供商品、1 条下架商品（用于验证隔离规则）
#
# 用法:
#   ./dev-tools/seed-acceptance-data.sh              # 自动探测模式并铺数据
#   ./dev-tools/seed-acceptance-data.sh --clean      # 移除本脚本铺的数据
#   ./dev-tools/seed-acceptance-data.sh --verify     # 只做只读校验，不改数据
#   MODE=cloud ./dev-tools/seed-acceptance-data.sh   # 强制指定模式
#
# 环境变量:
#   MODE       cloud | boot，默认自动探测
#   MYSQL_CTN  MySQL 容器名，默认 aryn-mysql
#   MYSQL_USER 默认 root
#   MYSQL_PASS 默认 123456
#
# 幂等：脚本自带按 96x 前缀的清理，可重复执行，不影响存量业务数据。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DB_DIR="${ROOT_DIR}/aryn-mall-java/db"

MYSQL_CTN="${MYSQL_CTN:-aryn-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
MODE="${MODE:-}"

ACTION="apply"

log()  { printf '[seed] %s\n' "$*"; }
die()  { printf '[seed] 错误: %s\n' "$*" >&2; exit 1; }

while (($# > 0)); do
  case "$1" in
    --clean)  ACTION="clean" ;;
    --verify) ACTION="verify" ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) die "未知参数: $1（可用 --clean / --verify / -h）" ;;
  esac
  shift
done

command -v docker >/dev/null 2>&1 || die "未找到命令: docker"
docker info >/dev/null 2>&1 || die "Docker 未运行，请先执行 ./dev-tools/start-all.sh"

mysql_exec() {
  docker exec -i "${MYSQL_CTN}" mysql --default-character-set=utf8mb4 \
    -u"${MYSQL_USER}" -p"${MYSQL_PASS}" "$@" 2>&1 | grep -v 'Using a password'
}

mysql_query() {
  docker exec -i "${MYSQL_CTN}" mysql --default-character-set=utf8mb4 -N -B \
    -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -e "$1" 2>/dev/null
}

# ---------------------------------------------------------------------------
# 模式探测：有 aryn_product 视为 Cloud 微服务模式，有 aryn_boot 视为单体模式
# ---------------------------------------------------------------------------
if [ -z "${MODE}" ]; then
  if [ -n "$(mysql_query "SHOW DATABASES LIKE 'aryn_product';")" ]; then
    MODE="cloud"
  elif [ -n "$(mysql_query "SHOW DATABASES LIKE 'aryn_boot';")" ]; then
    MODE="boot"
  else
    die "未探测到 aryn_product 或 aryn_boot 数据库，请确认 MySQL 容器名（当前 MYSQL_CTN=${MYSQL_CTN}）"
  fi
fi

case "${MODE}" in
  cloud) SQL_FILE="${DB_DIR}/cloud/62ship_supply_seed_acceptance.sql"; PROD_DB="aryn_product"; VESSEL_DB="aryn_vessel"; USER_DB="aryn_user" ;;
  boot)  SQL_FILE="${DB_DIR}/boot/62ship_supply_seed_acceptance.sql";  PROD_DB="aryn_boot";    VESSEL_DB="aryn_boot";    USER_DB="aryn_boot" ;;
  *) die "MODE 只能是 cloud 或 boot，当前为: ${MODE}" ;;
esac

[ -f "${SQL_FILE}" ] || die "找不到种子脚本: ${SQL_FILE}"

log "模式=${MODE}  容器=${MYSQL_CTN}  脚本=${SQL_FILE#"${ROOT_DIR}/"}"

# ---------------------------------------------------------------------------
# 只读校验
# ---------------------------------------------------------------------------
verify() {
  log "只读校验（不改数据）..."
  mysql_exec -t -e "
    SELECT '上架船供商品(期望 27)' AS item, COUNT(*) AS cnt
    FROM \`${PROD_DB}\`.\`ship_goods_profile\` p
    JOIN \`${PROD_DB}\`.\`goods_spu\` s ON s.\`id\` = p.\`spu_id\`
    WHERE p.\`del_flag\`='0' AND p.\`sale_scope\` IN ('2','3') AND s.\`status\`='1' AND s.\`del_flag\`='0'
    UNION ALL SELECT '仅船供 sale_scope=2(期望 1)', COUNT(*)
    FROM \`${PROD_DB}\`.\`ship_goods_profile\` WHERE \`del_flag\`='0' AND \`sale_scope\`='2'
    UNION ALL SELECT '下架商品(期望 1)', COUNT(*)
    FROM \`${PROD_DB}\`.\`ship_goods_profile\` p
    JOIN \`${PROD_DB}\`.\`goods_spu\` s ON s.\`id\` = p.\`spu_id\`
    WHERE p.\`del_flag\`='0' AND s.\`status\` <> '1'
    UNION ALL SELECT '船舶成员(期望 3)', COUNT(*)
    FROM \`${VESSEL_DB}\`.\`vessel_member\` WHERE \`del_flag\`='0' AND \`id\` LIKE '962%'
    UNION ALL SELECT '待审核绑定申请(期望 1)', COUNT(*)
    FROM \`${VESSEL_DB}\`.\`vessel_bind_apply\` WHERE \`del_flag\`='0' AND \`id\` LIKE '969%' AND \`status\`='1'
    UNION ALL SELECT '可用靠港计划(期望 >=2)', COUNT(*)
    FROM \`${VESSEL_DB}\`.\`vessel_call\` WHERE \`del_flag\`='0' AND \`status\` IN ('1','2') AND \`etd\` > NOW();"
}

# ---------------------------------------------------------------------------
# 清理
# ---------------------------------------------------------------------------
clean() {
  log "移除本脚本铺的种子数据（仅 96x 前缀）..."
  mysql_exec -e "
    DELETE FROM \`${PROD_DB}\`.\`ship_sku_profile\`   WHERE \`id\` LIKE '967%';
    DELETE FROM \`${PROD_DB}\`.\`ship_goods_profile\` WHERE \`id\` LIKE '966%';
    DELETE FROM \`${PROD_DB}\`.\`goods_sku\`          WHERE \`id\` LIKE '965%';
    DELETE FROM \`${PROD_DB}\`.\`goods_spu\`          WHERE \`id\` LIKE '964%';
    DELETE FROM \`${PROD_DB}\`.\`goods_category\`     WHERE \`id\` LIKE '963%';
    DELETE FROM \`${VESSEL_DB}\`.\`vessel_bind_apply\` WHERE \`id\` LIKE '969%';
    DELETE FROM \`${VESSEL_DB}\`.\`vessel_member\`    WHERE \`id\` LIKE '962%';
    DELETE FROM \`${VESSEL_DB}\`.\`vessel_call\`      WHERE \`id\` LIKE '968%';
    DELETE FROM \`${USER_DB}\`.\`user_info\`         WHERE \`id\` LIKE '97%';"
  log "清理完成。"
}

case "${ACTION}" in
  verify) verify; exit 0 ;;
  clean)  clean; verify; exit 0 ;;
esac

# ---------------------------------------------------------------------------
# 铺设
# ---------------------------------------------------------------------------
log "铺设种子数据..."
mysql_exec < "${SQL_FILE}"

log ""
verify
log ""
log "完成。接下来："
log "  1) 用绑定过的账号重新登录（权限与成员关系在登录时快照进 token）"
log "     发起人     2040654277629796353（微信用户6798）"
log "     采购确认人 2040656345832747009（176****2320，可密码登录）"
log "     普通船员   2096466699352522754（宇宙第一帅）"
log "  2) 首页工作台应显示「悦航1号」与下一靠港，点船舶名可打开靠港选择器"
log "  3) 按 docs/50-需求文档/2026-09-19-船供化交互调研与竞品对标/E2E验收清单.md 执行 B/C/D 链路"
