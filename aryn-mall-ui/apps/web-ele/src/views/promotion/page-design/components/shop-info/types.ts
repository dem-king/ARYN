import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

export interface ShopInfoProps extends RetailComponentBaseProps {
  showContact: boolean;
  showDescription: boolean;
}

export function createShopInfoDefaults(): ShopInfoProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 1,
    dataSource: { mode: 'current-tenant' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
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
