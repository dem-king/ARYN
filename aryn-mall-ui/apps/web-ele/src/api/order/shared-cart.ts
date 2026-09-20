import { requestClient } from '#/api/request';

export async function getSharedCartPage(query: any) {
  return requestClient.get('/mall-order/shared-cart/page', { params: query });
}

export async function getSharedCartDetail(id: string) {
  return requestClient.get(`/mall-order/shared-cart/${id}`);
}

/** 履约异常上报 */
export async function reportException(data: {
  description?: string;
  evidenceUrls?: string;
  exceptionType: string;
  orderId?: string;
  taskId?: string;
  waveId?: string;
}) {
  return requestClient.post('/mall-order/fulfillment/exception', data);
}

/** 订单营销快照 */
export async function getPromotionSnapshots(orderId: string) {
  return requestClient.get('/mall-order/promotion-snapshot/list', {
    params: { orderId },
  });
}
