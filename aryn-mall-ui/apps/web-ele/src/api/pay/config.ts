import { requestClient } from '#/api/request';

/**
 * 获取支付配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/pay/payconfig/page', { params: query });
}

/**
 * 根据ID获取支付配置
 */
export async function getById(id: string) {
  return requestClient.get(`/pay/payconfig/${id}`);
}

/**
 * 添加支付配置
 */
export async function addObj(data: any) {
  return requestClient.post('/pay/payconfig', data);
}

/**
 * 编辑支付配置
 */
export async function editObj(data: any) {
  return requestClient.put('/pay/payconfig', data);
}

/**
 * 删除支付配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/pay/payconfig/${id}`);
}
