import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface ShopInfoProps extends RetailComponentBaseProps {
  showContact: boolean;
  showDescription: boolean;
  /** 悬浮压图：顶部外边距再上移 16px（C 端 32rpx 同口径），把店铺头叠到上方 banner 上 */
  liftOverBanner: boolean;
}

export function createShopInfoDefaults(): ShopInfoProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 1,
    dataSource: { mode: 'current-tenant' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    liftOverBanner: false,
    showContact: true,
    showDescription: true,
  };
}

export function validateShopInfo(props: ShopInfoProps): string[] {
  const errors = validateRetailBase(props, 1);
  if (props.dataSource.mode !== 'current-tenant') {
    errors.push('店铺信息必须使用当前租户数据');
  }
  return errors;
}
