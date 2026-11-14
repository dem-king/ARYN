import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

/**
 * 采购档期特供楼层（2026-11-14 B2B）。
 *
 * 数据源固定 automatic：预览与 C 端同源拉取当前生效档期及特供商品（/promotion/app/calendar/*），
 * 无档期/无特供按 emptyStrategy 处理（默认隐藏整楼）。count = 特供商品展示上限。
 */
export interface TradeCalendarProps extends RetailComponentBaseProps {
  title: string;
}

export function createTradeCalendarDefaults(): TradeCalendarProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 10,
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'hide',
    invalidStrategy: 'hide',
    title: '',
  };
}

export function validateTradeCalendar(props: TradeCalendarProps): string[] {
  const errors = validateRetailBase(props, 50);
  if (props.count < 1 || props.count > 50) {
    errors.push('特供商品展示数量需在 1-50 之间');
  }
  return errors;
}
