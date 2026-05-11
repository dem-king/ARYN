import { requestClient } from '#/api/request';

/**
 * 根据ID获取订单物流信息
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-order/orderLogistics/${id}`);
}
