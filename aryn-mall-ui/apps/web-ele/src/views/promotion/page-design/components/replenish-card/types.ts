import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

/**
 * 补给单卡片（首页「今日补给单」）。
 *
 * 数据来自共享购物车摘要接口
 * （/mall-order/app/shared-cart/active-summary），内容由
 * 「谁在看 + 当前靠港」决定，因此没有可手选数据源。
 *
 * 刻意不提供「已采/还差/进度」配置：提单只是一份需求清单，
 * 采没采、采到什么程度由线下沟通，系统里没有这个事实来源。
 * 给出进度配置项等于让运营配一个假数据。
 */
export interface ReplenishCardProps extends RetailComponentBaseProps {
  showBatchAdd: boolean;
  showPreview: boolean;
  title: string;
}

export function createReplenishCardDefaults(): ReplenishCardProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 1,
    dataSource: { mode: 'current-tenant' },
    emptyStrategy: 'hide',
    invalidStrategy: 'hide',
    showBatchAdd: true,
    showPreview: true,
    title: '今日补给单',
  };
}

export function validateReplenishCard(props: ReplenishCardProps): string[] {
  const errors = validateRetailBase(props, 1);
  if (props.dataSource?.mode !== 'current-tenant') {
    errors.push('补给单卡片展示当前进行中的共享购物车，数据来源不可修改');
  }
  if (!props.title?.trim()) {
    errors.push('补给单标题不能为空');
  }
  return errors;
}
