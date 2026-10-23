import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface DiscountProps extends RetailComponentBaseProps {
  /** 每个活动最多展示的商品数（1-8，防止楼层过高） */
  goodsLimit: number;
  /** 是否展示活动倒计时 */
  showCountdown: boolean;
  /** 楼层头副标题，空串使用模块默认文案 */
  subtitle: string;
  title: string;
}

export function createDiscountDefaults(): DiscountProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 1,
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'placeholder',
    goodsLimit: 4,
    invalidStrategy: 'hide',
    showCountdown: true,
    subtitle: '',
    title: '限时折扣',
  };
}

export function validateDiscount(props: DiscountProps): string[] {
  const errors = validateRetailBase(props, 5);
  if (
    props.dataSource.mode === 'manual' &&
    !props.dataSource.targetIds?.length
  ) {
    errors.push('请选择折扣活动');
  }
  // 存量草稿没有该字段：读取侧回落默认 4，校验只在「配了非法值」时拦截
  const limit = props.goodsLimit;
  if (
    limit !== undefined &&
    (!Number.isInteger(limit) || limit < 1 || limit > 8)
  ) {
    errors.push('每活动商品数必须为 1-8 的整数');
  }
  return errors;
}
