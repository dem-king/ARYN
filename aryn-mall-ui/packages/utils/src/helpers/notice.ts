/**
 * 站内信展示口径（管理端铃铛与收件箱共用）。
 *
 * 这里只放不依赖 Vue / 路由的纯函数：
 * - 分类：后端 `category`（ORDER / VESSEL_BIND_TODO / SYSTEM…）与 `sourceType`
 *   （ORDER_CREATE / ORDER_REFUND_APPLY / COD_PAY_REMIND…）两套字段并存，
 *   前端按「会话 > 业务来源 > 类目」统一收敛成图标和筛选所需的分类；
 * - 时间：后端 LocalDateTime 以 `yyyy-MM-dd HH:mm:ss` 字符串下发，
 *   Safari 不接受空格分隔，必须按字段解析，避免 `new Date(str)` 得到 Invalid Date。
 */

/** 通知分类：铃铛、收件箱和筛选项共用同一套枚举。 */
export type NoticeCategory =
  'conversation' | 'order' | 'refund' | 'system' | 'vessel';

export interface NoticeCategoryInput {
  /** 后端通知类目，如 ORDER / VESSEL_BIND_TODO / SYSTEM。 */
  category?: null | string;
  /** 铃铛里的提醒类型：客服会话与普通站内信区分。 */
  kind?: null | string;
  /** 后端业务来源，如 ORDER_CREATE / ORDER_REFUND_APPLY / COD_PAY_REMIND。 */
  sourceType?: null | string;
}

/** 来源优先级高于类目：退款通知的 category 也是 ORDER，只能靠 sourceType 拆开。 */
export function noticeCategoryOf(
  input: NoticeCategoryInput = {},
): NoticeCategory {
  if (input.kind === 'conversation') {
    return 'conversation';
  }
  const sourceType = (input.sourceType ?? '').toUpperCase();
  if (sourceType.includes('REFUND')) {
    return 'refund';
  }
  if (sourceType.includes('VESSEL')) {
    return 'vessel';
  }
  if (sourceType.startsWith('ORDER') || sourceType === 'COD_PAY_REMIND') {
    return 'order';
  }
  const category = (input.category ?? '').toUpperCase();
  if (category.includes('REFUND')) {
    return 'refund';
  }
  if (category.includes('VESSEL')) {
    return 'vessel';
  }
  if (category === 'ORDER' || category.includes('DELIVERY')) {
    return 'order';
  }
  return 'system';
}

/**
 * 消息类型：比分类更细一档，决定列表里的图标、颜色和类型标签。
 *
 * 「新订单」与「买家取消订单」同属 ORDER 分类，旧实现用同一个购物车图标，
 * 运营需要逐条读标题才能分辨；这里按 `sourceType` 拆到具体事件，前端据此
 * 选图标/色板/文案（分类只用于筛选项）。
 */
export type NoticeType =
  | 'cod-pay-remind'
  | 'conversation'
  | 'delivery-task'
  | 'order-cancelled'
  | 'order-created'
  | 'order-refund'
  | 'system'
  | 'vessel-bind';

export interface NoticeTypeInput extends NoticeCategoryInput {
  /** 兜底用：历史数据可能没有 sourceType，用标题关键词补足类型。 */
  title?: null | string;
}

/** 类型判定优先级：会话 > 具体业务来源 > 类目 > 标题关键词兜底。 */
export function noticeTypeOf(input: NoticeTypeInput = {}): NoticeType {
  if (input.kind === 'conversation') {
    return 'conversation';
  }
  const sourceType = (input.sourceType ?? '').toUpperCase();
  if (sourceType.includes('REFUND')) {
    return 'order-refund';
  }
  if (sourceType.includes('COD')) {
    return 'cod-pay-remind';
  }
  if (sourceType.includes('VESSEL')) {
    return 'vessel-bind';
  }
  if (sourceType.includes('DELIVERY')) {
    return 'delivery-task';
  }
  if (sourceType.includes('CANCEL')) {
    return 'order-cancelled';
  }
  if (sourceType.startsWith('ORDER')) {
    return 'order-created';
  }
  const category = (input.category ?? '').toUpperCase();
  if (category.includes('REFUND')) {
    return 'order-refund';
  }
  if (category.includes('VESSEL')) {
    return 'vessel-bind';
  }
  if (category.includes('DELIVERY')) {
    return 'delivery-task';
  }
  if (category === 'ORDER') {
    return 'order-created';
  }
  // 老通知没有 sourceType：标题里的业务关键词是最后可用的判据
  const title = input.title ?? '';
  if (title.includes('取消')) {
    return 'order-cancelled';
  }
  if (title.includes('退款')) {
    return 'order-refund';
  }
  if (title.includes('绑定')) {
    return 'vessel-bind';
  }
  if (title.includes('收款')) {
    return 'cod-pay-remind';
  }
  if (title.includes('订单')) {
    return 'order-created';
  }
  return 'system';
}

/** 后端时间字符串：`yyyy-MM-dd HH:mm:ss`，允许 `T` 分隔且秒可缺省。 */
const NOTICE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/;

/**
 * 解析通知时间戳（毫秒）。
 *
 * 解析失败返回 undefined，调用方回退展示后端原始字符串，不能因脏数据抛错。
 */
export function parseNoticeTime(raw?: null | string): number | undefined {
  if (!raw) {
    return undefined;
  }
  const matched = NOTICE_TIME_PATTERN.exec(raw.trim());
  if (!matched) {
    return undefined;
  }
  const [, year, month, day, hour, minute, second = '0'] = matched;
  const time = new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second),
  ).getTime();
  return Number.isNaN(time) ? undefined : time;
}

function pad(value: number) {
  return String(value).padStart(2, '0');
}

/**
 * 紧凑时间（铃铛列表用）：同年展示 `MM-DD HH:mm`，跨年展示 `YYYY-MM-DD`。
 *
 * @param raw 后端时间字符串
 * @param now 当前时间戳，测试可注入固定值
 */
export function formatNoticeTime(
  raw?: null | string,
  now: number = Date.now(),
): string {
  const time = parseNoticeTime(raw);
  if (time === undefined) {
    return raw ?? '';
  }
  const date = new Date(time);
  if (date.getFullYear() !== new Date(now).getFullYear()) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 完整时间（收件箱/悬浮提示用）：`YYYY-MM-DD HH:mm`。 */
export function formatNoticeDateTime(raw?: null | string): string {
  const time = parseNoticeTime(raw);
  if (time === undefined) {
    return raw ?? '';
  }
  const date = new Date(time);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
