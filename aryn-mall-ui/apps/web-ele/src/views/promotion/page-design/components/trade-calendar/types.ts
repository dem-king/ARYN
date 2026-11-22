import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';
import type { DecorationLink } from '../../../page-designer/schema/types';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';
import { validateDecorationLink } from '../../../page-designer/utils/link-utils';

/** 内置档期特供会场页（与 C 端 pages.json 的 promotion/calendar-special/index 对应） */
export const CALENDAR_SPECIAL_PATH =
  '/sub-pages/promotion/calendar-special/index';

/**
 * 采购档期特供楼层（2026-11-14 B2B，2026-11-15 补「查看全部」入口）。
 *
 * 数据源固定 automatic：预览与 C 端同源拉取当前生效档期及特供商品（/promotion/app/calendar/*），
 * 无档期/无特供按 emptyStrategy 处理（默认隐藏整楼）。count = 特供商品展示上限。
 * 「查看全部」默认指向内置会场页，运营可改成任意装修链接或关闭入口。
 */
export interface TradeCalendarProps extends RetailComponentBaseProps {
  /** 「查看全部」链接；null/空 → 回落内置会场页 */
  moreLink: DecorationLink | null;
  /** 是否展示「查看全部」入口 */
  showMore: boolean;
  title: string;
}

export function createTradeCalendarDefaults(): TradeCalendarProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 10,
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'hide',
    invalidStrategy: 'hide',
    moreLink: { params: {}, path: CALENDAR_SPECIAL_PATH, type: 'custom' },
    showMore: true,
    title: '',
  };
}

export function validateTradeCalendar(props: TradeCalendarProps): string[] {
  const errors = validateRetailBase(props, 50);
  if (props.count < 1 || props.count > 50) {
    errors.push('特供商品展示数量需在 1-50 之间');
  }
  // 入口关闭时不校验链接；开启且配了类型才校验，存量空值由 C 端回落会场页
  if (props.showMore !== false && props.moreLink?.type) {
    errors.push(...validateDecorationLink(props.moreLink));
  }
  return errors;
}
