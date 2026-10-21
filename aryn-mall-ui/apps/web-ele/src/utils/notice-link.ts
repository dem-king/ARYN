/**
 * 站内信跳转解析（管理端）。
 *
 * 后端在 `message_notice` 里有两代跳转口径，前端必须都认：
 * - 新口径：`jumpPayload = {"route":"/order/detail?id=..."}`，直接就是管理端详情路由；
 * - 业务口径：`jumpPayload = {"bizType":"ORDER","bizId":"..."}`，多见于买家侧/C 端，
 *   管理端按 bizType 映射到对应详情页；
 * - 历史口径：订单通知的 route 曾是列表页 `/order/order-info`，退款通知的
 *   cardPayload 仍用 orderId 键承载退款单 ID，只有 `sourceType` 能区分。
 *
 * 安全约束：只允许跳转管理端站内路由（拒绝 `//`、协议、反斜杠、C 端
 * `/pages/**`、`/sub-pages/**`），避免通知载荷变成开放重定向。
 */

/** 跳转解析所需的通知字段（收件箱 VO 与铃铛 item 字段的超集）。 */
export interface NoticeLinkSource {
  cardPayload?: null | string;
  jumpPayload?: null | string;
  jumpType?: null | string;
  sourceType?: null | string;
}

interface JumpPayload {
  bizId?: string;
  bizType?: string;
  route?: string;
}

/** 管理端存在的业务路由前缀；与之无关的 /pages/**、/sub-pages/** 一律不跳。 */
const INTERNAL_ROUTE_PREFIXES = [
  '/analytics',
  '/dashboard',
  '/delivery/',
  '/fulfillment/',
  '/gen/',
  '/message/',
  '/miniapp/',
  '/order/',
  '/pay/',
  '/platform/',
  '/product/',
  '/promotion/',
  '/social/',
  '/upms/',
  '/user/',
  '/vessel/',
];

const ORDER_LIST_PATH = '/order/order-info';
const ORDER_DETAIL_PATH = '/order/detail';
const REFUND_LIST_PATH = '/order/order-refund';

function readText(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function parseJsonObject(raw?: null | string): Record<string, unknown> {
  if (!raw) {
    return {};
  }
  try {
    const value: unknown = JSON.parse(raw);
    return value && typeof value === 'object' && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

/** 兼容旧数据的裸路由字符串（非 JSON 时按 `/xxx` 处理）。 */
export function parseJumpPayload(raw?: null | string): JumpPayload {
  if (!raw) {
    return {};
  }
  const value = raw.trim();
  if (value.startsWith('/')) {
    return { route: value };
  }
  const payload = parseJsonObject(value);
  return {
    bizId: readText(payload.bizId),
    bizType: readText(payload.bizType)?.toUpperCase(),
    route: readText(payload.route),
  };
}

/** 只放行管理端站内路由；外部地址、协议相对地址与 C 端页面全部拒绝。 */
export function isSafeInternalRoute(route?: null | string): boolean {
  const value = route?.trim();
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return false;
  }
  if (value.includes('\\')) {
    return false;
  }
  const path = value.split(/[?#]/, 1)[0] ?? '';
  // 路由里出现协议或路径穿越（含编码后的 ..）都按不可信处理
  if (path.includes(':') || path.includes('..')) {
    return false;
  }
  return INTERNAL_ROUTE_PREFIXES.some((prefix) =>
    prefix.endsWith('/')
      ? path.startsWith(prefix)
      : path === prefix || path.startsWith(`${prefix}/`),
  );
}

function withQuery(path: string, key: string, id: string) {
  return `${path}?${key}=${encodeURIComponent(id)}`;
}

function isRefundSource(sourceType?: null | string) {
  return (sourceType ?? '').toUpperCase().includes('REFUND');
}

/**
 * 解析一条通知点击后应跳转的管理端路由。
 *
 * 返回 `undefined` 表示这条通知没有可跳转的业务页面（纯系统通告），
 * 此时点击只标记已读，不应给用户「点了没反应」的错觉。
 */
export function resolveNoticeLink(
  source: NoticeLinkSource = {},
): string | undefined {
  const jump = parseJumpPayload(source.jumpPayload);
  const card = parseJsonObject(source.cardPayload);
  const refundSource = isRefundSource(source.sourceType);
  const orderId = readText(card.orderId);
  const cardBizId = readText(card.bizId);
  const cardBizType = readText(card.bizType)?.toUpperCase();

  const route = jump.route?.trim();
  if (route && isSafeInternalRoute(route)) {
    const path = route.split(/[?#]/, 1)[0];
    // 历史订单通知指向列表页：详情页已存在（/order/detail?id=），升级成直达
    if (path === ORDER_LIST_PATH && orderId && !refundSource) {
      return withQuery(ORDER_DETAIL_PATH, 'id', orderId);
    }
    // 历史退款通知指向退单列表：cardPayload.orderId 实际承载退款单 ID
    if (path === REFUND_LIST_PATH) {
      const refundId =
        readText(card.refundId) ?? (refundSource ? orderId : undefined);
      if (refundId) {
        return withQuery(REFUND_LIST_PATH, 'refundId', refundId);
      }
    }
    return route;
  }

  const bizType = jump.bizType ?? cardBizType;
  const bizId = jump.bizId ?? cardBizId;
  if (bizType === 'ORDER' && bizId) {
    return withQuery(ORDER_DETAIL_PATH, 'id', bizId);
  }
  if (bizType === 'REFUND' && bizId) {
    return withQuery(REFUND_LIST_PATH, 'refundId', bizId);
  }

  // 最后回落到 cardPayload：订单类卡片键名固定为 orderId
  if (orderId && refundSource) {
    return withQuery(REFUND_LIST_PATH, 'refundId', orderId);
  }
  if (orderId) {
    return withQuery(ORDER_DETAIL_PATH, 'id', orderId);
  }
  return undefined;
}
