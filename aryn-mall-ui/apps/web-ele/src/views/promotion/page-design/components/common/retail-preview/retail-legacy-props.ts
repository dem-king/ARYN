import type { RetailComponentBaseProps } from '../../../../page-designer/schema/retail-components';

import { createRetailCommonStyle } from '../../../../page-designer/schema/retail-components';

/**
 * 三个旧版营销楼层（秒杀 / 拼团 / 折扣）的展示参数。
 *
 * 组件本身已下线（由会场页 + 活动入口条承接），这里只保留字段定义：
 * 预览数据装配 `retail-data.ts` 仍按这些参数决定取数条数/是否展示倒计时。
 */
export interface SeckillProps extends RetailComponentBaseProps {
  showCountdown: boolean;
  showProgress: boolean;
  subtitle: string;
  title: string;
}

export interface LimitedActivityProps extends RetailComponentBaseProps {
  layout: 'grid' | 'list';
  showCountdown: boolean;
  subtitle: string;
  title: string;
}

export interface DiscountProps extends RetailComponentBaseProps {
  goodsLimit: number;
  showCountdown: boolean;
  subtitle: string;
  title: string;
}

export function createSeckillDefaults(): SeckillProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 1,
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'placeholder',
    invalidStrategy: 'hide',
    showCountdown: true,
    showProgress: true,
    subtitle: '',
    title: '限时秒杀',
  };
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

export function createDiscountDefaults(): DiscountProps {
  return {
    commonStyle: createRetailCommonStyle(),
    count: 1,
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'placeholder',
    goodsLimit: 4,
    invalidStrategy: 'hide',
    showCountdown: true,
    subtitle: '',
    title: '限时折扣',
  };
}
