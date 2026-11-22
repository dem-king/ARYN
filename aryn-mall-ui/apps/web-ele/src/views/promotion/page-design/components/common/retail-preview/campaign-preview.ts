import type { ComputedRef, InjectionKey } from 'vue';

import type { GoodsGroupProps } from '../../goods-group/types';
import type { RetailGoodsItem } from './retail-data';

/** 仅活动包工作台提供的受权固定样本事实；不写装修JSON，不参与C端取数。 */
export const campaignPreviewGoodsKey: InjectionKey<
  ComputedRef<ReadonlyMap<string, RetailGoodsItem>>
> = Symbol('campaign-preview-goods');

export function selectCampaignPreviewGoods(
  props: GoodsGroupProps,
  captured: ReadonlyMap<string, RetailGoodsItem>,
): RetailGoodsItem[] {
  return (props.dataSource.targetIds ?? [])
    .flatMap((id) => {
      const goods = captured.get(id);
      return goods ? [goods] : [];
    })
    .slice(0, props.count);
}
