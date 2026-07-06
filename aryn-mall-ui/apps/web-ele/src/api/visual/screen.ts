import { requestClient } from '#/api/request';

/**
 * 大屏概览数据 VO
 */
export interface VisualOverviewVO {
  /** 实时 GMV */
  gmv: number;
  /** 订单数 */
  orderCount: number;
  /** 在线用户数 */
  onlineUsers: number;
  /** 转化率 (百分比) */
  conversionRate: number;
}

/**
 * 订单趋势 VO
 */
export interface VisualOrderTrendVO {
  /** 日期 */
  date: string;
  /** 订单数 */
  orderCount: number;
  /** 成交额 */
  gmv: number;
}

/**
 * 品类销售排行 VO
 */
export interface VisualCategoryRankVO {
  /** 品类名称 */
  categoryName: string;
  /** 销售额 */
  salesAmount: number;
  /** 销售数量 */
  salesCount: number;
}

/**
 * 用户漏斗 VO
 */
export interface VisualUserFunnelVO {
  /** 注册用户数 */
  registered: number;
  /** 下单用户数 */
  ordered: number;
  /** 复购用户数 */
  repurchased: number;
}

/** 获取大屏概览数据 */
export function getVisualOverview() {
  return requestClient.get<VisualOverviewVO>('/api/v1/visual/overview');
}

/** 获取订单趋势 */
export function getVisualOrderTrend(range: string = '7d') {
  return requestClient.get<VisualOrderTrendVO[]>('/api/v1/visual/order-trend', {
    params: { range },
  });
}

/** 获取品类销售排行 */
export function getVisualCategoryRank() {
  return requestClient.get<VisualCategoryRankVO[]>(
    '/api/v1/visual/category-rank',
  );
}

/** 获取用户转化漏斗 */
export function getVisualUserFunnel() {
  return requestClient.get<VisualUserFunnelVO>('/api/v1/visual/user-funnel');
}
