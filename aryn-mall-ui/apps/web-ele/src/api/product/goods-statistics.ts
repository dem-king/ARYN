import { requestClient } from '#/api/request';

export interface ProductStatisticsQuery {
  endTime?: string;
  shopId?: string;
  startTime?: string;
}

export type ShopStatisticsQuery = Pick<ProductStatisticsQuery, 'shopId'>;

/**
 * 商品销量排行 VO
 */
export interface ProductSalesRankVO {
  /**
   * 商品ID
   */
  id: string;
  /**
   * 商品名称
   */
  name: string;
  /**
   * 商品图片
   */
  picUrl: string;
  /**
   * 销量
   */
  salesVolume: number;
}

/**
 * 商品概览数据 VO
 */
export interface ProductOverviewVO {
  /**
   * 在售商品数量
   */
  onSaleCount: number;
  /**
   * 下架商品数量
   */
  offShelfCount: number;
  /**
   * 库存预警数量
   */
  lowStockCount: number;
  /**
   * 待审核数量
   */
  pendingReviewCount: number;
  /**
   * 总库存数量
   */
  totalStock: number;
}

/**
 * 获取商品销量TOP10
 * @param params
 */
export function getProductSalesTop10(params: ShopStatisticsQuery) {
  return requestClient.get<ProductSalesRankVO[]>(
    '/product/product/statistics/sales/top10',
    {
      params,
    },
  );
}

/**
 * 获取商品概览
 * @param params
 */
export function getProductOverview(params: ShopStatisticsQuery) {
  return requestClient.get<ProductOverviewVO>(
    '/product/product/statistics/overview',
    {
      params,
    },
  );
}

/**
 * 商品访问趋势 VO
 */
export interface ProductVisitTrendVO {
  /**
   * 时间点 (小时 或 日期)
   */
  timePoint: string;
  /**
   * 浏览量 (PV)
   */
  pv: number;
  /**
   * 访客数 (UV)
   */
  uv: number;
}

/**
 * 库存预警商品 VO
 */
export interface ProductLowStockVO {
  /**
   * 商品ID
   */
  id: string;
  /**
   * 商品名称
   */
  name: string;
  /**
   * 商品图片
   */
  picUrl: string;
  /**
   * 剩余库存
   */
  stock: number;
}

/**
 * 商品浏览排行 VO
 */
export interface ProductVisitRankVO {
  /**
   * 商品名称
   */
  spuName: string;
  /**
   * 商品图片
   */
  picUrl: string;
  /**
   * 浏览量 (PV)
   */
  pv: number;
  /**
   * 访客数 (UV)
   */
  uv: number;
}

/**
 * 商品好评榜 VO
 */
export interface ProductPraiseRankVO {
  /**
   * 商品名称
   */
  spuName: string;
  /**
   * 商品图片
   */
  picUrl: string;
  /**
   * 好评数
   */
  goodAppraiseCount: number;
  /**
   * 好评率
   */
  goodAppraiseRate: number;
}

/**
 * 获取商品访问趋势 (PV/UV)
 * @param params
 */
export function getProductVisitTrend(params: ProductStatisticsQuery) {
  return requestClient.get<ProductVisitTrendVO[]>(
    '/product/product/statistics/visit/trend',
    {
      params,
    },
  );
}

/**
 * 获取库存预警商品 (库存<10)
 * @param shopId
 */
export function getLowStockTop10(shopId?: string) {
  return requestClient.get<ProductLowStockVO[]>(
    '/product/product/statistics/stock/low',
    {
      params: { shopId },
    },
  );
}

/**
 * 获取商品浏览排行 TOP 10 (PV/UV)
 * @param params
 */
export function getProductVisitTop10(params: ProductStatisticsQuery) {
  return requestClient.get<ProductVisitRankVO[]>(
    '/product/product/statistics/visit/top10',
    {
      params,
    },
  );
}

/**
 * 获取商品好评榜 TOP 10
 * @param params
 */
export function getProductPraiseTop10(params: ProductStatisticsQuery) {
  return requestClient.get<ProductPraiseRankVO[]>(
    '/product/product/statistics/praise/top10',
    {
      params,
    },
  );
}
