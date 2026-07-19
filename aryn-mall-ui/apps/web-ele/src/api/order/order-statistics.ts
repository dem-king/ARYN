import { requestClient } from '#/api/request';

export interface OrderStatisticsQuery {
  endTime?: string;
  shopId?: string;
  startTime?: string;
}

/**
 * 订单交易数据 VO
 */
export interface OrderTradeStatisticsVO {
  /**
   * 支付金额 (GMV)
   */
  gmv: number;
  /**
   * 支付订单数
   */
  payOrderCount: number;
  /**
   * 支付人数
   */
  payBuyerCount: number;
  /**
   * 客单价
   */
  averageTicketSize: number;
  /**
   * 售后订单数
   */
  afterSalesOrderCount: number;
  /**
   * 退款金额
   */
  refundAmount: number;
}

/**
 * 交易趋势 VO
 */
export interface OrderTrendVO {
  /**
   * 时间点 (如 2023-10-27 12:00:00)
   */
  timePoint: string;
  /**
   * 支付金额
   */
  gmv: number;
  /**
   * 订单数
   */
  payOrderCount: number;
  /**
   * 支付人数
   */
  payBuyerCount: number;
}

/**
 * 退款率 Top10 VO
 */
export interface OrderRefundRateVO {
  /**
   * 商品名称
   */
  spuName: string;
  /**
   * 退款率 (百分比数值，如 12.5)
   */
  refundRate: number;
  /**
   * 退款订单数
   */
  refundOrderCount: number;
  /**
   * 总订单数
   */
  totalOrderCount: number;
}

/**
 * 订单用户统计 VO
 */
export interface OrderUserStatisticsVO {
  /**
   * 成交用户数
   */
  payBuyerCount: number;
  /**
   * 复购率 (百分比)
   */
  repurchaseRate: number;
  /**
   * 老客成交占比 (百分比)
   */
  oldCustomerGmvRate: number;
  /**
   * 新增用户数 (可选，根据业务需要)
   */
  newUserCount?: number;
}

/**
 * 订单概览统计 VO (服务与风控)
 */
export interface OrderOverviewVO {
  /**
   * 售后申请数
   */
  afterSalesCount: number;
  /**
   * 退款成功数
   */
  refundCompletedCount: number;
  /**
   * 超时未发货数
   */
  unshippedTimeoutCount: number;
  /**
   * 负面评价数
   */
  negativeAppraisalCount: number;
}

/**
 * 订单商品销量排行 VO
 */
export interface OrderProductSalesRankVO {
  /**
   * 商品名称
   */
  spuName: string;
  /**
   * 商品图片
   */
  picUrl: string;
  /**
   * 销量
   */
  salesCount: number;
  /**
   * 销售金额
   */
  salesAmount: number;
}

/**
 * 订单状态总览 VO
 */
export interface OrderStatusOverviewVO {
  /**
   * 待付款数量
   */
  waitingForPaymentCount: number;
  /**
   * 待发货数量
   */
  waitingForDeliveryCount: number;
  /**
   * 已发货数量
   */
  shippedCount: number;
  /**
   * 已完成数量
   */
  completedCount: number;
  /**
   * 售后中数量
   */
  afterSalesCount: number;
  /**
   * 已退款数量
   */
  refundedCount: number;
}

/**
 * 用户增长趋势 VO
 */
export interface UserGrowthTrendVO {
  /**
   * 时间点 (小时 或 日期)
   */
  timePoint: string;
  /**
   * 新增用户数量
   */
  newUserCount: number;
  /**
   * 成交用户数量 (支付买家数)
   */
  transactingUserCount: number;
}

/**
 * 用户消费分析 VO
 */
export interface UserConsumptionAnalysisVO {
  /**
   * 成交用户数
   */
  transactingUserCount: number;
  /**
   * 总消费金额
   */
  totalConsumptionAmount: number;
  /**
   * 人均消费 (总消费金额 / 成交用户数)
   */
  averageConsumptionPerUser: number;
  /**
   * 客单价 (总消费金额 / 总订单数)
   */
  averageOrderValue: number;
  /**
   * 总订单数
   */
  totalOrderCount: number;
}

/**
 * 用户消费次数分布 VO
 */
export interface UserConsumptionFrequencyVO {
  /**
   * 消费 1 次的用户数量
   */
  oneTimeCount: number;
  /**
   * 消费 2-3 次的用户数量
   */
  twoToThreeTimesCount: number;
  /**
   * 消费 4-5 次的用户数量
   */
  fourToFiveTimesCount: number;
  /**
   * 消费 6 次及以上的用户数量
   */
  sixPlusTimesCount: number;
}

/**
 * 用户消费金额分层统计 VO
 */
export interface UserConsumptionAmountVO {
  /**
   * 消费金额 0-100 元的用户数量
   */
  amount0To100Count: number;
  /**
   * 消费金额 100-500 元的用户数量
   */
  amount100To500Count: number;
  /**
   * 消费金额 500-2000 元的用户数量
   */
  amount500To2000Count: number;
  /**
   * 消费金额 2000 元以上的用户数量
   */
  amount2000PlusCount: number;
}

/**
 * 商品销售能力分析 VO
 */
export interface ProductSalesAnalysisVO {
  /**
   * 动销商品数 (有销量的商品数量)
   */
  activeProductCount: number;
  /**
   * 销售件数
   */
  salesCount: number;
  /**
   * 销售额
   */
  salesAmount: number;
  /**
   * 客单价 (销售额 / 订单数)
   */
  averageTicketSize: number;
  /**
   * 动销率 (动销商品数 / 在售商品数)
   */
  activeProductRate: number;
  /**
   * 订单数
   */
  orderCount: number;
}

/**
 * 获取订单交易数据
 * @param params
 */
export function getOrderTradeStatistics(params: OrderStatisticsQuery) {
  return requestClient.get<OrderTradeStatisticsVO>(
    '/mall-order/order/statistics/trade',
    {
      params,
    },
  );
}

/**
 * 获取交易趋势统计
 * @param params
 */
export function getOrderTradeTrend(params: OrderStatisticsQuery) {
  return requestClient.get<OrderTrendVO[]>(
    '/mall-order/order/statistics/trade/trend',
    {
      params,
    },
  );
}

/**
 * 获取退款率 Top10
 * @param params
 */
export function getRefundRateTop10(params: OrderStatisticsQuery) {
  return requestClient.get<OrderRefundRateVO[]>(
    '/mall-order/order/statistics/refund/rate/top10',
    {
      params,
    },
  );
}

/**
 * 获取订单用户统计 (成交用户数、复购率、老客成交占比)
 * @param params
 */
export function getUserStatistics(params: OrderStatisticsQuery) {
  return requestClient.get<OrderUserStatisticsVO>(
    '/mall-order/order/statistics/user',
    {
      params,
    },
  );
}

/**
 * 获取订单概览统计 (售后、退款、超时未发货、负面评价)
 * @param params
 */
export function getOrderOverview(params: OrderStatisticsQuery) {
  return requestClient.get<OrderOverviewVO>(
    '/mall-order/order/statistics/overview',
    {
      params,
    },
  );
}

/**
 * 获取商品销量TOP10
 * @param params
 */
export function getProductSalesTop10(params: OrderStatisticsQuery) {
  return requestClient.get<OrderProductSalesRankVO[]>(
    '/mall-order/order/statistics/product/sales/top10',
    {
      params,
    },
  );
}

/**
 * 获取订单状态总览 (待付款、待发货、已发货、已完成、售后中、已退款)
 * @param params
 */
export function getOrderStatusOverview(params: OrderStatisticsQuery) {
  return requestClient.get<OrderStatusOverviewVO>(
    '/mall-order/order/statistics/status/overview',
    {
      params,
    },
  );
}

/**
 * 获取用户增长趋势 (新增用户、成交用户)
 * @param params
 */
export function getUserGrowthTrend(params: OrderStatisticsQuery) {
  return requestClient.get<UserGrowthTrendVO[]>(
    '/mall-order/order/statistics/user/growth/trend',
    {
      params,
    },
  );
}

/**
 * 获取用户消费分析
 * @param params
 */
export function getUserConsumptionAnalysis(params: OrderStatisticsQuery) {
  return requestClient.get<UserConsumptionAnalysisVO>(
    '/mall-order/order/statistics/user/consumption/analysis',
    {
      params,
    },
  );
}

/**
 * 获取用户消费次数分布统计
 * @param params
 */
export function getUserConsumptionFrequency(params: OrderStatisticsQuery) {
  return requestClient.get<UserConsumptionFrequencyVO>(
    '/mall-order/order/statistics/user/consumption/frequency',
    {
      params,
    },
  );
}

/**
 * 获取用户消费金额分层统计
 * @param params
 */
export function getUserConsumptionAmount(params: OrderStatisticsQuery) {
  return requestClient.get<UserConsumptionAmountVO>(
    '/mall-order/order/statistics/user/consumption/amount',
    {
      params,
    },
  );
}

/**
 * 获取累计成交用户数
 * @param params
 */
export function getAccumulatedTransactionUserCount(
  params: OrderStatisticsQuery,
) {
  return requestClient.get<number>(
    '/mall-order/order/statistics/user/transacting/count',
    {
      params,
    },
  );
}

/**
 * 获取商品销售能力分析 (动销商品数、销售件数、销售额、客单价、动销率)
 * @param params
 */
export function getProductSalesAnalysis(params: OrderStatisticsQuery) {
  return requestClient.get<ProductSalesAnalysisVO>(
    '/mall-order/order/statistics/product/sales/analysis',
    {
      params,
    },
  );
}
