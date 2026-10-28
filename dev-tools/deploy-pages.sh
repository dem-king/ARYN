#!/usr/bin/env bash
#
# 一键构建并发布 H5 / 管理端到 Cloudflare Pages。
#
# 前置：
#   - 已登录 Cloudflare（首次执行一次 `npx wrangler login`，浏览器点允许即可）；
#   - 本机后端隧道（图片隧道，映射 9999）在运行，脚本会自动读取其域名；
#   - Pages 项目默认 aryn-h5 / aryn-admin（可用参数覆盖）。
#
# 用法（仓库根目录）：
#   ./dev-tools/deploy-pages.sh                     # 重建两个前端 + 发布到 Pages
#   ./dev-tools/deploy-pages.sh --skip-build        # 不重建，直接用现有产物发布（后端域名变化时常用）
#   ./dev-tools/deploy-pages.sh --only h5           # 只构建/发布 H5（改了 H5 时用，省时间）
#   ./dev-tools/deploy-pages.sh --only admin        # 只构建/发布管理端
#   ./dev-tools/deploy-pages.sh --only h5 --skip-build   # 只重新发布现有 H5 产物
#   ./dev-tools/deploy-pages.sh --h5-project aryn-h5 --admin-project aryn-admin
#
# 说明：页面/JS 是静态部署，链接固定不变；接口与图片仍依赖本机隧道，
#       隧道换域名后执行本脚本（或用 --skip-build，因为 _worker.js 的 fallback 会更新）即可。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
MOBILE_DIR="${ROOT_DIR}/aryn-mall-uniapp"
UI_DIR="${ROOT_DIR}/aryn-mall-ui"
RUN_IMAGE="${TMPDIR:-/tmp}/aryn-image-tunnel"

H5_PROJECT="${H5_PROJECT:-aryn-h5}"
ADMIN_PROJECT="${ADMIN_PROJECT:-aryn-admin}"
DO_BUILD=true
ONLY="both"

log() { printf '[deploy-pages] %s\n' "$*"; }
die() { printf '[deploy-pages] 错误: %s\n' "$*" >&2; exit 1; }

while (($# > 0)); do
  case "$1" in
    --skip-build) DO_BUILD=false; shift ;;
    --only) ONLY="${2:-}"; shift 2 ;;
    --h5-project) H5_PROJECT="${2:-}"; shift 2 ;;
    --admin-project) ADMIN_PROJECT="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) die "未知参数: $1" ;;
  esac
done

case "$ONLY" in
  both|h5|admin) ;;
  *) die "--only 只支持 h5 / admin / both，收到: $ONLY" ;;
esac

# 1) 后端隧道地址
BACKEND_HOST=$(sed -n 's/^CF_HOST=//p' "${RUN_IMAGE}/state.env" 2>/dev/null | head -1)
[ -n "$BACKEND_HOST" ] || die "读不到后端隧道地址，请先运行 ./dev-tools/dev-image-tunnel.sh 或 ./dev-tools/start-demo-env.sh"
BACKEND="https://${BACKEND_HOST}"
if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${BACKEND}/boot/")" = "000" ]; then
  die "后端隧道不可用：${BACKEND}（先跑 ./dev-tools/start-demo-env.sh 恢复）"
fi
log "后端隧道：${BACKEND}"

# 2) 构建
if $DO_BUILD; then
  if [ "$ONLY" != "admin" ]; then
    log '构建 H5（demo 模式，API 同源 /api）…'
    ( cd "$MOBILE_DIR" && pnpm build:h5:demo ) || die 'H5 构建失败'
  fi
  if [ "$ONLY" != "h5" ]; then
    log '构建管理端静态包…'
    ( cd "$UI_DIR" && pnpm build:ele ) || die '管理端构建失败'
  fi
fi

# 3) 生成部署产物（_worker.js 里写入当前后端地址）
PREPARE_ARGS=(--backend "$BACKEND")
[ "$ONLY" = "h5" ] && PREPARE_ARGS+=(--skip-admin)
[ "$ONLY" = "admin" ] && PREPARE_ARGS+=(--skip-h5)
node "${SCRIPT_DIR}/prepare-pages-deploy.mjs" "${PREPARE_ARGS[@]}" || die '生成 Pages 产物失败'

# 4) 检查登录态
cd "$ROOT_DIR"
if ! npx --yes wrangler@latest whoami 2>&1 | grep -q 'Account Name'; then
  die '未登录 Cloudflare，请先在本机终端执行：npx wrangler login'
fi

# 5/6) 确保项目存在并按需发布
if [ "$ONLY" != "admin" ]; then
  npx --yes wrangler@latest pages project create "$H5_PROJECT" --production-branch main >/dev/null 2>&1 || true
  log "发布 H5 -> ${H5_PROJECT}…"
  npx --yes wrangler@latest pages deploy outputs/pages/h5 --project-name "$H5_PROJECT" --branch main --commit-dirty=true 2>&1 | tail -3
fi
if [ "$ONLY" != "h5" ]; then
  npx --yes wrangler@latest pages project create "$ADMIN_PROJECT" --production-branch main >/dev/null 2>&1 || true
  log "发布管理端 -> ${ADMIN_PROJECT}…"
  npx --yes wrangler@latest pages deploy outputs/pages/admin --project-name "$ADMIN_PROJECT" --branch main --commit-dirty=true 2>&1 | tail -3
fi

printf '\n[deploy-pages] 完成（only=%s）：\n' "$ONLY"
[ "$ONLY" != "admin" ] && printf '  H5      : https://%s.pages.dev\n' "$H5_PROJECT"
[ "$ONLY" != "h5" ] && printf '  管理端  : https://%s.pages.dev\n' "$ADMIN_PROJECT"
printf '  后端隧道: %s\n' "$BACKEND"
printf '[deploy-pages] 提示：隧道换域名后，重新执行本脚本（加 --skip-build 更快）即可更新代理目标。\n'
