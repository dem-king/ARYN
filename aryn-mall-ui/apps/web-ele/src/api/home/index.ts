import { requestClient } from '#/api/request';
/**
 * 获取用户数量
 */
export async function getUserCount(query: any) {
  return requestClient.get('/mall-user/userinfo/statistics', { params: query });
}

/**
 * 获取退单数量
 */
export async function getRefundCount(query: any) {
  return requestClient.get('/mall-order/orderrefund/count', { params: query });
}
/**
 * 获取订单数量
 */
export async function getOrderCount(query: any) {
  return requestClient.get('/mall-order/orderinfo/count', { params: query });
}
/**
 * 获取商品数量
 */
export async function getGoodsCount(query: any) {
  return requestClient.get('/product/goodsspu/count', { params: query });
}
/**
 * 订单统计-销售趋势
 */
export async function getOrderStatistics(query: any) {
  return requestClient.get('/mall-order/orderinfo/statistics', {
    params: query,
  });
}
/**
 * 订单统计-支付类型
 */
export async function getPayTypeStatistics(query: any) {
  return requestClient.get('/mall-order/orderinfo/pay-type/statistics', {
    params: query,
  });
}

/**
 * 用户统计-用户来源
 */
export async function getUserSourceStatistics(query: any) {
  return requestClient.get('/mall-user/userinfo/source/statistics', {
    params: query,
  });
}
