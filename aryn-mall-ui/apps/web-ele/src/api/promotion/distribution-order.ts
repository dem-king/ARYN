import { requestClient } from '#/api/request';

/**
 * 获取分销订单分页
 */
export async function getPage(query: any) {
  return requestClient.get('/promotion/distribution/order/page', {
    params: query,
  });
}

/**
 * 根据ID获取分销订单
 */
export async function getById(id: string) {
  return requestClient.get(`/promotion/distribution/order/${id}`);
}

/**
 * 手动触发结算
 */
export async function settleOrder(data: any) {
  return requestClient.post('/promotion/distribution/order/settle', data);
}
