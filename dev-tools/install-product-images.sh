#!/usr/bin/env bash
#
# 商超商品主图安装：把 db/assets/grocery-product-images 的图片放进文件存储根目录，
# 并执行对应模式的 70grocery_product_images.sql 回填 goods_spu.spu_urls 与 sys_material。
#
# 背景：图片是本地存储（sys_storage_config.type=local）的实体文件，必须落在
#       LocalUploadFileHandler 配置的 bucket 目录下，Servlet 才能通过
#       /{upms|boot}/file/local/{tenantId}/{uuid}.ext 回源。只跑 SQL 不铺文件会得到 404。
#
# 用法:
#   ./dev-tools/install-product-images.sh              # 自动探测模式，铺图片 + 执行 SQL
#   ./dev-tools/install-product-images.sh --files-only # 只铺图片，不执行 SQL
#   ./dev-tools/install-product-images.sh --verify     # 只读校验（文件 + 数据库 + HTTP）
#   MODE=cloud ./dev-tools/install-product-images.sh   # 强制指定模式
#
# 环境变量:
#   MODE       cloud | boot，默认自动探测
#   MYSQL_CTN  MySQL 容器名，默认 aryn-mysql
#   UPMS_CTN   Cloud 模式文件容器，默认 aryn-upms-biz
#   BOOT_CTN   Boot 模式文件容器，默认 aryn-boot
#   MYSQL_USER / MYSQL_PASS 默认 root / 123456
#   GATEWAY    回源地址，默认 http://localhost:9999
#
# 幂等：按存储文件名覆盖，SQL 只更新 SPU 954x 并按素材 ID upsert，可重复执行。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DB_DIR="${ROOT_DIR}/aryn-mall-java/db"
ASSET_DIR="${DB_DIR}/assets/grocery-product-images"

MYSQL_CTN="${MYSQL_CTN:-aryn-mysql}"
UPMS_CTN="${UPMS_CTN:-aryn-upms-biz}"
BOOT_CTN="${BOOT_CTN:-aryn-boot}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
GATEWAY="${GATEWAY:-http://localhost:9999}"
MODE="${MODE:-}"
TENANT="1590229800633634816"

ACTION="apply"
log() { printf '[images] %s\n' "$*"; }
die() { printf '[images] 错误: %s\n' "$*" >&2; exit 1; }

while (($# > 0)); do
  case "$1" in
    --files-only) ACTION="files" ;;
    --verify)     ACTION="verify" ;;
    -h|--help)    sed -n '2,30p' "$0"; exit 0 ;;
    *) die "未知参数: $1（可用 --files-only / --verify / -h）" ;;
  esac
  shift
done

command -v docker >/dev/null 2>&1 || die "未找到命令: docker"
docker info >/dev/null 2>&1 || die "Docker 未运行，请先执行 ./dev-tools/start-all.sh"
[ -f "${ASSET_DIR}/manifest.tsv" ] || die "找不到图片清单: ${ASSET_DIR}/manifest.tsv"

mysql_query() {
  docker exec -i "${MYSQL_CTN}" mysql --default-character-set=utf8mb4 -N -B \
    -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -e "$1" 2>/dev/null
}
# 注意：管道末端是 grep，grep 无匹配时返回 1，在 set -o pipefail 下会把成功的 mysql 也判成失败。
# 这里显式取 mysql 的退出码，并允许「没有告警可过滤」这一正常情况。
mysql_exec() {
  local out rc
  out=$(docker exec -i "${MYSQL_CTN}" mysql --default-character-set=utf8mb4 \
    -u"${MYSQL_USER}" -p"${MYSQL_PASS}" "$@" 2>&1)
  rc=$?
  printf '%s\n' "${out}" | grep -v 'Using a password' || true
  return "${rc}"
}

if [ -z "${MODE}" ]; then
  if [ -n "$(mysql_query "SHOW DATABASES LIKE 'aryn_product';")" ]; then MODE="cloud"
  elif [ -n "$(mysql_query "SHOW DATABASES LIKE 'aryn_boot';")" ]; then MODE="boot"
  else die "未探测到 aryn_product 或 aryn_boot 数据库（当前 MYSQL_CTN=${MYSQL_CTN}）"; fi
fi
case "${MODE}" in
  cloud) SQL_FILE="${DB_DIR}/cloud/70grocery_product_images.sql"; PROD_DB="aryn_product"; UPMS_DB="aryn_upms"; FILE_CTN="${UPMS_CTN}"; URL_MODE="upms" ;;
  boot)  SQL_FILE="${DB_DIR}/boot/70grocery_product_images.sql";  PROD_DB="aryn_boot";    UPMS_DB="aryn_boot";    FILE_CTN="${BOOT_CTN}"; URL_MODE="boot" ;;
  *) die "MODE 只能是 cloud 或 boot，当前为: ${MODE}" ;;
esac

STORAGE_ROOT="$(mysql_query "SELECT bucket FROM \`${UPMS_DB}\`.\`sys_storage_config\` WHERE del_flag='0' AND \`status\`='0' AND type='local' LIMIT 1;")"
[ -n "${STORAGE_ROOT}" ] || die "未读到本机存储根目录（${UPMS_DB}.sys_storage_config 无启用中的 local 配置）"
TARGET_DIR="${STORAGE_ROOT}/${TENANT}"

docker inspect "${FILE_CTN}" >/dev/null 2>&1 || die "容器不存在: ${FILE_CTN}"

log "模式=${MODE}  容器=${FILE_CTN}  存储根=${STORAGE_ROOT}  商品库=${PROD_DB}"
log "图片清单=$(($(wc -l < "${ASSET_DIR}/manifest.tsv") - 1)) 条"

# 仓库内图片用「可读的商品名」保存，落盘时必须按 manifest.tsv 的 stored_file 改名，
# 否则 URL 里引用的 UUID 文件名对不上，回源会 404。
install_files() {
  local stage="${TMPDIR:-/tmp}/aryn-product-images-stage.$$"
  mkdir -p "${stage}" || die "无法创建暂存目录 ${stage}"
  log "按 manifest 暂存图片（源名 -> 存储名）..."
  local n=0
  while IFS=$'\t' read -r spu_id product_name asset_path stored_file file_size; do
    [ "${spu_id}" = "spu_id" ] && continue
    [ -f "${ASSET_DIR}/${asset_path}" ] || die "清单引用的图片不存在: ${asset_path}"
    cp -f "${ASSET_DIR}/${asset_path}" "${stage}/${stored_file}" || die "暂存失败: ${asset_path}"
    n=$((n + 1))
  done < "${ASSET_DIR}/manifest.tsv"
  [ "${n}" -gt 0 ] || die "manifest 未解析出任何图片"

  docker exec "${FILE_CTN}" mkdir -p "${TARGET_DIR}" || die "无法创建目录 ${TARGET_DIR}"
  log "拷贝 ${n} 个文件到 ${FILE_CTN}:${TARGET_DIR} ..."
  docker cp "${stage}/." "${FILE_CTN}:${TARGET_DIR}/" >/dev/null || die "docker cp 失败"
  rm -rf "${stage}"
  log "复制完成，容器内文件数=$(docker exec "${FILE_CTN}" sh -c "ls -1 '${TARGET_DIR}' | wc -l")"
}

verify() {
  log "只读校验（不改数据）..."
  local expected actual
  expected=$(($(wc -l < "${ASSET_DIR}/manifest.tsv") - 1))

  actual=$(mysql_query "SELECT COUNT(*) FROM \`${PROD_DB}\`.\`goods_spu\` WHERE id LIKE '954%' AND del_flag='0' AND spu_urls<>'';")
  log "已回填主图商品数=${actual}（期望 ${expected}）"

  actual=$(mysql_query "SELECT COUNT(*) FROM \`${UPMS_DB}\`.\`sys_material\` WHERE id LIKE '956%' AND del_flag='0';")
  log "已登记素材数=${actual}（期望 ${expected}）"

  local file_count
  file_count=$(docker exec "${FILE_CTN}" sh -c "ls -1 '${TARGET_DIR}' 2>/dev/null | wc -l")
  log "容器内文件数=${file_count}"

  local sample
  sample=$(mysql_query "SELECT spu_urls FROM \`${PROD_DB}\`.\`goods_spu\` WHERE id='9540000000000000001';")
  if [ -n "${sample}" ]; then
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' "${sample}" 2>/dev/null || echo "000")
    log "样例回源 ${code}  ${sample}"
  fi
}

case "${ACTION}" in
  verify) verify; exit 0 ;;
  files)  install_files; verify; exit 0 ;;
esac

install_files
log "执行 ${SQL_FILE#"${ROOT_DIR}/"} ..."
mysql_exec < "${SQL_FILE}" || die "SQL 执行失败"
verify
log "完成。"
