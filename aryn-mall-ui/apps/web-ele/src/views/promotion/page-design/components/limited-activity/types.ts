import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface LimitedActivityProps extends RetailComponentBaseProps {
  showCountdown: boolean;
  title: string;
}

export function createLimitedActivityDefaults(): LimitedActivityProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 3,
    dataSource: { mode: 'automatic', sort: 'start-time' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    showCountdown: true,
    title: '限时活动',
  };
}

export function validateLimitedActivity(props: LimitedActivityProps): string[] {
  const errors = validateRetailBase(props, 10);
  if (
    props.dataSource.mode === 'manual' &&
    !props.dataSource.targetIds?.length
  ) {
    errors.push('请选择活动');
  }
  return errors;
}
