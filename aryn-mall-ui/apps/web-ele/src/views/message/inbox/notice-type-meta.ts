/**
 * 收件箱消息类型的图标 / 配色 / 文案。
 *
 * 与铃铛弹层（`packages/effects/layouts` 里的 Notification）保持同一套类型口径：
 * `@vben/utils` 的 `noticeTypeOf` 负责判定类型，这里只负责管理端呈现
 * （lucide 图标 + 十六进制色值，页面用 CSS 变量消费）。
 *
 * 文案与铃铛 i18n 的 `ui.widgets.type*` 保持一致；收件箱是中文运营后台页面，
 * 因此直接写中文，避免为了 8 个标签引入半套 i18n。
 */
import type { Component } from 'vue';

import type { NoticeType } from '@vben/utils';

import {
  CircleX,
  Info,
  MessageSquare,
  RotateCw,
  Ship,
  ShoppingCart,
  Truck,
  Wallet,
} from '@vben/icons';
import { noticeTypeOf } from '@vben/utils';

export interface NoticeTypeMeta {
  /** 图标主色（用于图标色块与类型标签文字）。 */
  color: string;
  icon: Component;
  /** 类型标签文案，与铃铛 i18n 文案一致。 */
  label: string;
  /** 图标色块 / 标签的浅色底。 */
  soft: string;
}

export const NOTICE_TYPE_META: Record<NoticeType, NoticeTypeMeta> = {
  'cod-pay-remind': {
    color: '#059669',
    icon: Wallet,
    label: '待收款',
    soft: 'rgb(5 150 105 / 10%)',
  },
  'delivery-task': {
    color: '#7c3aed',
    icon: Truck,
    label: '配送任务',
    soft: 'rgb(124 58 237 / 10%)',
  },
  'order-cancelled': {
    color: '#e11d48',
    icon: CircleX,
    label: '已取消',
    soft: 'rgb(225 29 72 / 10%)',
  },
  'order-created': {
    color: '#2563eb',
    icon: ShoppingCart,
    label: '新订单',
    soft: 'rgb(37 99 235 / 10%)',
  },
  'order-refund': {
    color: '#d97706',
    icon: RotateCw,
    label: '退款审核',
    soft: 'rgb(217 119 6 / 10%)',
  },
  'vessel-bind': {
    color: '#0891b2',
    icon: Ship,
    label: '绑定申请',
    soft: 'rgb(8 145 178 / 10%)',
  },
  conversation: {
    color: '#059669',
    icon: MessageSquare,
    label: '客服',
    soft: 'rgb(5 150 105 / 10%)',
  },
  system: {
    color: '#64748b',
    icon: Info,
    label: '系统',
    soft: 'rgb(100 116 139 / 10%)',
  },
};

/** 收件箱卡片按类型取图标与配色。 */
export function noticeTypeMetaOf(input: {
  category?: null | string;
  kind?: null | string;
  sourceType?: null | string;
  title?: null | string;
}): NoticeTypeMeta {
  return NOTICE_TYPE_META[noticeTypeOf(input)];
}

/** 类型标签文案（模板里给 aria-label / 兜底文案复用）。 */
export function noticeTypeLabelOf(type: NoticeType): string {
  return NOTICE_TYPE_META[type].label;
}
