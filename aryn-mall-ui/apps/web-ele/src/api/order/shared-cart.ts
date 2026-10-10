import { requestClient } from '#/api/request';

export async function getSharedCartPage(query: any) {
  return requestClient.get('/mall-order/shared-cart/page', { params: query });
}

export async function getSharedCartDetail(id: string) {
  return requestClient.get(`/mall-order/shared-cart/${id}`);
}

/**
 * 设置成员明细维护权限（收回后该成员只能查看清单）。
 *
 * 服务端按 can_edit 校验加购/改数量/移除/导入/历史复用，
 * 收回即立刻生效；发起人不接受收回（服务端报错，界面不提供开关）。
 */
export async function updateSharedCartMemberPermission(
  cartId: string,
  memberId: string,
  canEdit: '0' | '1',
) {
  return requestClient.put(
    `/mall-order/shared-cart/${cartId}/members/${memberId}/permission`,
    { canEdit },
  );
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
