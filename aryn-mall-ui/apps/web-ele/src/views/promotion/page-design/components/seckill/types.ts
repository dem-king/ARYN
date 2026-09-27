import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface SeckillProps extends RetailComponentBaseProps {
  /** 是否展示场次倒计时 */
  showCountdown: boolean;
  /** 是否展示已售进度条 */
  showProgress: boolean;
  title: string;
}

export function createSeckillDefaults(): SeckillProps {
  return {
    commonStyle: createRetailCommonStyle(),
    // 秒杀楼层一场为一个内容块，默认只放 1 场，避免首页被秒杀占满
    count: 1,
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    showCountdown: true,
    showProgress: true,
    title: '限时秒杀',
  };
}

export function validateSeckill(props: SeckillProps): string[] {
  const errors = validateRetailBase(props, 5);
  if (
    props.dataSource.mode === 'manual' &&
    !props.dataSource.targetIds?.length
  ) {
    errors.push('请选择秒杀场次');
  }
  return errors;
}
