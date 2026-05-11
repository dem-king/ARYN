import { requestClient } from '#/api/request';

/**
 * 获取支付通知记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/pay/notify/page', { params: query });
}

/**
 * 根据ID获取支付通知记录
 */
export async function getById(id: string) {
  return requestClient.get(`/pay/notify/${id}`);
}
