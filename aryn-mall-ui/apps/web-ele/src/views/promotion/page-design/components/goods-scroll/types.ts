import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface GoodsScrollProps extends RetailComponentBaseProps {
  title: string;
  subtitle: string;
  /** scroll：手指横滑；pager：自动轮播 */
  displayMode: 'pager' | 'scroll';
  /** 一屏展示几个商品 */
  perView: 3 | 4;
  /** 自动轮播间隔（毫秒） */
  interval: number;
  /** 是否展示划线原价（仅当商品原价高于售价时可见） */
  showOriginalPrice: boolean;
  showSales: boolean;
}

export function createGoodsScrollDefaults(): GoodsScrollProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 8,
    dataSource: { mode: 'rule', sort: 'sales' },
    displayMode: 'pager',
    emptyStrategy: 'placeholder',
    interval: 3000,
    invalidStrategy: 'hide',
    perView: 3,
    showOriginalPrice: true,
    showSales: false,
    subtitle: '快手好菜 美味即享',
    title: '省心午餐',
  };
}

export function validateGoodsScroll(props: GoodsScrollProps): string[] {
  const errors = validateRetailBase(props, 20);
  if (
    props.dataSource.mode === 'manual' &&
    !props.dataSource.targetIds?.length
  ) {
    errors.push('请选择商品');
  }
  return errors;
}
