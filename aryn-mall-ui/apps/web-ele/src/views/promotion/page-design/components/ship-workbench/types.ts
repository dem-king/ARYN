import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

/**
 * 船舶工作台。
 *
 * 展示当前登录用户此刻的船舶与靠港状态，数据来自：
 *   - /vessel/app/my-vessels（我的船舶）
 *   - /vessel/app/{id}/calls（可用靠港计划）
 *   - /upms/app/tenant/shop-info（租户 business_mode 能力开关）
 *
 * 因此它没有可手选的数据源：内容由「谁在看」决定，运营只能控制是否展示
 * 「常购」入口与通用样式。
 */
export interface ShipWorkbenchProps extends RetailComponentBaseProps {
  showFrequent: boolean;
}

export function createShipWorkbenchDefaults(): ShipWorkbenchProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 1,
    dataSource: { mode: 'current-tenant' },
    emptyStrategy: 'hide',
    invalidStrategy: 'hide',
    showFrequent: true,
  };
}

export function validateShipWorkbench(props: ShipWorkbenchProps): string[] {
  const errors = validateRetailBase(props, 1);
  if (props.dataSource?.mode !== 'current-tenant') {
    errors.push('船舶工作台展示当前登录用户的船舶，数据来源不可修改');
  }
  return errors;
}
