import { requestClient } from '#/api/request';

/**
 * 获取订单配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-order/order-config/page', { params: query });
}

/**
 * 根据ID获取订单配置
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-order/order-config/${id}`);
}

/**
 * 添加订单配置
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-order/order-config', data);
}

/**
 * 编辑订单配置
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-order/order-config', data);
}

/**
 * 删除订单配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-order/order-config/${id}`);
}
