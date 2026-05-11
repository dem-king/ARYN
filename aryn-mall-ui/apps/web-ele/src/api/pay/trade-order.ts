import { requestClient } from '#/api/request';

/**
 * 获取支付交易订单分页
 */
export async function getPage(query: any) {
  return requestClient.get('/pay/pay/page', { params: query });
}

/**
 * 根据ID获取支付交易订单
 */
export async function getById(id: string) {
  return requestClient.get(`/pay/pay/${id}`);
}
