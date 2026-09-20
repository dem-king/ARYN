#!/usr/bin/env bash
#
# 船舶自助绑定 · 接口端到端冒烟测试
#
# 覆盖三条通道与权限收敛（对应《船舶自助绑定-实施进度.md》P0-2）：
#   1. 已绑定用户可读：我的船舶 / 成员列表
#   2. 权限收敛：普通船员(2) 不能加人、不能生成邀请码、不能检索候选
#   3. 管理角色：采购确认人(3) 可以生成邀请码、可检索候选
#   4. 申请通道：已是成员时拒绝重复申请；不存在/无效邀请码被拒
#   5. 管理端审核接口带 @SaCheckPermission，未授权访问被拒
#
# 用法:
#   ./dev-tools/smoke-vessel-bind.sh
#   BASE_URL=http://localhost:9999 TENANT_ID=xxx ./dev-tools/smoke-vessel-bind.sh
#
# 环境变量:
#   BASE_URL       网关地址，默认 http://localhost:9999
#   MODE           cloud（默认）或 boot（改写为 /boot/路径）
#   TENANT_ID      租户ID，默认取 aryn-mall-uniapp/.env 的 VITE_TENANT_ID
#   CREW_PHONE     普通船员手机号，默认 18438306345（宇宙第一帅）
#   CONFIRMER_PHONE 采购确认人手机号，默认 17640212320
#   SMS_CODE       登录用短信验证码，默认 888888（脚本会写入 Redis db11）
#   MYSQL_CTN / REDIS_CTN / REDIS_PASS  容器名与 Redis 密码
#
# 退出码: 0 全部通过；1 存在失败项

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

BASE_URL="${BASE_URL:-http://localhost:9999}"
MODE="${MODE:-cloud}"
TENANT_ID="${TENANT_ID:-}"
CREW_PHONE="${CREW_PHONE:-18438306345}"
CONFIRMER_PHONE="${CONFIRMER_PHONE:-17640212320}"
SMS_CODE="${SMS_CODE:-888888}"
REDIS_CTN="${REDIS_CTN:-aryn-redis}"
REDIS_PASS="${REDIS_PASS:-redis}"
MYSQL_CTN="${MYSQL_CTN:-aryn-mysql}"
MYSQL_PASS="${MYSQL_PASS:-123456}"

PASS=0
FAIL=0
SKIP=0

log()  { printf '[bind-smoke] %s\n' "$*"; }
ok()   { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$*"; }
bad()  { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$*"; }
skip() { SKIP=$((SKIP + 1)); printf '  \033[33mSKIP\033[0m %s\n' "$*"; }

# ---------------------------------------------------------------------------
# JSON 取值（与 smoke-ship-supply.sh 保持一致，python3 解析，不依赖 jq）
# ---------------------------------------------------------------------------

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

# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------

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

# api <method> <path> [token] [data]
api() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local url="${BASE_URL}$(resolve_path "${path}")"
  local tmp
  tmp="$(mktemp)"
  local args=(-sS -o "${tmp}" -w '%{http_code}' -X "${method}" "${url}"
              -H 'Content-Type: application/json'
              -H "tenant-id: ${TENANT_ID}"
              -H 'platform-type: H5'
              -H 'app-id: mall')
  [ -n "${token}" ] && args+=(-H "satoken: ${token}")
  [ -n "${data}" ] && args+=(-d "${data}")

  LAST_STATUS="$(curl "${args[@]}" 2>/dev/null || printf '000')"
  LAST_BODY="$(cat "${tmp}")"
  rm -f "${tmp}"
}

# 业务是否成功：HTTP 200 且 code=0
biz_ok() { [ "${LAST_STATUS}" = "200" ] && printf '%s' "${LAST_BODY}" | grep -q '"code": *0'; }
# 业务是否失败（返回了业务错误信息）
biz_failed() { printf '%s' "${LAST_BODY}" | grep -qE '"code": *[^0]|"msg": *"[^"]+"'; }

# ---------------------------------------------------------------------------
# 登录：写 Redis 短信码后走 /toc-token/sms/login
# 说明：SmsValidateCodeFilter 校验 Redis 中的 sms_code_<phone>，本脚本按同样
#       键位写入测试码，属于正常的测试环境准备，不是绕过鉴权。
# ---------------------------------------------------------------------------

login() {
  local phone="$1" label="$2"
  docker exec "${REDIS_CTN}" redis-cli -n 11 -a "${REDIS_PASS}" --no-auth-warning \
    SET "sms_code_${phone}" "${SMS_CODE}" >/dev/null 2>&1

  api POST "/auth/toc-token/sms/login?code=${SMS_CODE}&phone=${phone}" "" \
    "{\"phone\":\"${phone}\",\"code\":\"${SMS_CODE}\",\"platformType\":\"H5\"}"

  local token
  token="$(printf '%s' "${LAST_BODY}" | jget 'data.tokenValue')"
  if [ -z "${token}" ]; then
    log "  ${label} 登录失败：${LAST_BODY}"
    return 1
  fi
  printf '%s' "${token}"
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

# 船舶域所在库：cloud 为独立库，boot 为单库
case "${MODE}" in
  boot) VESSEL_DB="aryn_boot" ;;
  *)    VESSEL_DB="aryn_vessel" ;;
esac

# 前置检查：新增租户表必须已登记进运行中的 Nacos 配置，否则服务会启动即崩、
# 无限重启（TenantSchemaValidator 双向严格校验）。这里直接探测服务存活。
log ''
log '0) 服务可用性前置检查'
api GET '/vessel/app/my-vessels'
if [ "${LAST_STATUS}" = "000" ]; then
  bad "vessel 服务不可达（HTTP 000）。若刚重启过，请确认 Nacos 的 hx.tenant.tables 已包含 vessel_bind_apply / vessel_invite_code"
  log '结果: PASS='"${PASS}"'  FAIL='"${FAIL}"'  SKIP='"${SKIP}"'（前置检查失败，后续用例跳过）'
  exit 1
fi
ok "vessel 服务可达（HTTP ${LAST_STATUS}，未带 token 返回鉴权提示属正常）"

CREW_TOKEN="$(login "${CREW_PHONE}" '普通船员')" || exit 1
ok "普通船员登录成功（${CREW_PHONE}）"

CONFIRMER_TOKEN="$(login "${CONFIRMER_PHONE}" '采购确认人')" || log "  ⚠ 采购确认人登录失败，相关用例将跳过"

# ---------------------------------------------------------------------------
# 1. 已绑定用户可读
# ---------------------------------------------------------------------------

log ''
log '1) 已绑定用户读接口'

api GET '/vessel/app/my-vessels' "${CREW_TOKEN}"
VESSEL_ID="$(printf '%s' "${LAST_BODY}" | jfield 'data' 'id' | head -1)"

if [ -z "${VESSEL_ID}" ]; then
  bad "普通船员读不到我的船舶（种子数据缺失？）"
  log '结果: PASS='"${PASS}"'  FAIL='"${FAIL}"'  SKIP='"${SKIP}"'（缺少船舶上下文，无法继续）'
  exit 1
fi
ok "我的船舶可用：${VESSEL_ID}"

api GET "/vessel/app/${VESSEL_ID}/members" "${CREW_TOKEN}"
member_count="$(printf '%s' "${LAST_BODY}" | jfield 'data' 'userId' | wc -l | tr -d ' ')"
if biz_ok && [ "${member_count}" -gt 0 ]; then
  ok "成员列表可见（${member_count} 人）—— 成员访问权正常"
else
  bad "成员列表读取失败：HTTP ${LAST_STATUS} ${LAST_BODY}"
fi

# 手机号脱敏：成员列表不得返回完整手机号
if printf '%s' "${LAST_BODY}" | grep -qE '"phone": *"[0-9]{11}"'; then
  bad "成员列表返回了未脱敏的完整手机号"
else
  ok "成员列表手机号已脱敏"
fi

# ---------------------------------------------------------------------------
# 2. 权限收敛：普通船员(2) 不能管理成员
# ---------------------------------------------------------------------------

log ''
log '2) 权限收敛（普通船员角色=2）'

api POST "/vessel/app/${VESSEL_ID}/members" "${CREW_TOKEN}" \
  '{"userId":"2040654277629796353","memberRole":"2"}'
if biz_failed && ! biz_ok; then
  msg="$(printf '%s' "${LAST_BODY}" | jget 'msg')"
  ok "普通船员加人被拒：${msg}"
else
  bad "普通船员竟然可以添加成员（权限收敛失效）"
fi

api POST "/vessel/app/${VESSEL_ID}/invite-codes" "${CREW_TOKEN}"
if biz_failed && ! biz_ok; then
  msg="$(printf '%s' "${LAST_BODY}" | jget 'msg')"
  ok "普通船员生成邀请码被拒：${msg}"
else
  bad "普通船员竟然可以生成邀请码（权限收敛失效）"
  CODE_ID="$(printf '%s' "${LAST_BODY}" | jget 'data.id')"
  [ -n "${CODE_ID}" ] && api DELETE "/vessel/app/invite-codes/${CODE_ID}" "${CREW_TOKEN}"
fi

api GET "/vessel/app/${VESSEL_ID}/member-candidates?keyword=184" "${CREW_TOKEN}"
if biz_failed && ! biz_ok; then
  msg="$(printf '%s' "${LAST_BODY}" | jget 'msg')"
  ok "普通船员检索候选被拒：${msg}"
else
  bad "普通船员竟然可以检索候选用户"
fi

# 非成员越权：用一个不属于该船的用户读取成员列表
api GET '/vessel/app/9610000000000000001/members' ''
if printf '%s' "${LAST_BODY}" | grep -qE '"code": *401|未登录|未能读取'; then
  ok "未登录访问成员列表被拒"
else
  bad "未登录竟能访问成员列表：${LAST_BODY}"
fi

# ---------------------------------------------------------------------------
# 3. 管理角色：采购确认人(3) 具备管理能力
# ---------------------------------------------------------------------------

log ''
log '3) 管理角色能力（采购确认人角色=3）'

GENERATED_CODE_ID=""
GENERATED_CODE=""
if [ -z "${CONFIRMER_TOKEN:-}" ]; then
  skip "采购确认人未登录，跳过管理能力用例"
else
  api GET "/vessel/app/${VESSEL_ID}/member-candidates?keyword=184" "${CONFIRMER_TOKEN}"
  if biz_ok; then
    cand_count="$(printf '%s' "${LAST_BODY}" | jfield 'data' 'userId' | wc -l | tr -d ' ')"
    ok "采购确认人可检索候选用户（${cand_count} 条）"
  else
    bad "采购确认人检索候选失败：HTTP ${LAST_STATUS} ${LAST_BODY}"
  fi

  api POST "/vessel/app/${VESSEL_ID}/invite-codes?expireHours=24" "${CONFIRMER_TOKEN}"
  GENERATED_CODE="$(printf '%s' "${LAST_BODY}" | jget 'data.code')"
  GENERATED_CODE_ID="$(printf '%s' "${LAST_BODY}" | jget 'data.id')"
  if biz_ok && [ -n "${GENERATED_CODE}" ]; then
    ok "采购确认人可生成邀请码：${GENERATED_CODE}（6 位）"
    if printf '%s' "${GENERATED_CODE}" | grep -qE '^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$'; then
      ok "邀请码字符集正确（无 0/O/1/I 等易混字符）"
    else
      bad "邀请码含易混字符或长度不符：${GENERATED_CODE}"
    fi
  else
    bad "采购确认人生成邀请码失败：HTTP ${LAST_STATUS} ${LAST_BODY}"
  fi
fi

# ---------------------------------------------------------------------------
# 4. 申请通道边界
# ---------------------------------------------------------------------------

log ''
log '4) 申请通道边界'

# 已是成员再申请 → 应被拒
api POST '/vessel/app/bind-applies' "${CREW_TOKEN}" \
  '{"applyRole":"2","applyVesselName":"测试船"}'
if biz_failed && ! biz_ok; then
  msg="$(printf '%s' "${LAST_BODY}" | jget 'msg')"
  ok "已是成员时重复申请被拒：${msg}"
else
  bad "已是成员仍可提交申请（重复绑定拦截失效）"
  AID="$(printf '%s' "${LAST_BODY}" | jget 'data.id')"
  [ -n "${AID}" ] && api POST "/vessel/app/bind-applies/${AID}/cancel" "${CREW_TOKEN}"
fi

# 非法申请角色（既非船员 2 也非业务员 4）
api POST '/vessel/app/bind-applies' "${CREW_TOKEN}" \
  '{"applyRole":"9","applyVesselName":"测试船"}'
if biz_failed && ! biz_ok; then
  msg="$(printf '%s' "${LAST_BODY}" | jget 'msg')"
  ok "非法申请角色被拒：${msg}"
else
  bad "非法 applyRole=9 竟然通过（角色白名单失效）"
fi

# 我的申请列表
api GET '/vessel/app/bind-applies/my' "${CREW_TOKEN}"
if biz_ok; then
  ok "我的申请列表可用"
else
  bad "我的申请列表失败：HTTP ${LAST_STATUS} ${LAST_BODY}"
fi

# ---------------------------------------------------------------------------
# 5. 邀请码兑换边界
# ---------------------------------------------------------------------------

log ''
log '5) 邀请码兑换边界'

api POST '/vessel/app/invite-codes/redeem?code=ZZZZZZ' "${CREW_TOKEN}"
if biz_failed && ! biz_ok; then
  msg="$(printf '%s' "${LAST_BODY}" | jget 'msg')"
  ok "不存在的邀请码被拒：${msg}"
else
  bad "不存在的邀请码竟然可兑换"
fi

# 已是成员用有效码兑换 → 应被拒（重复绑定拦截）
if [ -n "${GENERATED_CODE:-}" ]; then
  api POST "/vessel/app/invite-codes/redeem?code=${GENERATED_CODE}" "${CREW_TOKEN}"
  if biz_failed && ! biz_ok; then
    msg="$(printf '%s' "${LAST_BODY}" | jget 'msg')"
    ok "已是成员时兑换邀请码被拒：${msg}"
  else
    bad "已是成员仍能通过邀请码重复加入（重复绑定拦截失效）"
  fi
else
  skip "无有效邀请码，跳过重复兑换用例"
fi

# 撤销自己生成的邀请码（仅生成人可撤销；船员无权 → 应被拒）
if [ -n "${GENERATED_CODE_ID:-}" ]; then
  api DELETE "/vessel/app/invite-codes/${GENERATED_CODE_ID}" "${CREW_TOKEN}"
  if biz_failed && ! biz_ok; then
    msg="$(printf '%s' "${LAST_BODY}" | jget 'msg')"
    ok "他人撤销邀请码被拒：${msg}"
  else
    bad "非生成人竟能撤销邀请码"
  fi

  # 生成人本人撤销 → 应成功
  api DELETE "/vessel/app/invite-codes/${GENERATED_CODE_ID}" "${CONFIRMER_TOKEN}"
  if biz_ok; then
    ok "生成人撤销自己的邀请码成功"
  else
    bad "生成人撤销失败：HTTP ${LAST_STATUS} ${LAST_BODY}"
  fi

  # 撤销只置状态、不删行；冒烟测试留下残留数据会干扰后续统计，这里显式清理。
  if docker exec "${MYSQL_CTN}" mysql -uroot -p"${MYSQL_PASS}" -e \
      "DELETE FROM \`${VESSEL_DB}\`.\`vessel_invite_code\` WHERE \`id\`='${GENERATED_CODE_ID}';" >/dev/null 2>&1; then
    ok "邀请码测试数据已清理"
  else
    skip "邀请码测试数据清理失败（不影响结论，可手工删除 id=${GENERATED_CODE_ID}）"
  fi
fi

# ---------------------------------------------------------------------------
# 6. 管理端审核接口权限
# ---------------------------------------------------------------------------

log ''
log '6) 管理端审核接口'

api GET '/vessel/admin/bind-applies/page' "${CREW_TOKEN}"
if printf '%s' "${LAST_BODY}" | grep -qE '无此权限|403|未授权|401'; then
  ok "C 端 token 访问管理端审核接口被拒（权限边界正确）"
else
  bad "C 端 token 竟能访问管理端接口：${LAST_BODY}"
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
log '注意：管理端「审核通过建船」与 App 端 UI 交互仍需人工按《E2E 验收清单》执行。'
