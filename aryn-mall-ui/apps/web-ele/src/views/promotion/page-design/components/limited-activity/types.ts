import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface LimitedActivityProps extends RetailComponentBaseProps {
  /** 展示样式：list=单列横排（默认，匹配 1 活动=1 商品）；grid=双列货架式 */
  layout: 'grid' | 'list';
  showCountdown: boolean;
  /** 楼层头副标题，空串使用模块默认文案 */
  subtitle: string;
  title: string;
}

export function createLimitedActivityDefaults(): LimitedActivityProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 3,
    dataSource: { mode: 'automatic', sort: 'start-time' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    layout: 'list',
    showCountdown: true,
    subtitle: '',
    title: '拼团',
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
  // 存量装修数据没有 layout 字段（读取侧回落 list），只有显式传了非法值才报错
  if (props.layout && props.layout !== 'grid' && props.layout !== 'list') {
    errors.push('展示样式仅支持单列（list）或双列（grid）');
  }
  return errors;
}
