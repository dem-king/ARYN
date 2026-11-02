import { requestClient } from '#/api/request';

export interface GoodsSkuDisplay {
  picUrl?: string;
  salesPrice?: number | string;
  skuId: string;
  skuName?: string;
  spuId?: string;
  spuName?: string;
}

/**
 * 批量查询 SKU 展示信息（商品名/规格/售价，含已下架）。
 *
 * 活动等配置只落库 SKU ID，编辑回显时用它补齐商品名与售价。
 */
export async function getDisplayByIds(ids: string[]) {
  return requestClient.get<GoodsSkuDisplay[]>(
    `/product/goodssku/byIds/${ids.join(',')}`,
  );
}
