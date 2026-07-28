import { requestClient } from '#/api/request';

/**
 * 获取订单信息分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mall-order/orderinfo/page', { params: query });
}

/**
 * 根据ID获取订单信息
 */
export async function getById(id: string) {
  return requestClient.get(`/mall-order/orderinfo/${id}`);
}

/**
 * 添加订单信息
 */
export async function addObj(data: any) {
  return requestClient.post('/mall-order/orderinfo', data);
}

/**
 * 编辑订单信息
 */
export async function editObj(data: any) {
  return requestClient.put('/mall-order/orderinfo', data);
}

/**
 * 删除订单信息
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mall-order/orderinfo/${id}`);
}

/**
 * 获取订单统计信息
 */
export async function getStatistics(query: any) {
  return requestClient.get('/mall-order/orderinfo/statistics', {
    params: query,
  });
}

/**
 * 获取订单数量
 */
export async function getCount() {
  return requestClient.get('/mall-order/orderinfo/count');
}

/**
 * 订单发货
 */
export async function deliverOrder(data: any) {
  return requestClient.post('/mall-order/orderinfo/deliver', data);
}

/**
 * 取消订单
 */
export async function cancelObj(id: string) {
  return requestClient.get(`/mall-order/orderinfo/cancel/${id}`);
}

/**
 * 自提订单
 */
export async function selffetchObj(data: any) {
  return requestClient.post('/mall-order/orderinfo/selffetch', data);
}

/** 从订单上下文查询关联的商城配送任务。 */
export async function getMallDeliveryTaskByOrderNo(orderNo: string) {
  return requestClient.get('/mall-order/delivery/admin/tasks', {
    params: { current: 1, orderNo, size: 1 },
  });
}
