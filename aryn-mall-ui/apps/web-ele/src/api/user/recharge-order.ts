import { requestClient } from '#/api/request';

/**
 * 获取充值订单分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-user/rechargeorder/page', { params: query });
}

/**
 * 根据ID获取充值订单
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-user/rechargeorder/${id}`);
}
