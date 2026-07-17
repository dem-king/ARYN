import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface GoodsRankingProps extends RetailComponentBaseProps {
  showRankNumber: boolean;
  title: string;
}

export function createGoodsRankingDefaults(): GoodsRankingProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 5,
    dataSource: { metric: 'sales', mode: 'ranking' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    showRankNumber: true,
    title: '畅销排行',
  };
}

export function validateGoodsRanking(props: GoodsRankingProps): string[] {
  const errors = validateRetailBase(props, 10);
  if (!props.dataSource.metric) errors.push('请选择排行指标');
  return errors;
}
