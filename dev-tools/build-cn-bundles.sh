#!/usr/bin/env bash
#
# 构建国内演示用的两个前端产物：
#   - H5      -> aryn-mall-uniapp/dist/build/h5（同源 /api，history 路由）
#   - 管理端  -> aryn-mall-ui/apps/web-ele/dist-cn（base=/admin/，hash 路由）
#
# 为什么管理端要单独构建：
#   国内方案只开一条隧道、一个域名，管理端必须挂在 /admin/ 子路径下。
#   Vben 的 base 同时影响静态资源地址和 router base，必须用 VITE_BASE=/admin/ 构建。
#   这里通过临时写 .env.production.local 覆盖，构建完自动恢复，不影响原来的 dist。
#
# 用法（仓库根目录）：
#   ./dev-tools/build-cn-bundles.sh              # 两个前端都构建
#   ./dev-tools/build-cn-bundles.sh --only h5    # 只构建 H5
#   ./dev-tools/build-cn-bundles.sh --only admin # 只构建管理端
#
# 说明：构建管理端约 2~5 分钟；不改代码时不需要重复构建。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
MOBILE_DIR="${ROOT_DIR}/aryn-mall-uniapp"
UI_DIR="${ROOT_DIR}/aryn-mall-ui"
ADMIN_DIR="${UI_DIR}/apps/web-ele"
ADMIN_OUT="${ADMIN_DIR}/dist-cn"
ENV_LOCAL="${ADMIN_DIR}/.env.production.local"
ENV_BACKUP="${ENV_LOCAL}.aryn-cn-backup"

ONLY="both"

log() { printf '[build-cn] %s\n' "$*"; }
warn() { printf '[build-cn] 警告: %s\n' "$*" >&2; }
die() { printf '[build-cn] 错误: %s\n' "$*" >&2; exit 1; }

while (($# > 0)); do
  case "$1" in
    --only) ONLY="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) die "未知参数: $1" ;;
  esac
done

case "$ONLY" in
  both|h5|admin) ;;
  *) die "--only 只支持 h5 / admin / both，收到: $ONLY" ;;
esac

command -v pnpm >/dev/null 2>&1 || die '未找到 pnpm'
export NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=8192}"

ENV_RESTORED=0
restore_env() {
  [ "$ENV_RESTORED" = "1" ] && return 0
  ENV_RESTORED=1
  rm -f "$ENV_LOCAL"
  if [ -f "$ENV_BACKUP" ]; then
    mv "$ENV_BACKUP" "$ENV_LOCAL"
    log '已恢复原有的 .env.production.local'
  fi
}
trap restore_env EXIT INT TERM

if [ "$ONLY" != "admin" ]; then
  if pgrep -f "vite-plugin-uni/bin/uni.js dev" >/dev/null 2>&1; then
    warn '检测到 uni-app dev 进程在运行；H5 构建可能与它争写 src/pages.json，建议先停掉再构建'
  fi
  log '构建 H5（demo 模式，同源 /api）…'
  ( cd "$MOBILE_DIR" && pnpm build:h5:demo ) || die 'H5 构建失败'
  [ -f "${MOBILE_DIR}/dist/build/h5/index.html" ] || die 'H5 产物缺少 index.html'
  log "H5 产物就绪：${MOBILE_DIR}/dist/build/h5"
fi

if [ "$ONLY" != "h5" ]; then
  log '构建管理端（base=/admin/ → dist-cn）…'
  [ -d "${ADMIN_DIR}/node_modules" ] || [ -d "${UI_DIR}/node_modules" ] || die '管理端依赖未安装，请先在 aryn-mall-ui 执行 pnpm install'
  if [ -f "$ENV_LOCAL" ]; then
    cp "$ENV_LOCAL" "$ENV_BACKUP" || die '备份 .env.production.local 失败'
  fi
  printf 'VITE_BASE=/admin/\nVITE_ARCHIVER=false\n' > "$ENV_LOCAL" || die '写入 .env.production.local 失败'

  ( cd "$ADMIN_DIR" && pnpm exec vite build --mode production --outDir dist-cn ) || die '管理端构建失败'
  restore_env

  [ -f "${ADMIN_OUT}/index.html" ] || die '管理端产物缺少 index.html'
  if ! grep -q '/admin/' "${ADMIN_OUT}/index.html"; then
    warn '管理端 index.html 里没有找到 /admin/ 前缀，base 可能没生效'
  fi
  log "管理端产物就绪：${ADMIN_OUT}"
fi

log '完成。启动网关：./dev-tools/start-cn-demo.sh'
du -sh "${MOBILE_DIR}/dist/build/h5" "${ADMIN_OUT}" 2>/dev/null || true
