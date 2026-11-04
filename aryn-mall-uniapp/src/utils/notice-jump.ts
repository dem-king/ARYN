/**
 * 站内信跳转解析（C 端）。
 *
 * 后端 `message_notice` 的跳转口径有两代：
 * - 新口径 `jumpPayload = {"route":"/sub-pages/order/order-detail/index?id=..."}`
 *   （INTERNAL_ROUTE，运营/业务直接下发端内路由，也可能有历史裸路由字符串）；
 * - 业务口径 `jumpPayload = {"bizType":"ORDER","bizId":"..."}`
 *   （BIZ_DETAIL，常见于买家侧：订单/退款/优惠券到账通知），需要按 bizType 映射。
 * 另有 `cardPayload`（订单类通知的 `{orderId}`、退款类 `{refundId}`、通用
 * `{bizType,bizId}`）与 `sourceType`（如 `ORDER_REFUND`）可作兜底。
 *
 * 安全约束：只允许 C 端站内路由（`/pages/**`、`/sub-pages/**`，与路由白名单
 * 同源口径），拒绝外链、协议相对地址、反斜杠、路径穿越与 `javascript:`/`data:`
 * 等协议——通知载荷来自运营配置与后端事件，不能变成开放重定向的入口。
 * 无法安全映射时返回 `undefined`，由页面退化为「只标记已读」。
 */
export interface NoticeJumpSource {
  cardPayload?: null | string
  jumpPayload?: null | string
  jumpType?: null | string
  sourceType?: null | string
}

/** C 端业务类型：与后端 `MessageSendCommand.bizType` / 历史 `jumpType` 对齐 */
export type NoticeBizType = 'COUPON' | 'ORDER' | 'PRODUCT' | 'REFUND'

/** 仅放行 C 端站内页面：主包 /pages 与分包 /sub-pages */
const C_PAGE_PATH_PATTERN = /^\/(?:pages|sub-pages)\//

/**
 * 业务类型 → C 端页面。
 *
 * 参数名与页面 `onLoad` 实参对齐（不是管理端口径）：
 * - 订单详情 `/sub-pages/order/order-detail/index` 读 `id`
 * - 退款详情 `/sub-pages/order/order-refunds/refunds-detail/index` 读 `id`
 * - 商品详情 `/sub-pages/product/goods-detail/index` 读 `id`
 * - 优惠券到账通知跳「我的优惠券」：`coupon-user` 页只按 status 分页，
 *   `couponUser` id 不是页面参数，因此只落页面不拼 id（领券中心是另一个页面）
 */
const BIZ_ROUTES: Record<NoticeBizType, string> = {
  COUPON: '/sub-pages/promotion/coupon/coupon-user/index',
  ORDER: '/sub-pages/order/order-detail/index',
  PRODUCT: '/sub-pages/product/goods-detail/index',
  REFUND: '/sub-pages/order/order-refunds/refunds-detail/index',
}

const SCHEME_PATTERN = /^[a-z][\w+.-]*:/i
const MAX_ID_LENGTH = 128

function readText(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

/** 业务 ID 只接受短字符串：过长/空的值按脏数据丢弃，避免拼出超长 URL */
function readId(value: unknown): string | undefined {
  const text = readText(value)
  return text && text.length <= MAX_ID_LENGTH ? text : undefined
}

/**
 * 是否 C 端可安全跳转的站内路由。
 *
 * 判定顺序：先拒绝协议/协议相对/反斜杠/空白，再对解码后的整串与路径段做
 * 路径穿越检查，最后才比对白名单前缀——顺序反过来会让 `//evil.com` 之类
 * 因为「看起来像路径」被放过。
 */
export function isSafeCRoute(route?: null | string): boolean {
  const value = route?.trim()
  if (!value || !value.startsWith('/'))
    return false
  // 协议相对地址 //host/path、反斜杠与空白：H5 端会被浏览器当成外链
  if (value.startsWith('//') || value.includes('\\') || /\s/.test(value))
    return false
  // 显式协议（http(s):、javascript:、data:…）：以 / 开头已挡住，双保险防前缀绕过
  if (SCHEME_PATTERN.test(value))
    return false

  let decoded = value
  try {
    decoded = decodeURIComponent(value)
  }
  catch {
    // 非法百分号编码：无法确认目标，直接拒绝
    return false
  }
  if (decoded.includes('\\') || decoded.includes('..'))
    return false

  const path = value.split(/[?#]/, 1)[0] ?? ''
  if (path.includes('..') || path.includes(':'))
    return false
  return C_PAGE_PATH_PATTERN.test(path)
}

/** jumpPayload / cardPayload 解析：JSON 对象为主，历史数据有裸路由字符串 */
function parsePayload(raw?: null | string): Record<string, unknown> {
  if (!raw)
    return {}
  const value = raw.trim()
  // 历史裸路由字符串（腾讯文档口径里的「route 裸串」）
  if (value.startsWith('/'))
    return { route: value }
  try {
    const parsed: unknown = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {}
  }
  catch {
    return {}
  }
}

/**
 * bizType 归一：兼容 `ORDER_PAY` / `ORDER_REFUND` / `coupon` 等写法。
 *
 * REFUND 必须先于 ORDER 判断（`ORDER_REFUND_APPLY` 是退款不是订单）；
 * 纯跳转类型（`INTERNAL_ROUTE` / `BIZ_DETAIL` / `NONE`）自动得到 undefined。
 */
function normalizeBizType(value?: null | string): NoticeBizType | undefined {
  const text = value?.trim().toUpperCase()
  if (!text)
    return undefined
  if (text.includes('REFUND'))
    return 'REFUND'
  if (text.includes('PRODUCT') || text.includes('GOODS') || text.includes('SPU'))
    return 'PRODUCT'
  if (text.includes('COUPON'))
    return 'COUPON'
  if (text.includes('ORDER'))
    return 'ORDER'
  return undefined
}

function withId(path: string, id: string) {
  return `${path}?id=${encodeURIComponent(id)}`
}

/** 按业务类型挑业务 ID：字段优先级由各业务经手过的历史口径决定 */
function pickBizId(
  bizType: NoticeBizType,
  jump: Record<string, unknown>,
  card: Record<string, unknown>,
): string | undefined {
  if (bizType === 'REFUND') {
    // 退款历史数据曾把退款单 ID 塞进 cardPayload.orderId，退款类通知以此兜底
    return readId(card.refundId)
      ?? readId(jump.bizId)
      ?? readId(jump.refundId)
      ?? readId(card.bizId)
      ?? readId(card.orderId)
  }
  if (bizType === 'PRODUCT') {
    return readId(card.productId)
      ?? readId(card.spuId)
      ?? readId(jump.bizId)
      ?? readId(jump.productId)
      ?? readId(card.bizId)
  }
  if (bizType === 'ORDER') {
    return readId(jump.bizId)
      ?? readId(jump.orderId)
      ?? readId(card.orderId)
      ?? readId(card.bizId)
  }
  // 优惠券只落券包页，不消费 ID
  return readId(jump.bizId) ?? readId(card.bizId)
}

/**
 * 解析一条通知点击后应跳转的 C 端路由；`undefined` 表示无可跳转目标。
 *
 * 优先级：
 * 1. `jumpPayload.route` 安全透传（INTERNAL_ROUTE / 裸路由，含共享购物车、
 *    船舶绑定、秒杀提醒等端内深链）；
 * 2. `bizType`（jumpPayload → jumpType → cardPayload）映射订单/退款/商品/优惠券；
 * 3. `cardPayload` 裸 ID 兜底（orderId/refundId/productId）。
 */
export function resolveNoticeJump(
  source: NoticeJumpSource = {},
): string | undefined {
  const jump = parsePayload(source.jumpPayload)
  const card = parsePayload(source.cardPayload)
  const jumpType = readText(source.jumpType)?.toUpperCase()
  const sourceType = readText(source.sourceType)?.toUpperCase() ?? ''

  // 1) 端内路由直接透传：只放行 C 端白名单，管理端路由（/order/detail 等）会落空
  const route = readText(jump.route)
  if (route && isSafeCRoute(route))
    return route

  // 2) 业务类型映射：历史数据可能把业务类型写进 jumpType，一并兼容
  const bizType = normalizeBizType(readText(jump.bizType))
    ?? normalizeBizType(jumpType)
    ?? normalizeBizType(readText(card.bizType))

  if (bizType === 'COUPON')
    return BIZ_ROUTES.COUPON

  if (bizType) {
    const bizId = pickBizId(bizType, jump, card)
    // 有业务类型却没有可用 ID：不猜目标，交给下面的 cardPayload 兜底
    if (bizId)
      return withId(BIZ_ROUTES[bizType], bizId)
  }

  // 3) cardPayload 兜底：订单/退款/商品卡片都只带业务 ID
  const cardRefundId = readId(card.refundId)
  if (cardRefundId)
    return withId(BIZ_ROUTES.REFUND, cardRefundId)
  const cardOrderId = readId(card.orderId)
  if (cardOrderId) {
    // 退款类通知的历史 cardPayload 用 orderId 键承载退款单 ID
    return sourceType.includes('REFUND')
      ? withId(BIZ_ROUTES.REFUND, cardOrderId)
      : withId(BIZ_ROUTES.ORDER, cardOrderId)
  }
  const cardProductId = readId(card.productId) ?? readId(card.spuId)
  if (cardProductId)
    return withId(BIZ_ROUTES.PRODUCT, cardProductId)
  return undefined
}
