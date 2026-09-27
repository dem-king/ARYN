import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface DiscountProps extends RetailComponentBaseProps {
  /** 是否展示活动倒计时 */
  showCountdown: boolean;
  title: string;
}

export function createDiscountDefaults(): DiscountProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 1,
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    showCountdown: true,
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
  return errors;
}
