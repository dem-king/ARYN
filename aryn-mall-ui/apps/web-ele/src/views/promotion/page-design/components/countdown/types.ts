import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface CountdownProps extends RetailComponentBaseProps {
  completedText: string;
  targetTime: string;
  title: string;
}

export function createCountdownDefaults(): CountdownProps {
  return {
    commonStyle: createRetailCommonStyle(),
    completedText: '活动已结束',
    count: 1,
    dataSource: { mode: 'manual' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'placeholder',
    targetTime: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    title: '距活动结束',
  };
}

export function validateCountdown(props: CountdownProps): string[] {
  const errors = validateRetailBase(props, 1);
  if (!props.targetTime || Number.isNaN(Date.parse(props.targetTime))) {
    errors.push('请设置有效的目标时间');
  }
  return errors;
}
