#!/usr/bin/env bash
#
# 船供化改造 API 冒烟测试
#
# 覆盖 2026-09-19 交互调研后修复项的「接口可验证部分」：
#   P0-3  船供目录搜索（按 IMPA / 中文名 / 内部编码 各自可命中）
#   P0-5  共享购物车 C 端接口（列表 / 创建 / 详情 / 关闭）
#   P0-6a 船供目录只返回上架商品
#   P0-6b 仅船供商品不出现在零售列表
#   P1-1  靠港计划接口可用
#
# 用法:
#   ./dev-tools/smoke-ship-supply.sh
#   MODE=boot ./dev-tools/smoke-ship-supply.sh
#   TOKEN=<已有satoken> ./dev-tools/smoke-ship-supply.sh
#   BASE_URL=http://localhost:9999 PHONE=13800000000 PASSWORD=xxx ./dev-tools/smoke-ship-supply.sh
#
# 环境变量:
#   BASE_URL   网关地址，默认 http://localhost:9999
#   MODE       cloud（默认，走 /域/路径）或 boot（改写为 /boot/路径）
#   TENANT_ID  租户ID，默认取前端 .env 的 VITE_TENANT_ID
#   TOKEN      已有的 C 端 satoken；不填则用 PHONE + PASSWORD 登录换取
#   PHONE/PASSWORD  商城用户手机号与密码
#
# 退出码: 0 全部通过；1 存在失败项

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

BASE_URL="${BASE_URL:-http://localhost:9999}"
MODE="${MODE:-cloud}"
TENANT_ID="${TENANT_ID:-}"
TOKEN="${TOKEN:-}"
PHONE="${PHONE:-}"
PASSWORD="${PASSWORD:-}"

PASS=0
FAIL=0
SKIP=0

log()  { printf '[smoke] %s\n' "$*"; }
ok()   { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$*"; }
bad()  { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$*"; }
skip() { SKIP=$((SKIP + 1)); printf '  \033[33mSKIP\033[0m %s\n' "$*"; }

# ---------------------------------------------------------------------------
# JSON 取值助手（python3 仅作解析，不依赖 jq）
# ---------------------------------------------------------------------------

# jget <点号路径>   JSON 从 stdin 读入；取不到输出空串
jget() {
  python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for part in sys.argv[1].split('.'):
    if not part:
        continue
    try:
        data = data[int(part)] if isinstance(data, list) else data[part]
    except Exception:
        sys.exit(0)
if data is None:
    sys.exit(0)
print(data if isinstance(data, (str, int, float, bool)) else json.dumps(data, ensure_ascii=False))
" "$1"
}

# jfield <数组路径> <字段名>   每行输出一个字段值
jfield() {
  python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for part in sys.argv[1].split('.'):
    if not part:
        continue
    try:
        data = data[int(part)] if isinstance(data, list) else data[part]
    except Exception:
        sys.exit(0)
if not isinstance(data, list):
    sys.exit(0)
for item in data:
    if isinstance(item, dict) and item.get(sys.argv[2]) is not None:
        print(item[sys.argv[2]])
" "$1" "$2"
}

# urlencode <文本>   百分号编码查询参数值
#
# 此前这里用 sed 's/ /%20/g' 只替换空格，中文关键词直接以原始 UTF-8 字节进 URL，
# 服务端解析查询串失败返回 400（表现为「关键词搜索无有效响应」）。
urlencode() {
  python3 -c "
import sys, urllib.parse
sys.stdout.write(urllib.parse.quote(sys.argv[1], safe=''))
" "$1"
}

# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------

# boot 模式下把 /域/路径 改写为 /boot/路径（与前端 rewriteBootUrl 一致）
#
# 注意：不能用 ${path#/[!/]*} —— `#` 是非贪婪匹配，`*` 会匹配空串，
# 结果只吃掉首段的一个字符（/vessel/... → /bootessel/...）。改用正则精确去掉首段。
resolve_path() {
  local path="$1"
  if [ "${MODE}" = "boot" ] && [[ "${path}" =~ ^/[^/]+(.*)$ ]]; then
    printf '/boot%s' "${BASH_REMATCH[1]}"
  else
    printf '%s' "${path}"
  fi
}

LAST_BODY=""
LAST_STATUS=""

api() {
  local method="$1" path="$2" data="${3:-}"
  local url="${BASE_URL}$(resolve_path "${path}")"
  local tmp
  tmp="$(mktemp)"
  local args=(-sS -o "${tmp}" -w '%{http_code}' -X "${method}" "${url}"
              -H 'Content-Type: application/json')
  [ -n "${TENANT_ID}" ] && args+=(-H "tenant-id: ${TENANT_ID}")
  [ -n "${TOKEN}" ] && args+=(-H "satoken: ${TOKEN}")
  [ -n "${data}" ] && args+=(-d "${data}")

  LAST_STATUS="$(curl "${args[@]}" 2>/dev/null || printf '000')"
  LAST_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

api_get()  { api GET "$1"; }
api_post() { api POST "$1" "${2:-}"; }

# 取一次 token
acquire_token() {
  if [ -n "${TOKEN}" ]; then
    return 0
  fi
  if [ -z "${PHONE}" ] || [ -z "${PASSWORD}" ]; then
    return 1
  fi
  api_post '/auth/toc-token/password/login' \
    "{\"phone\":\"${PHONE}\",\"password\":\"${PASSWORD}\"}"
  TOKEN="$(printf '%s' "${LAST_BODY}" | jget 'data.tokenValue')"
  [ -n "${TOKEN}" ]
}

# ---------------------------------------------------------------------------
# 准备
# ---------------------------------------------------------------------------

if [ -z "${TENANT_ID}" ]; then
  env_file="${ROOT_DIR}/aryn-mall-uniapp/.env"
  if [ -f "${env_file}" ]; then
    TENANT_ID="$(grep -E '^VITE_TENANT_ID=' "${env_file}" | head -1 | cut -d= -f2- | tr -d '"' | tr -d "'" | tr -d '[:space:]')"
  fi
fi
if [ -z "${TENANT_ID}" ]; then
  log "缺少租户ID：请设置 TENANT_ID，或在 aryn-mall-uniapp/.env 中配置 VITE_TENANT_ID"
  exit 1
fi

log "BASE_URL=${BASE_URL}  MODE=${MODE}  TENANT_ID=${TENANT_ID}"

if ! acquire_token; then
  log "未提供 TOKEN，且无法用 PHONE/PASSWORD 登录。请设置 TOKEN 或 PHONE + PASSWORD。"
  exit 1
fi
log "已获取访问令牌"

# ---------------------------------------------------------------------------
# 1. 船舶域
# ---------------------------------------------------------------------------

log ''
log '1) 船舶域可用性'

api_get '/vessel/app/my-vessels'
if [ "${LAST_STATUS}" != "200" ]; then
  bad "GET /vessel/app/my-vessels 返回 ${LAST_STATUS}"
else
  ok "GET /vessel/app/my-vessels 返回 200"
fi

VESSEL_ID="$(printf '%s' "${LAST_BODY}" | jfield 'data' 'id' | head -1)"
VESSEL_NAME="$(printf '%s' "${LAST_BODY}" | jfield 'data' 'vesselName' | head -1)"

if [ -z "${VESSEL_ID}" ]; then
  skip "当前账号无在船船舶，跳过靠港计划与共享购物车创建检查"
else
  log "  使用船舶: ${VESSEL_NAME:-<未命名>} (${VESSEL_ID})"
  api_get "/vessel/app/${VESSEL_ID}/calls"
  if [ "${LAST_STATUS}" != "200" ]; then
    bad "GET /vessel/app/{id}/calls 返回 ${LAST_STATUS}（P1-1 靠港计划接口）"
  else
    CALL_ID="$(printf '%s' "${LAST_BODY}" | jfield 'data' 'id' | head -1)"
    if [ -n "${CALL_ID}" ]; then
      ok "GET /vessel/app/{id}/calls 返回可用靠港计划"
    else
      skip "该船舶暂无可用靠港计划（P1-1 选择器会提示「到港后可下单」）"
    fi
  fi
fi

# ---------------------------------------------------------------------------
# 2. 船供目录 + 搜索（P0-3 / P0-6a）
# ---------------------------------------------------------------------------

log ''
log '2) 船供目录与搜索'

api_get '/product/app/goodsspu/ship/page?current=1&size=10'
if [ "${LAST_STATUS}" != "200" ]; then
  bad "GET /product/app/goodsspu/ship/page 返回 ${LAST_STATUS}"
  SHIP_BODY=''
else
  ok "GET /product/app/goodsspu/ship/page 返回 200"
  SHIP_BODY="${LAST_BODY}"

  # P0-6a：下架商品不得出现（Controller 层强制 status=1）
  offline="$(printf '%s' "${SHIP_BODY}" | jfield 'data.records' 'status' | grep -vx '1' | wc -l | tr -d ' ')"
  if [ "${offline}" = "0" ]; then
    ok "船供目录全部为已上架商品（status=1）"
  else
    bad "船供目录含 ${offline} 条非上架商品（P0-6a）"
  fi

  SAMPLE_IMPA="$(printf '%s' "${SHIP_BODY}" | jfield 'data.records' 'impaCode' | head -1)"
  SAMPLE_NAME="$(printf '%s' "${SHIP_BODY}" | jfield 'data.records' 'name' | head -1)"
  SAMPLE_INTERNAL="$(printf '%s' "${SHIP_BODY}" | jfield 'data.records' 'internalItemCode' | head -1)"
  SAMPLE_SPU="$(printf '%s' "${SHIP_BODY}" | jfield 'data.records' 'spuId' | head -1)"
  SAMPLE_STATUS="$(printf '%s' "${SHIP_BODY}" | jfield 'data.records' 'status' | head -1)"

  # P0-3：关键词搜索必须能按「编码」或「名称」单独命中。
  # 修复前 impaCode 与 nameEn 两组条件是 AND，单独搜编码或单独搜品名都返回 0 条。
  check_keyword_hit() {
    local label="$1" kw="$2"
    if [ -z "${kw}" ]; then
      skip "样本无 ${label}，跳过"
      return
    fi
    api_get "/product/app/goodsspu/ship/page?current=1&size=10&keyword=$(urlencode "${kw}")"
    local total
    total="$(printf '%s' "${LAST_BODY}" | jget 'data.total')"
    if [ -z "${total}" ]; then
      bad "关键词搜索(${label}=${kw}) 无有效响应"
    elif [ "${total}" -gt 0 ] 2>/dev/null; then
      ok "关键词搜索命中：${label}=${kw} → ${total} 条"
    else
      bad "关键词搜索无结果：${label}=${kw}（P0-3 搜索条件可能又被 AND 掉）"
    fi
  }

  check_keyword_hit 'IMPA' "${SAMPLE_IMPA}"
  check_keyword_hit '中文名' "${SAMPLE_NAME}"
  check_keyword_hit '内部编码' "${SAMPLE_INTERNAL}"
fi

# ---------------------------------------------------------------------------
# 3. 零售列表不得泄漏仅船供商品（P0-6b）
# ---------------------------------------------------------------------------

log ''
log '3) 零售列表与船供商品的隔离'

# 隔离规则只针对 sale_scope=2（仅船供）；sale_scope=3（个人+船供）**本来就该**
# 出现在零售列表里，拿它做断言会得到假失败。所以要显式挑出 sale_scope=2 的样本，
# 不能想当然地用列表第一条。
# 用大页拿到全部船供商品，避免唯一样本不在默认首页时误判为「无样本」
api_get '/product/app/goodsspu/ship/page?current=1&size=200'
SHIP_ALL_BODY="${LAST_BODY}"

SHIP_ONLY_SPU="$(printf '%s' "${SHIP_ALL_BODY:-}" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for item in (data.get('data') or {}).get('records') or []:
    if str(item.get('saleScope')) == '2' and str(item.get('status')) == '1':
        print(item.get('spuId'))
        break
" 2>/dev/null)"

if [ -z "${SHIP_ONLY_SPU}" ]; then
  skip "当前分页内无 sale_scope=2 的上架商品，跳过零售隔离检查（P0-6b）"
else
  SHIP_ONLY_NAME="$(printf '%s' "${SHIP_ALL_BODY}" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for item in (data.get('data') or {}).get('records') or []:
    if str(item.get('spuId')) == sys.argv[1]:
        print(item.get('name') or '')
        break
" "${SHIP_ONLY_SPU}" 2>/dev/null)"
  log "  仅船供样本: ${SHIP_ONLY_NAME:-<未命名>} (${SHIP_ONLY_SPU})"

  api_get "/product/app/goodsspu/page?current=1&size=20&name=$(urlencode "${SHIP_ONLY_NAME}")"
  if [ "${LAST_STATUS}" != "200" ]; then
    bad "GET /product/app/goodsspu/page 返回 ${LAST_STATUS}"
  else
    hit="$(printf '%s' "${LAST_BODY}" | jfield 'data.records' 'id' | grep -cx "${SHIP_ONLY_SPU}" || true)"
    if [ "${hit}" = "0" ]; then
      ok "零售列表未出现仅船供商品（P0-6b）"
    else
      bad "零售列表出现了仅船供商品 spuId=${SHIP_ONLY_SPU}（P0-6b 未生效）"
    fi
  fi
fi

# ---------------------------------------------------------------------------
# 4. 共享购物车 C 端（P0-5）
# ---------------------------------------------------------------------------

log ''
log '4) 共享购物车 C 端接口'

api_get '/mall-order/app/shared-cart/my'
if [ "${LAST_STATUS}" != "200" ]; then
  bad "GET /mall-order/app/shared-cart/my 返回 ${LAST_STATUS}（P0-5 列表接口）"
else
  ok "GET /mall-order/app/shared-cart/my 返回 200"
  my_count="$(printf '%s' "${LAST_BODY}" | jfield 'data' 'id' | wc -l | tr -d ' ')"
  log "  当前账号参与 ${my_count} 个共享购物车"

  # 详情接口必须返回 viewer* 权限标记，前端据此决定按钮显隐
  existing="$(printf '%s' "${LAST_BODY}" | jfield 'data' 'id' | head -1)"
  if [ -n "${existing}" ]; then
    api_get "/mall-order/app/shared-cart/${existing}"
    if printf '%s' "${LAST_BODY}" | grep -q 'viewerIsOwner'; then
      ok "共享购物车详情返回 viewer* 权限标记"
    else
      bad "共享购物车详情缺少 viewer* 权限标记（前端将无法正确显隐按钮）"
    fi
  fi
fi

if [ -z "${VESSEL_ID:-}" ] || [ -z "${CALL_ID:-}" ]; then
  skip "缺少船舶+靠港上下文，跳过创建/关闭共享购物车"
else
  api_post '/mall-order/app/shared-cart' \
    "{\"vesselId\":\"${VESSEL_ID}\",\"vesselCallId\":\"${CALL_ID}\",\"remark\":\"smoke-test\"}"
  NEW_CART="$(printf '%s' "${LAST_BODY}" | jget 'data.id')"
  if [ "${LAST_STATUS}" = "200" ] && [ -n "${NEW_CART}" ]; then
    ok "创建共享购物车成功 id=${NEW_CART}"

    api_get "/mall-order/app/shared-cart/${NEW_CART}"
    if printf '%s' "${LAST_BODY}" | grep -q '"viewerIsOwner":true'; then
      ok "创建者被识别为发起人（viewerIsOwner=true）"
    else
      bad "创建者未被识别为发起人"
    fi

    api_get "/mall-order/app/shared-cart/${NEW_CART}/members"
    if [ "${LAST_STATUS}" = "200" ]; then
      ok "成员列表接口可用"
    else
      bad "GET /{id}/members 返回 ${LAST_STATUS}"
    fi

    api_get "/mall-order/app/shared-cart/${NEW_CART}/items"
    if [ "${LAST_STATUS}" = "200" ]; then
      ok "明细列表接口可用"
    else
      bad "GET /{id}/items 返回 ${LAST_STATUS}"
    fi

    api_post "/mall-order/app/shared-cart/${NEW_CART}/close" '{}'
    if [ "${LAST_STATUS}" = "200" ]; then
      ok "关闭共享购物车成功（冒烟数据已清理）"
    else
      bad "关闭共享购物车返回 ${LAST_STATUS}，请手工清理 id=${NEW_CART}"
    fi
  else
    bad "创建共享购物车失败：HTTP ${LAST_STATUS} ${LAST_BODY}"
  fi
fi

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------

log ''
log "结果: PASS=${PASS}  FAIL=${FAIL}  SKIP=${SKIP}"
if [ "${FAIL}" -gt 0 ]; then
  log '存在失败项，请对照上方 FAIL 行定位。'
  exit 1
fi
log '全部通过。'
log '注意：本脚本只覆盖接口层。UI 交互与端到端链路请按《E2E 验收清单》人工执行。'
