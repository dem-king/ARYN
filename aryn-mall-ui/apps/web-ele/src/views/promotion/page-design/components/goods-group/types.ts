import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface GoodsGroupProps extends RetailComponentBaseProps {
  columns: 2 | 3;
  showSales: boolean;
  title: string;
}

export function createGoodsGroupDefaults(): GoodsGroupProps {
  return {
    columns: 2,
    commonStyle: createRetailCommonStyle(),
    count: 6,
    dataSource: { mode: 'rule', sort: 'sales' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    showSales: true,
    title: '热卖商品',
  };
}

export function validateGoodsGroup(props: GoodsGroupProps): string[] {
  const errors = validateRetailBase(props, 20);
  if (
    props.dataSource.mode === 'manual' &&
    !props.dataSource.targetIds?.length
  ) {
    errors.push('请选择商品');
  }
  return errors;
}
